/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.JarScanData;
import net.fabricmc.loom.util.mcp.McpTinyv2Writer;
import net.fabricmc.loom.util.mcp.Members;
import net.fabricmc.loom.util.mcp.Packages;
import net.fabricmc.loom.util.mcp.Srg;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.BufferedReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Parses mappings. The parsed mappings are available in-memory with {@code getMappings()}, and the on-disk files are also available.
 * 
 * Supported formats are currently:
 * <ul>
 *   <li>MCP 1.4.7 zips, which do not include package information</li>
 *   <li>a zip of the Minecraft Forge source code, which does include package information</li>
 * </ul>
 */
public class MappingsProvider extends DependencyProvider {
	@Inject
	public MappingsProvider(Project project, LoomGradleExtension extension, ForgePatchedProvider forgePatched) {
		super(project, extension);
		this.forgePatched = forgePatched;
		
		dependsOn(forgePatched);
	}
	
	private final ForgePatchedProvider forgePatched;
	
	private Path mappingsDir;
	private Path rawMappingsJar;
	private String mappingsDepString;
	
	//output created by performInstall():
	private Path tinyMappings;
	private TinyTree parsedMappings;
	
	@Override
	protected void performSetup() throws Exception {
		DependencyInfo mappingsDependency = getSingleDependency(Constants.MAPPINGS);
		rawMappingsJar = mappingsDependency.resolveSinglePath();
		
		//TODO: REMOVE this hack
		String mappingDiscriminant = "";
		if(extension.forgeCapabilities.useSrgsAsFallback()) mappingDiscriminant += "-srgfallback";
		
		//outputs
		mappingsDir = getCacheDir().resolve("mappings");
		mappingsDepString = mappingsDependency.getDepString() + mappingDiscriminant;
		tinyMappings = mappingsDir.resolve(rawMappingsJar.getFileName() + mappingDiscriminant + ".tiny");
		
		project.getLogger().lifecycle("] mappings dep: {}", mappingsDepString);
		project.getLogger().lifecycle("] mappings source: {}", rawMappingsJar);
		project.getLogger().lifecycle("] mappings destination: {}", tinyMappings);
		
		cleanOnRefreshDependencies(tinyMappings);
	}
	
	public void performInstall() throws Exception {
		Files.createDirectories(mappingsDir);
		
		if(Files.notExists(tinyMappings)) {
			project.getLogger().lifecycle("|-> Mappings file does not exist, parsing...");
			
			long filesize;
			try {
				filesize = Files.size(rawMappingsJar);
			} catch (Exception e) {
				throw new RuntimeException("Problem statting mappings zip", e);
			}
			if(filesize == 0) {
				throw new RuntimeException("The mappings zip at " + rawMappingsJar + " is a 0-byte file. Please double-check the URL and redownload. " +
					"If you obtained this from the Internet Archive, note that it likes to return 0-byte files instead of 404 errors.");
			}
			
			try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + rawMappingsJar.toUri()), Collections.emptyMap())) {
				//TODO: Remove this crap when i do the good mappings system
				Path tinyv2FunnyMoments = mcpZipFs.getPath("mappings/mappings.tiny"); 
				if(Files.exists(tinyv2FunnyMoments)) {
					//WOW its already in tinyv2 format how neat!!!
					project.getLogger().warn("MAPPINGS ALREADY TINYv2 I THINK!!!!! Fyi it should probably contain {} {} {} headers", Constants.PROGUARDED_NAMING_SCHEME, Constants.INTERMEDIATE_NAMING_SCHEME, Constants.MAPPED_NAMING_SCHEME);
					Files.copy(tinyv2FunnyMoments, tinyMappings);
				} else {
				
				Path conf;
				if(Files.exists(mcpZipFs.getPath("forge/fml/conf"))) {
					conf = mcpZipFs.getPath("forge/fml/conf"); //Forge 1.3 to Forge 1.7
				} else if(Files.exists(mcpZipFs.getPath("forge/conf"))) {
					conf = mcpZipFs.getPath("forge/conf"); //Forge 1.2
				} else if(Files.exists(mcpZipFs.getPath("conf"))){
					conf = mcpZipFs.getPath("conf"); //MCP
				} else {
					conf = mcpZipFs.getPath(""); //manually zipped mappings?
				}
				project.getLogger().lifecycle("] Mappings root detected to be '{}'", conf);
				
				project.getLogger().lifecycle("|-> Reading joined.srg...");
				Srg joinedSrg;
				if(Files.exists(conf.resolve("joined.srg"))) {
					joinedSrg = new Srg().read(conf.resolve("joined.srg"));
				} else {
					//just assume we're manually merging a client and server srg
					//TODO: newids?
					project.getLogger().lifecycle("\\-> No joined.srg exists. Reading client.srg...");
					Srg client = new Srg().read(conf.resolve("client.srg"));
					
					project.getLogger().lifecycle("\\-> Reading server.srg...");
					Srg server = new Srg().read(conf.resolve("server.srg"));
					
					project.getLogger().lifecycle("\\-> Manually joining srgs...");
					joinedSrg = client.mergeWith(server);
				}
				
				project.getLogger().lifecycle("|-> Reading fields.csv...");
				Members fields = new Members().read(conf.resolve("fields.csv"));
				
				project.getLogger().lifecycle("|-> Reading methods.csv...");
				Members methods = new Members().read(conf.resolve("methods.csv"));
				
				project.getLogger().lifecycle("|-> Reading packages.csv...");
				@Nullable Packages packages;
				if(Files.exists(conf.resolve("packages.csv"))) {
					packages = new Packages().read(conf.resolve("packages.csv"));
				} else {
					project.getLogger().lifecycle("\\-> No packages.csv exists.");
					packages = null;
				}
				
				//TODO: Only usage of ForgePatched, and I think putting tiny-remapper in "ignore field descs" mode helps
				project.getLogger().lifecycle("|-> Scanning unmapped jar for field types...");
				JarScanData scanData = new JarScanData().scan(forgePatched.getPatchedJar());
				
				project.getLogger().lifecycle("|-> Computing tinyv2 mappings...");
				List<String> tinyv2 = new McpTinyv2Writer()
					.srg(joinedSrg)
					.fields(fields)
					.methods(methods)
					.packages(packages)
					.srgsAsFallback(extension.forgeCapabilities.useSrgsAsFallback()) //TODO select from forge version
					.jarScanData(scanData)
					.write();
				
				project.getLogger().lifecycle("|-> Saving...");
				Files.write(tinyMappings, tinyv2, StandardCharsets.UTF_8);
				
				project.getLogger().lifecycle("|-> Done!");
			}}
		}
		
		//make them available for other tasks TODO maybe it shouldn't roundtrip through a file
		try(BufferedReader lol = Files.newBufferedReader(tinyMappings)) {
			parsedMappings = TinyMappingFactory.loadWithDetection(lol);
		}
	}
	
	public TinyTree getMappings() {
		return parsedMappings;
	}
	
	public String getMappingsDepString() {
		return mappingsDepString;
	}
	
	public Path getTinyMappings() {
		return tinyMappings;
	}
}

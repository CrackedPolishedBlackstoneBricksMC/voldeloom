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
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
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
import java.util.Arrays;
import java.util.Collection;
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
	}
	
	private final ForgePatchedProvider forgePatched;
	
	private final Path mappingsDir = WellKnownLocations.getUserCache(project).resolve("mappings");
	
	private String mappingsName;
	private String mappingsVersion;
	private TinyTree parsedMappings;
	
	private Path tinyMappings;
	private Path tinyMappingsJar;
	
	public void decorateProject() throws Exception {
		//inputs
		DependencyInfo mappingsDependency = getSingleDependency(Constants.MAPPINGS);
		Path mappingsJar = mappingsDependency.resolveSinglePath();
		project.getLogger().lifecycle("] mappings name: " + mappingsDependency.getDependency().getName() + ", version: " + mappingsDependency.getResolvedVersion());

		//outputs
		mappingsName = mappingsDependency.getDependency().getGroup() + "." + mappingsDependency.getDependency().getName();
		mappingsVersion = mappingsDependency.getResolvedVersion();
		tinyMappings = mappingsDir.resolve(mappingsJar.getFileName() + ".tiny");
		tinyMappingsJar = mappingsDir.resolve(mappingsJar.getFileName() + ".tiny.jar");
		
		cleanIfRefreshDependencies();
		Files.createDirectories(mappingsDir);
		
		//task
		if(Files.notExists(tinyMappings)) {
			long filesize;
			try {
				filesize = Files.size(mappingsJar);
			} catch (Exception e) {
				throw new RuntimeException("Problem statting mappings zip", e);
			}
			if(filesize == 0) {
				throw new RuntimeException("The mappings zip at " + mappingsJar + " is a 0-byte file. Please double-check the URL and redownload. " +
					"If you obtained this from the Internet Archive, note that it likes to return 0-byte files instead of 404 errors.");
			}
			
			try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + mappingsJar.toUri()), Collections.emptyMap())) {
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
				project.getLogger().info("] Mappings root detected to be '{}'", conf);
				
				project.getLogger().info("|-> Reading joined.srg...");
				Srg joinedSrg;
				if(Files.exists(conf.resolve("joined.srg"))) {
					joinedSrg = new Srg().read(conf.resolve("joined.srg"));
				} else {
					//just assume we're manually merging a client and server srg
					//TODO: newids?
					project.getLogger().info("\\-> No joined.srg exists. Reading client.srg...");
					Srg client = new Srg().read(conf.resolve("client.srg"));
					
					project.getLogger().info("\\-> Reading server.srg...");
					Srg server = new Srg().read(conf.resolve("server.srg"));
					
					project.getLogger().info("\\-> Manually joining srgs...");
					joinedSrg = client.mergeWith(server);
				}
				
				project.getLogger().info("|-> Reading fields.csv...");
				Members fields = new Members().read(conf.resolve("fields.csv"));
				
				project.getLogger().info("|-> Reading methods.csv...");
				Members methods = new Members().read(conf.resolve("methods.csv"));
				
				project.getLogger().info("|-> Reading packages.csv...");
				@Nullable Packages packages;
				if(Files.exists(conf.resolve("packages.csv"))) {
					packages = new Packages().read(conf.resolve("packages.csv"));
				} else {
					project.getLogger().info("\\-> No packages.csv exists.");
					packages = null;
				}
				
				project.getLogger().info("|-> Scanning unmapped jar for field types...");
				JarScanData scanData = new JarScanData().scan(forgePatched.getPatchedJar());
				
				project.getLogger().info("|-> Computing tinyv2 mappings...");
				List<String> tinyv2 = new McpTinyv2Writer()
					.srg(joinedSrg)
					.fields(fields)
					.methods(methods)
					.packages(packages)
					.srgsAsFallback(false) //TODO select from forge version/make configurable
					.jarScanData(scanData)
					.write();
				
				project.getLogger().info("|-> Saving...");
				Files.write(tinyMappings, tinyv2, StandardCharsets.UTF_8);
				
				project.getLogger().info("|-> Done!");
			}
		}
		
		//Package them up for tiny-remapper, which expects to find the mappings in a specific spot in a jar
		if(Files.notExists(tinyMappingsJar)) {
			try(FileSystem mappingsJarFs = FileSystems.newFileSystem(URI.create("jar:" + tinyMappingsJar.toUri()), Collections.singletonMap("create", "true"))) {
				Path target = mappingsJarFs.getPath("mappings/mappings.tiny");
				Files.createDirectories(target.getParent());
				Files.copy(tinyMappings, target);
			}
		}
		
		//make them available for other tasks TODO make it not roundtrip through a file lol
		try(BufferedReader lol = Files.newBufferedReader(tinyMappings)) {
			parsedMappings = TinyMappingFactory.loadWithDetection(lol);
		}
		
		//add it as a project dependency TODO move
		project.getDependencies().add(Constants.MAPPINGS_FINAL, project.files(tinyMappingsJar));
		
		installed = true;
	}
	
	public TinyTree getMappings() {
		return parsedMappings;
	}
	
	public String getMappingsName() {
		return mappingsName;
	}
	
	public String getMappingsVersion() {
		return mappingsVersion;
	}
	
	public Path getTinyMappings() {
		return tinyMappings;
	}
	
	public Path getTinyMappingsJar() {
		return tinyMappingsJar;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Arrays.asList(tinyMappings, tinyMappingsJar);
	}
}

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
import net.fabricmc.loom.util.mcp.AcceptorProvider;
import net.fabricmc.loom.util.mcp.CsvApplierAcceptor;
import net.fabricmc.loom.util.mcp.SrgMappingProvider;
import net.fabricmc.loom.util.mcp.TinyWriter3Column;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Parses mappings. The parsed mappings are available in-memory with getMappings(), and the on-disk files are also available.
 * 
 * Supported formats are currently:
 * - MCP 1.4.7 zips, which do not include package information
 * - a zip of the Minecraft Forge source code, which does include package information
 */
public class MappingsProvider extends DependencyProvider {
	public MappingsProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private final Path mappingsDir = WellKnownLocations.getUserCache(project).resolve("mappings");
	
	private String mappingsName;
	private String mappingsVersion;
	private TinyTree parsedMappings;
	
	private Path tinyMappings;
	private Path tinyMappingsJar;
	
	public void decorateProject(ForgePatchedProvider forgePatched) throws Exception {
		//deps
		DependencyInfo mappingsDependency = getSingleDependency(Constants.MAPPINGS);
		
		project.getLogger().lifecycle("] mappings name: " + mappingsDependency.getDependency().getName() + ", version: " + mappingsDependency.getResolvedVersion());
		
		Path mappingsJar = mappingsDependency.resolveSinglePath().orElseThrow(() -> new RuntimeException("Could not find mcp mappings: " + mappingsDependency));

		this.mappingsName = mappingsDependency.getDependency().getGroup() + "." + mappingsDependency.getDependency().getName();
		this.mappingsVersion = mappingsDependency.getResolvedVersion();
		
		tinyMappings = mappingsDir.resolve(mappingsJar.getFileName() + ".tiny");
		tinyMappingsJar = mappingsDir.resolve(mappingsJar.getFileName() + ".tiny.jar");
		Files.createDirectories(mappingsDir);
		
		if (Files.notExists(tinyMappings)) {
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
			
			try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + mappingsJar.toUri()), Collections.singletonMap("create", "true"));
			    OutputStream out = Files.newOutputStream(tinyMappings)) {
				TinyWriter3Column writer = new TinyWriter3Column("official", "intermediary", "named");
				
				if(Files.exists(mcpZipFs.getPath("forge/fml/conf/joined.srg"))) {
					project.getLogger().lifecycle("] joined.srg detected - looks like a forge sources zip");
					Path conf = mcpZipFs.getPath("forge/fml/conf");
					
					//Scan JAR
					SrgMappingProvider.JarScanData data = SrgMappingProvider.scan(forgePatched.getPatchedJar());
					
					//Read joined SRG (classes, newid fields, newid methods)
					//Conveniently, Forge has pre-remapped this to newid SRGs instead of us having to use newids.csv.
					SrgMappingProvider joined = new SrgMappingProvider(conf.resolve("joined.srg"), data);
					
					//Apply Forge's packaging data (MCP doesn't have packaging data yet)
					AcceptorProvider packaged = new AcceptorProvider();
					joined.load(new CsvApplierAcceptor(packaged, conf.resolve("packages.csv"), CsvApplierAcceptor.PACKAGES_IN, CsvApplierAcceptor.PACKAGES_OUT));
					
					packaged.load(writer);
					
					writer.acceptSecond(); //toggle writer into accepting names for `named` classes
					MappingAcceptor fieldMapper = new CsvApplierAcceptor(writer, conf.resolve("fields.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
					MappingAcceptor methodMapper = new CsvApplierAcceptor(fieldMapper, conf.resolve("methods.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
					packaged.load(methodMapper);
				} else {
					project.getLogger().lifecycle("] no joined.srg detected - looks like an MCP mappings zip");
					project.getLogger().warn("] TODO i didn't retest this after implementing reading forge zips"); //TODO
					Path conf = mcpZipFs.getPath("conf");
					
					//Scan JAR
					SrgMappingProvider.JarScanData data = SrgMappingProvider.scan(forgePatched.getPatchedJar());
					
					//Read client and server SRGs (classes, splitid fields, splitid methods)
					SrgMappingProvider client = new SrgMappingProvider(conf.resolve("client.srg"), data);
					SrgMappingProvider server = new SrgMappingProvider(conf.resolve("server.srg"), data);
					
					//Rename members from their client-only/server-only SRG splitids to their newids, which are shared across both sides
					AcceptorProvider joined = new AcceptorProvider();
					client.load(new CsvApplierAcceptor(joined, mcpZipFs.getPath("conf", "newids.csv"), CsvApplierAcceptor.NEWNAME_CLIENT_IN, CsvApplierAcceptor.NEWNAME_OUT));
					server.load(new CsvApplierAcceptor(joined, mcpZipFs.getPath("conf", "newids.csv"), CsvApplierAcceptor.NEWNAME_SERVER_IN, CsvApplierAcceptor.NEWNAME_OUT));
					
					//This file only exists in Forge, but you might have it if you pasted forge sources over an MCP zip?
					AcceptorProvider packaged = new AcceptorProvider();
					if(Files.exists(conf.resolve("packages.csv"))) {
						joined.load(new CsvApplierAcceptor(packaged, mcpZipFs.getPath("conf", "packages.csv"), CsvApplierAcceptor.PACKAGES_IN, CsvApplierAcceptor.PACKAGES_OUT));
					} else {
						joined.load(packaged);
					}
					packaged.load(writer);
					
					writer.acceptSecond(); //toggle writer into accepting names for `named` classes
					MappingAcceptor fieldMapper = new CsvApplierAcceptor(writer, mcpZipFs.getPath("conf", "fields.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
					MappingAcceptor methodMapper = new CsvApplierAcceptor(fieldMapper, mcpZipFs.getPath("conf", "methods.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
					packaged.load(methodMapper);
				}
				
				writer.write(out);
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

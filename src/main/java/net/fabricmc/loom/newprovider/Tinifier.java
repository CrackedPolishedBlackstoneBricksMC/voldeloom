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

package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.JarScanData;
import net.fabricmc.loom.util.mcp.McpTinyv2Writer;
import net.fabricmc.loom.util.terrible.TreeSquisher;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Converts mappings from {@code RawMappingsProvider} into a tinyfile.
 */
public class Tinifier extends NewProvider<Tinifier> {
	public Tinifier(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private final Collection<Path> jarsToScan = new ArrayList<>(4);
	private MappingsWrapper mappings;
	private boolean useSrgsAsFallback = false;
	
	public Tinifier scanJars(Path... jars) {
		this.jarsToScan.addAll(Arrays.asList(jars));
		return this;
	}
	
	public Tinifier mappings(MappingsWrapper mappings) {
		this.mappings = mappings;
		return this;
	}
	
	public Tinifier useSrgsAsFallback(boolean useSrgsAsFallback) {
		this.useSrgsAsFallback = useSrgsAsFallback;
		return this;
	}
	
	//outputs
	private Path mappingsFile;
	private TinyTree parsedMappings;
	
	//procedure
	public Tinifier tinify() throws Exception {
		Preconditions.checkNotNull(mappings, "mappings");
		
		mappingsFile = getCacheDir().resolve("mappings").resolve(mappings.getPath().getFileName() + mappings.getMappingDiscriminant() + ".tiny");
		log.lifecycle("] mappings destination: {}", mappingsFile);
		
		cleanOnRefreshDependencies(mappingsFile);
		if(Files.notExists(mappingsFile)) {
			log.info("|-> Mappings file does not exist, writing...");
			Files.createDirectories(mappingsFile.getParent());
			
			if(mappings.isAlreadyTinyv2()) {
				//TODO: its the TINY PASSTHROUGH HACK !!
				Files.copy(mappings.getPath(), mappingsFile);
			} else {
				JarScanData scanData = new JarScanData();
				for(Path jar : jarsToScan) {
					log.info("|-> Scanning {} for field types/inner classes...", jar);
					scanData.scan(jar);
				}
				
				log.info("|-> Computing tinyv2 mappings...");
				List<String> tinyv2 = new McpTinyv2Writer()
					.srg(mappings.getJoined())
					.fields(mappings.getFields())
					.methods(mappings.getMethods())
					.packages(mappings.getPackages())
					.srgsAsFallback(useSrgsAsFallback)
					.jarScanData(scanData)
					.write();
				
				Files.write(mappingsFile, tinyv2, StandardCharsets.UTF_8);
			}
		}
		
		log.info("|-> Parsing tinyv2 mappings in-memory...");
		try(BufferedReader buf = Files.newBufferedReader(mappingsFile)) {
			parsedMappings = TinyMappingFactory.loadWithDetection(buf);
			
			if(projectHasProperty("voldeloom.lowMemory")) {
				log.info("|-> Compressing in-memory representation using reflection crap...");
				new TreeSquisher().squish(log, parsedMappings);
			}
		}
		
		return this;
	}
	
	public TinyTree getTinyTree() {
		return parsedMappings;
	}
	
	public Path getMappingsFile() {
		return mappingsFile;
	}
}

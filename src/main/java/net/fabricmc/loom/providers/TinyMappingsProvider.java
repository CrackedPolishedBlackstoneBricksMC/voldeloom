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

import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.JarScanData;
import net.fabricmc.loom.util.mcp.McpTinyv2Writer;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Converts mappings from {@code RawMappingsProvider} into a tinyfile.
 */
public class TinyMappingsProvider extends DependencyProvider {
	@Inject
	public TinyMappingsProvider(Project project, LoomGradleExtension extension, RawMappingsProvider rawMappings, ForgePatchedProvider forgePatched) {
		super(project, extension);
		this.rawMappings = rawMappings;
		this.forgePatched = forgePatched;
		
		dependsOn(rawMappings, forgePatched);
	}
	
	private final RawMappingsProvider rawMappings;
	private final ForgePatchedProvider forgePatched;
	
	private Path tinyMappings;
	private TinyTree parsedMappings;
	
	@Override
	protected void performSetup() throws Exception {
		//outputs
		tinyMappings = getCacheDir().resolve("mappings").resolve(rawMappings.getRawMappingsJar().getFileName() + rawMappings.getMappingDiscriminant() + ".tiny");
		project.getLogger().lifecycle("] mappings destination: {}", tinyMappings);
		
		cleanOnRefreshDependencies(tinyMappings);
	}
	
	public void performInstall() throws Exception {
		Files.createDirectories(tinyMappings.getParent());
		
		if(Files.notExists(tinyMappings)) {
			project.getLogger().lifecycle("|-> Mappings file does not exist, writing...");
			
			if(rawMappings.isAlreadyTinyv2()) {
				//TODO: its the TINY PASSTHROUGH HACK !!
				Files.copy(rawMappings.getRawMappingsJar(), tinyMappings);
			} else {
				project.getLogger().lifecycle("|-> Scanning unmapped jar for field types/inner classes...");
				JarScanData scanData = new JarScanData().scan(forgePatched.getPatchedJar());
				
				project.getLogger().lifecycle("|-> Computing tinyv2 mappings...");
				List<String> tinyv2 = new McpTinyv2Writer()
					.srg(rawMappings.getJoined())
					.fields(rawMappings.getFields())
					.methods(rawMappings.getMethods())
					.packages(rawMappings.getPackages())
					.srgsAsFallback(extension.forgeCapabilities.useSrgsAsFallback())
					.jarScanData(scanData)
					.write();
				
				Files.write(tinyMappings, tinyv2, StandardCharsets.UTF_8);
			}
		}
		
		//make them available for other tasks
		try(BufferedReader lol = Files.newBufferedReader(tinyMappings)) {
			parsedMappings = TinyMappingFactory.loadWithDetection(lol);
		}
	}
	
	public TinyTree getMappings() {
		return parsedMappings;
	}
	
	public Path getTinyMappings() {
		return tinyMappings;
	}
	
	@Deprecated
	public String getMappingsDepString() {
		return rawMappings.getMappingsDepString();
	}
}

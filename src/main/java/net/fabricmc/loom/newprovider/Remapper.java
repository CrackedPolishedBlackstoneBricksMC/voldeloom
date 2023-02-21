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
import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.TinyRemapperSession;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Remaps the patched+accesstransformed Minecraft+Forge jar into the user's selected mappings.
 *
 * The named jar is available with {@code getMappedJar()}, and the intermediary (srg) jar is also available.
 */
public class Remapper extends NewProvider<Remapper> {
	public Remapper(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private TinyTree tinyTree;
	private String mappingsDepString;
	private String inputNamingScheme;
	private Path inputJar;
	private Set<String> deletedPrefixes;
	private Collection<Path> nonNativeLibs;
	
	//outputs
	private final Map<String, Path> outputJars = new HashMap<>();
	
	public Remapper tinyTree(TinyTree tinyTree) {
		this.tinyTree = tinyTree;
		return this;
	}
	
	public Remapper mappingsDepString(String mappingsDepString) {
		this.mappingsDepString = mappingsDepString;
		return this;
	}
	
	public Remapper inputJar(String inputNamingScheme, Path inputJar) {
		this.inputNamingScheme = inputNamingScheme;
		this.inputJar = inputJar;
		return this;
	}
	
	public Remapper deletedPrefixes(Set<String> deletedPrefixes) {
		this.deletedPrefixes = deletedPrefixes;
		return this;
	}
	
	public Remapper nonNativeLibs(Collection<Path> nonNativeLibs) {
		this.nonNativeLibs = nonNativeLibs;
		return this;
	}
	
	public Remapper addOutputJar(String namingScheme, String mappedJarName) {
		this.outputJars.put(namingScheme, getCacheDir().resolve("mapped").resolve(mappingsDepString).resolve(mappedJarName));
		return this;
	}
	
	public Path getMappedJar(String namingScheme) {
		return outputJars.get(namingScheme);
	}
	
	//procedure
	public Remapper remap() throws Exception {
		Preconditions.checkNotNull(tinyTree, "tiny tree");
		Preconditions.checkNotNull(inputJar, "input jar");
		Preconditions.checkNotNull(nonNativeLibs, "nonNativeLibs"); // ?
		
		boolean allExist = true;
		for(Map.Entry<String, Path> yeah : new HashMap<>(outputJars).entrySet()) {
			String namingScheme = yeah.getKey();
			Path jar = yeah.getValue();
			
			log.lifecycle("] {} jar: {}", namingScheme, jar);
			cleanOnRefreshDependencies(jar);
			if(Files.notExists(jar)) allExist = false;
		}
		
		if(!allExist) {
			log.lifecycle("|-> At least one mapped jar didn't exist, performing remap...");
			
			//ensure both are actually gone
			for(Path jar : outputJars.values()) Files.deleteIfExists(jar);
			
			//These are minecraft libraries that conflict with the ones forge wants
			//They're obfuscated and mcp maps them back to reality. The forge Ant script had a task to delete them lol.
			//https://github.com/MinecraftForge/FML/blob/8e7956397dd80902f7ca69c466e833047dfa5010/build.xml#L295-L298
			Predicate<String> classFilter = s -> !deletedPrefixes.contains(s.split("/", 2)[0]);
			
			TinyRemapperSession sesh = new TinyRemapperSession()
				.setMappings(tinyTree)
				.setInputJar(inputJar)
				.setInputNamingScheme(Constants.PROGUARDED_NAMING_SCHEME)
				.setInputClasspath(nonNativeLibs)
				.setClassFilter(classFilter)
				.setLogger(log::lifecycle);
			outputJars.forEach(sesh::addOutputJar);
			sesh.run();
			
			log.lifecycle("\\-> Remap success! :)");
		}
		
		return this;
	}
}

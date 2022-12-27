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

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.TinyRemapperSession;
import net.fabricmc.loom.util.WellKnownLocations;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MinecraftForgeMappedProvider extends DependencyProvider {
	private File minecraftMappedJar;
	private File minecraftIntermediaryJar;

	public MinecraftForgeMappedProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}

	@Override
	public void decorateProject() throws Exception {
		//inputs
		//TODO library provider will move
		MinecraftLibraryProvider libraryProvider = extension.getDependencyManager().getMinecraftProvider().getLibraryProvider();
		List<Path> libs = libraryProvider.getLibraries().stream().map(File::toPath).collect(Collectors.toList());
		
		//MinecraftForgePatchedProvider forgePatchedProvider = extension.getDependencyManager().getMinecraftForgePatchedProvider();
		MinecraftForgePatchedAccessTransformedProvider blah = extension.getDependencyManager().getMinecraftForgePatchedAccessTransformedProvider();
		File forgePatchedJar = blah.getPatchedAccessTransformedJar();
		
		MappingsProvider mappingsProvider = extension.getDependencyManager().getMappingsProvider();
		TinyTree mappings = mappingsProvider.getMappings();
		
		//outputs
		File userCache = WellKnownLocations.getUserCache(project);
		
		//TODO kludgy? yeah
		String intermediaryJarNameKinda = String.format("%s-%s-%s-%s",
			extension.getDependencyManager().getMinecraftProvider().getJarStuff(),
			"intermediary",
			extension.getDependencyManager().getMappingsProvider().mappingsName,
			extension.getDependencyManager().getMappingsProvider().mappingsVersion
		);
		String intermediaryJarName = "minecraft-" + intermediaryJarNameKinda + ".jar";
		
		String mappedJarNameKinda = String.format("%s-%s-%s-%s",
			extension.getDependencyManager().getMinecraftProvider().getJarStuff(),
			"mapped",
			extension.getDependencyManager().getMappingsProvider().mappingsName,
			extension.getDependencyManager().getMappingsProvider().mappingsVersion
		);
		String mappedJarName = "minecraft-" + mappedJarNameKinda + ".jar";
		File mappedDestDir = new File(userCache, mappedJarNameKinda);
		
		minecraftIntermediaryJar = new File(userCache, intermediaryJarName);
		minecraftMappedJar = new File(mappedDestDir, mappedJarName);
		
		//task
		project.getLogger().lifecycle("] intermediary jar is at: " + minecraftIntermediaryJar);
		project.getLogger().lifecycle("] mapped jar is at: " + minecraftMappedJar);
		if (!minecraftMappedJar.exists() || !getIntermediaryJar().exists()) {
			project.getLogger().lifecycle("|-> At least one didn't exist, performing remap...");
			
			//ensure both are actually gone
			if(minecraftMappedJar.exists()) minecraftMappedJar.delete();
			if(minecraftIntermediaryJar.exists()) minecraftIntermediaryJar.delete();
			
			//These are minecraft libraries that conflict with the ones forge wants
			//theyre obfuscated and mcp maps them back to reality
			//TODO, if leaving them in crashes, how did forge work back in the day?
			Predicate<String> classFilter = s -> !s.startsWith("argo") && !s.startsWith("org");
			
			new TinyRemapperSession()
				.setMappings(mappings)
				.setInputJar(forgePatchedJar.toPath())
				.setInputNamingScheme("official")
				.setInputClasspath(libs)
				.addOutputJar("intermediary", this.minecraftIntermediaryJar.toPath())
				.addOutputJar("named", this.minecraftMappedJar.toPath())
				.setClassFilter(classFilter)
				.setLogger(project.getLogger()::lifecycle)
				.run();
			
			project.getLogger().lifecycle("|-> Remap success! :)");
		}
		
		//TODO: move this out?
		project.getRepositories().flatDir(repository -> repository.dir(mappedDestDir));
		project.getDependencies().add(Constants.MINECRAFT_NAMED, project.getDependencies().module("net.minecraft:minecraft:" + mappedJarNameKinda));
	}

	public File getIntermediaryJar() {
		return minecraftIntermediaryJar;
	}

	public File getMappedJar() {
		return minecraftMappedJar;
	}
}
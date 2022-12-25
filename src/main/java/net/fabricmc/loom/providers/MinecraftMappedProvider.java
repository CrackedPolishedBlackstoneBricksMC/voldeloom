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
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.TinyRemapperSession;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.Project;

import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

public class MinecraftMappedProvider extends DependencyProvider {
	private File minecraftMappedJar;
	private File minecraftIntermediaryJar;

	private MinecraftProvider minecraftProvider;

	public MinecraftMappedProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	public MinecraftMappedProvider(Project project, LoomGradleExtension extension, String nameLeakyAbstraction) {
		super(project, extension);
	}

	@Override
	public void decorateProject() throws Exception {
		if (!extension.getDependencyManager().getMappingsProvider().tinyMappings.exists()) {
			throw new RuntimeException("mappings file not found");
		}
		
		if (!extension.getDependencyManager().getForgePatchedProvider().getPatchedJar().exists()) {
			throw new RuntimeException("input merged+patched jar not found");
		}

		if (!minecraftMappedJar.exists() || !getIntermediaryJar().exists()) {
			if(minecraftMappedJar.exists()) minecraftMappedJar.delete();
			if(minecraftIntermediaryJar.exists()) minecraftIntermediaryJar.delete();
			
			minecraftMappedJar.getParentFile().mkdirs();
			
			new TinyRemapperSession()
				.setMappings(extension.getDependencyManager().getMappingsProvider().getMappings())
				.setInputJar(extension.getDependencyManager().getForgePatchedProvider().getPatchedJar().toPath())
				.setInputNamingScheme("official")
				.setInputClasspath(getMinecraftDependencies().stream().map(File::toPath).collect(Collectors.toList()))
				.addOutputJar("intermediary", this.minecraftIntermediaryJar.toPath())
				.addOutputJar("named", this.minecraftMappedJar.toPath())
				.setLogger(project.getLogger()::lifecycle)
				.run();
		}

		if (!minecraftMappedJar.exists()) {
			throw new RuntimeException("mapped jar not found");
		}
		
		addDependencies();
	}

	protected void addDependencies() {
		project.getRepositories().flatDir(repository -> repository.dir(getJarDirectory(WellKnownLocations.getUserCache(project), "mapped")));
		
		project.getDependencies().add(Constants.MINECRAFT_NAMED,
				project.getDependencies().module("net.minecraft:minecraft:" + getJarVersionString("mapped")));
	}

	public void initFiles(MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
		this.minecraftProvider = minecraftProvider;
		minecraftIntermediaryJar = new File(WellKnownLocations.getUserCache(project), "minecraft-" + getJarVersionString("intermediary") + ".jar");
		minecraftMappedJar = new File(getJarDirectory(WellKnownLocations.getUserCache(project), "mapped"), "minecraft-" + getJarVersionString("mapped") + ".jar");
	}

	protected File getJarDirectory(File parentDirectory, String type) {
		return new File(parentDirectory, getJarVersionString(type));
	}

	protected String getJarVersionString(String type) {
		return String.format("%s-%s-%s-%s", minecraftProvider.getJarStuff(), type, extension.getDependencyManager().getMappingsProvider().mappingsName, extension.getDependencyManager().getMappingsProvider().mappingsVersion);
	}

	public Collection<File> getMinecraftDependencies() {
		return minecraftProvider.getLibraryProvider().getLibraries();
	}

	public File getIntermediaryJar() {
		return minecraftIntermediaryJar;
	}

	public File getMappedJar() {
		return minecraftMappedJar;
	}
}

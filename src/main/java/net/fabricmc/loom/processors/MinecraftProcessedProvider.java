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

package net.fabricmc.loom.processors;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.WellKnownLocations;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;

public class MinecraftProcessedProvider extends MinecraftMappedProvider {
	public static final String PROJECT_MAPPED_CLASSIFIER = "projectmapped";

	private File projectMappedJar;

	private final JarProcessorManager jarProcessorManager;

	public MinecraftProcessedProvider(Project project, LoomGradleExtension extension, JarProcessorManager jarProcessorManager) {
		super(project, extension, "Vanilla Minecraft, projectmapped");
		this.jarProcessorManager = jarProcessorManager;
	}

	@Override
	protected void addDependencies() {
		if (jarProcessorManager.isInvalid(projectMappedJar)) {
			project.getLogger().lifecycle(":processing mapped jar");
			invalidateJars();

			try {
				FileUtils.copyFile(super.getMappedJar(), projectMappedJar);
			} catch (IOException e) {
				throw new RuntimeException("Failed to copy source jar", e);
			}

			jarProcessorManager.process(projectMappedJar);
		}
		
		project.getRepositories().flatDir(repository -> repository.dir(getJarDirectory(WellKnownLocations.getProjectBuildCache(project), PROJECT_MAPPED_CLASSIFIER)));
		
		project.getDependencies().add(Constants.MINECRAFT_NAMED,
				project.getDependencies().module("net.minecraft:minecraft:" + getJarVersionString(PROJECT_MAPPED_CLASSIFIER)));
	}

	private void invalidateJars() {
		File dir = getJarDirectory(WellKnownLocations.getUserCache(project), PROJECT_MAPPED_CLASSIFIER);

		if (dir.exists()) {
			project.getLogger().warn("Invalidating project jars");

			try {
				FileUtils.cleanDirectory(dir);
			} catch (IOException e) {
				throw new RuntimeException("Failed to invalidate jars, try stopping gradle daemon or closing the game", e);
			}
		}
	}

	@Override
	public void initFiles(MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
		super.initFiles(minecraftProvider, mappingsProvider);
		
		projectMappedJar = new File(getJarDirectory(WellKnownLocations.getProjectBuildCache(project), PROJECT_MAPPED_CLASSIFIER), "minecraft-" + getJarVersionString(PROJECT_MAPPED_CLASSIFIER) + ".jar");
	}

	@Override
	public File getMappedJar() {
		return projectMappedJar;
	}
}

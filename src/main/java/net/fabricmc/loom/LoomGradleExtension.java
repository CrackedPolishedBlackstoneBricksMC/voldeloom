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

package net.fabricmc.loom;

import net.fabricmc.loom.processors.JarProcessorManager;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.util.LoomDependencyManager;
import net.fabricmc.loom.util.WellKnownLocations;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.Project;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class LoomGradleExtension {
	public LoomGradleExtension(Project project) {
		this.project = project;
	}
	
	public String runDir = "run";
	public String loaderLaunchMethod;
	public boolean remapMod = true;
	public boolean autoGenIDERuns = true;
	public String customManifest = null;
	public File accessWidener = null;

	private List<Path> unmappedModsBuilt = new ArrayList<>();

	//Not to be set in the build.gradle
	private final Project project;
	private LoomDependencyManager dependencyManager;
	private JarProcessorManager jarProcessorManager;
	private final MappingSet[] srcMappingCache = new MappingSet[2];
	private final Mercury[] srcMercuryCache = new Mercury[2];

	//(for SourceRemapper tasks)
	public MappingSet getOrCreateSrcMappingCache(int id, Supplier<MappingSet> factory) {
		return srcMappingCache[id] != null ? srcMappingCache[id] : (srcMappingCache[id] = factory.get());
	}
	
	//(for SourceRemapper tasks)
	public Mercury getOrCreateSrcMercuryCache(int id, Supplier<Mercury> factory) {
		return srcMercuryCache[id] != null ? srcMercuryCache[id] : (srcMercuryCache[id] = factory.get());
	}

	//set in LoomGradlePlugin afterEvaluate
	public void addUnmappedMod(Path file) {
		unmappedModsBuilt.add(file);
	}

	//AbstractRunTask and SourceRemapper
	public List<Path> getUnmappedMods() {
		return Collections.unmodifiableList(unmappedModsBuilt);
	}

	//several places, including run configs, launch provider, and MinecraftNativesProvider
	public File getNativesDirectory() {
		File natives = new File(WellKnownLocations.getUserCache(project), "natives/" + getMinecraftProvider().getMinecraftVersion());
		if (!natives.exists()) natives.mkdirs();
		return natives;
	}

	public String getLoaderLaunchMethod() {
		return loaderLaunchMethod != null ? loaderLaunchMethod : "";
	}

	public LoomDependencyManager getDependencyManager() {
		return dependencyManager;
	}

	public MinecraftProvider getMinecraftProvider() {
		return getDependencyManager().getProvider(MinecraftProvider.class);
	}

	public MinecraftMappedProvider getMinecraftMappedProvider() {
		return getMappingsProvider().mappedProvider;
	}

	public MappingsProvider getMappingsProvider() {
		return getDependencyManager().getProvider(MappingsProvider.class);
	}

	public void setDependencyManager(LoomDependencyManager dependencyManager) {
		this.dependencyManager = dependencyManager;
	}

	public JarProcessorManager getJarProcessorManager() {
		return jarProcessorManager;
	}

	public void setJarProcessorManager(JarProcessorManager jarProcessorManager) {
		this.jarProcessorManager = jarProcessorManager;
	}
}

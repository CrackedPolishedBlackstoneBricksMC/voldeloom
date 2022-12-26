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

import net.fabricmc.loom.providers.ForgeProvider;
import net.fabricmc.loom.providers.LaunchProvider;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftForgeMappedProvider;
import net.fabricmc.loom.providers.MinecraftForgePatchedAccessTransformedProvider;
import net.fabricmc.loom.providers.MinecraftForgePatchedProvider;
import net.fabricmc.loom.providers.MinecraftMergedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import org.gradle.api.Project;

/**
 * TODO(VOLDELOOM-DISASTER): Phase this out in favor of, say, real Gradle tasks.
 *  Anything that accesses this class is signaling that
 *  - it makes use of a "derived dependency" (like "minecraft, but merged and remapped")
 *  - it has to run *after* said dependency is set up (an ordering relationship)
 */
public class LoomDependencyManager {
	public LoomDependencyManager(Project project, LoomGradleExtension extension) {
		forgeProvider = new ForgeProvider(project, extension);
		minecraftProvider = new MinecraftProvider(project, extension);
		minecraftMergedProvider = new MinecraftMergedProvider(project, extension);
		minecraftForgePatchedProvider = new MinecraftForgePatchedProvider(project, extension);
		//After writing this 46-character class name, twice, on the same line, I'm starting to think a real task graph is a good idea
		minecraftForgePatchedAccessTransformedProvider = new MinecraftForgePatchedAccessTransformedProvider(project, extension);
		minecraftForgeMappedProvider = new MinecraftForgeMappedProvider(project, extension);
		mappingsProvider = new MappingsProvider(project, extension);
		launchProvider = new LaunchProvider(project, extension);
	}
	
	private final ForgeProvider forgeProvider;
	private final MinecraftProvider minecraftProvider;
	private final MinecraftMergedProvider minecraftMergedProvider;
	private final MinecraftForgePatchedProvider minecraftForgePatchedProvider;
	private final MinecraftForgePatchedAccessTransformedProvider minecraftForgePatchedAccessTransformedProvider;
	private final MinecraftForgeMappedProvider minecraftForgeMappedProvider;
	private final MappingsProvider mappingsProvider;
	private final LaunchProvider launchProvider;
	
	public ForgeProvider getForgeProvider() {
		return forgeProvider;
	}
	
	public MinecraftProvider getMinecraftProvider() {
		return minecraftProvider;
	}
	
	public MinecraftMergedProvider getMinecraftMergedProvider() {
		return minecraftMergedProvider;
	}
	
	public MinecraftForgePatchedProvider getMinecraftForgePatchedProvider() {
		return minecraftForgePatchedProvider;
	}
	
	public MinecraftForgePatchedAccessTransformedProvider getMinecraftForgePatchedAccessTransformedProvider() {
		return minecraftForgePatchedAccessTransformedProvider;
	}
	
	public MinecraftForgeMappedProvider getMinecraftForgeMappedProvider() {
		return minecraftForgeMappedProvider;
	}
	
	public MappingsProvider getMappingsProvider() {
		return mappingsProvider;
	}
	
	public LaunchProvider getLaunchProvider() {
		return launchProvider;
	}
}

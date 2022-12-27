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

/**
 * TODO(VOLDELOOM-DISASTER): Phase this out in favor of, say, real Gradle tasks.
 *  Anything that accesses this class is signaling that
 *  - it makes use of a "derived dependency" (like "minecraft, but merged and remapped")
 *  - it has to run *after* said dependency is set up (an ordering relationship)
 */
public class LoomDependencyManager {
	public LoomDependencyManager() {
	}
	
	private ForgeProvider forgeProvider;
	private MinecraftProvider minecraftProvider;
	private MinecraftMergedProvider minecraftMergedProvider;
	private MinecraftForgePatchedProvider minecraftForgePatchedProvider;
	private MinecraftForgePatchedAccessTransformedProvider longName;
	private MinecraftForgeMappedProvider minecraftForgeMappedProvider;
	private MappingsProvider mappingsProvider;
	private LaunchProvider launchProvider;
	
	public LoomDependencyManager installForgeProvider(ForgeProvider forgeProvider) {
		this.forgeProvider = forgeProvider;
		forgeProvider.decorateProjectOrThrow();
		return this;
	}
	
	public LoomDependencyManager installMinecraftProvider(MinecraftProvider minecraftProvider) {
		this.minecraftProvider = minecraftProvider;
		minecraftProvider.decorateProjectOrThrow();
		return this;
	}
	
	public LoomDependencyManager installMinecraftMergedProvider(MinecraftMergedProvider minecraftMergedProvider) {
		this.minecraftMergedProvider = minecraftMergedProvider;
		minecraftMergedProvider.decorateProjectOrThrow();
		return this;
	}
	
	public LoomDependencyManager installMinecraftForgePatchedProvider(MinecraftForgePatchedProvider minecraftForgePatchedProvider) {
		this.minecraftForgePatchedProvider = minecraftForgePatchedProvider;
		minecraftForgePatchedProvider.decorateProjectOrThrow();
		return this;
	}
	
	public LoomDependencyManager installMinecraftForgePatchedAccessTransformedProvider(MinecraftForgePatchedAccessTransformedProvider longName) {
		this.longName = longName;
		longName.decorateProjectOrThrow();
		return this;
	}
	
	public LoomDependencyManager installMinecraftForgeMappedProvider(MinecraftForgeMappedProvider minecraftForgeMappedProvider) {
		this.minecraftForgeMappedProvider = minecraftForgeMappedProvider;
		minecraftForgeMappedProvider.decorateProjectOrThrow();
		return this;
	}
	
	public LoomDependencyManager installMappingsProvider(MappingsProvider mappingsProvider) {
		this.mappingsProvider = mappingsProvider;
		mappingsProvider.decorateProjectOrThrow();
		return this;
	}
	
	public LoomDependencyManager installLaunchProvider(LaunchProvider launchProvider) {
		this.launchProvider = launchProvider;
		launchProvider.decorateProjectOrThrow();
		return this;
	}
	
	public ForgeProvider getForgeProvider() {
		if(forgeProvider == null) throw new IllegalStateException("forgeProvider hasn't been installed yet!");
		else return forgeProvider;
	}
	
	public MinecraftProvider getMinecraftProvider() {
		if(minecraftProvider == null) throw new IllegalStateException("minecraftProvider hasn't been installed yet!");
		else return minecraftProvider;
	}
	
	public MinecraftMergedProvider getMinecraftMergedProvider() {
		if(minecraftMergedProvider == null) throw new IllegalStateException("minecraftMergedProvider hasn't been installed yet!");
		else return minecraftMergedProvider;
	}
	
	public MinecraftForgePatchedProvider getMinecraftForgePatchedProvider() {
		if(minecraftForgePatchedProvider == null) throw new IllegalStateException("minecraftForgePatchedProvider hasn't been installed yet!");
		else return minecraftForgePatchedProvider;
	}
	
	public MinecraftForgePatchedAccessTransformedProvider getMinecraftForgePatchedAccessTransformedProvider() {
		if(longName == null) throw new IllegalStateException("minecraftForgePatchedAccessTransformedProvider hasn't been installed yet!");
		else return longName;
	}
	
	public MinecraftForgeMappedProvider getMinecraftForgeMappedProvider() {
		if(minecraftForgeMappedProvider == null) throw new IllegalStateException("minecraftForgeMappedProvider hasn't been installed yet!");
		else return minecraftForgeMappedProvider;
	}
	
	public MappingsProvider getMappingsProvider() {
		if(mappingsProvider == null) throw new IllegalStateException("mappingsProvider hasn't been installed yet!");
		else return mappingsProvider;
	}
	
	public LaunchProvider getLaunchProvider() {
		if(launchProvider == null) throw new IllegalStateException("launchProvider hasn't been installed yet!");
		else return launchProvider;
	}
}

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

import net.fabricmc.loom.providers.AssetsProvider;
import net.fabricmc.loom.providers.DevLaunchInjectorProvider;
import net.fabricmc.loom.providers.ForgePatchedAccessTxdProvider;
import net.fabricmc.loom.providers.ForgePatchedProvider;
import net.fabricmc.loom.providers.ForgeProvider;
import net.fabricmc.loom.providers.LibraryProvider;
import net.fabricmc.loom.providers.MappedProvider;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MergedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.providers.RemappedDependenciesProvider;

/**
 *  Anything that accesses this class is signaling that
 *  - it makes use of a "derived dependency" (like "minecraft, but merged and remapped")
 *  - it has to run *after* said dependency is set up (an ordering relationship)
 *  
 *  TODO(VOLDELOOM-DISASTER): Ideally this class shouldn't exist.
 *    The "provider" pattern is fine, but providers take dependencies through their constructor.
 *    These getters are only used when non-provider bits of code need access, which could be done with more Gradley methods
 *    (such as querying the contents of a configuration, dependency injection into a task, or whatever)
 */
public class LoomDependencyManager {
	public LoomDependencyManager() {
	}
	
	private ForgeProvider forgeProvider;
	private MinecraftProvider minecraftProvider;
	private AssetsProvider assetsProvider;
	private LibraryProvider libraryProvider;
	private MergedProvider mergedProvider;
	private ForgePatchedProvider forgePatchedProvider;
	private ForgePatchedAccessTxdProvider forgePatchedAccessTxdProvider;
	private MappingsProvider mappingsProvider;
	private MappedProvider mappedProvider;
	private RemappedDependenciesProvider remappedDependenciesProvider;
	private DevLaunchInjectorProvider devLaunchInjectorProvider;
	
	public ForgeProvider installForgeProvider(ForgeProvider forgeProvider) {
		this.forgeProvider = forgeProvider;
		forgeProvider.decorateProjectOrThrow();
		return forgeProvider;
	}
	
	public MinecraftProvider installMinecraftProvider(MinecraftProvider minecraftProvider) {
		this.minecraftProvider = minecraftProvider;
		minecraftProvider.decorateProjectOrThrow();
		return minecraftProvider;
	}
	
	public AssetsProvider installAssetsProvider(AssetsProvider assetsProvider) {
		this.assetsProvider = assetsProvider;
		assetsProvider.decorateProjectOrThrow();
		return assetsProvider;
	}
	
	public LibraryProvider installLibraryProvider(LibraryProvider libraryProvider) {
		this.libraryProvider = libraryProvider;
		libraryProvider.decorateProjectOrThrow();
		return libraryProvider;
	}
	
	public MergedProvider installMergedProvider(MergedProvider mergedProvider) {
		this.mergedProvider = mergedProvider;
		mergedProvider.decorateProjectOrThrow();
		return mergedProvider;
	}
	
	public ForgePatchedProvider installForgePatchedProvider(ForgePatchedProvider forgePatchedProvider) {
		this.forgePatchedProvider = forgePatchedProvider;
		forgePatchedProvider.decorateProjectOrThrow();
		return forgePatchedProvider;
	}
	
	public ForgePatchedAccessTxdProvider installForgePatchedAccessTxdProvider(ForgePatchedAccessTxdProvider longName) {
		this.forgePatchedAccessTxdProvider = longName;
		longName.decorateProjectOrThrow();
		return forgePatchedAccessTxdProvider;
	}
	
	public MappingsProvider installMappingsProvider(MappingsProvider mappingsProvider) {
		this.mappingsProvider = mappingsProvider;
		mappingsProvider.decorateProjectOrThrow();
		return mappingsProvider;
	}
	
	public MappedProvider installMappedProvider(MappedProvider mappedProvider) {
		this.mappedProvider = mappedProvider;
		mappedProvider.decorateProjectOrThrow();
		return mappedProvider;
	}
	
	public RemappedDependenciesProvider installRemappedDependenciesProvider(RemappedDependenciesProvider remappedDependenciesProvider) {
		this.remappedDependenciesProvider = remappedDependenciesProvider;
		remappedDependenciesProvider.decorateProjectOrThrow();
		return remappedDependenciesProvider;
	}
	
	public DevLaunchInjectorProvider installDevLaunchInjectorProvider(DevLaunchInjectorProvider devLaunchInjectorProvider) {
		this.devLaunchInjectorProvider = devLaunchInjectorProvider;
		devLaunchInjectorProvider.decorateProjectOrThrow();
		return devLaunchInjectorProvider;
	}
	
	public ForgeProvider getForgeProvider() {
		if(forgeProvider == null) throw new IllegalStateException("ForgeProvider hasn't been installed yet!");
		else return forgeProvider;
	}
	
	public MinecraftProvider getMinecraftProvider() {
		if(minecraftProvider == null) throw new IllegalStateException("MinecraftProvider hasn't been installed yet!");
		else return minecraftProvider;
	}
	
	public AssetsProvider getAssetsProvider() {
		if(assetsProvider == null) throw new IllegalStateException("AssetsProvider hasn't been installed yet!");
		return assetsProvider;
	}
	
	public LibraryProvider getLibraryProvider() {
		if(libraryProvider == null) throw new IllegalStateException("LibraryProvider hasn't been installed yet!");
		else return libraryProvider;
	}
	
	public MergedProvider getMergedProvider() {
		if(mergedProvider == null) throw new IllegalStateException("MergedProvider hasn't been installed yet!");
		else return mergedProvider;
	}
	
	public ForgePatchedProvider getForgePatchedProvider() {
		if(forgePatchedProvider == null) throw new IllegalStateException("ForgePatchedProvider hasn't been installed yet!");
		else return forgePatchedProvider;
	}
	
	public ForgePatchedAccessTxdProvider getForgePatchedAccessTxdProvider() {
		if(forgePatchedAccessTxdProvider == null) throw new IllegalStateException("ForgePatchedAccessTxdProvider hasn't been installed yet!");
		else return forgePatchedAccessTxdProvider;
	}
	
	public MappingsProvider getMappingsProvider() {
		if(mappingsProvider == null) throw new IllegalStateException("MappingsProvider hasn't been installed yet!");
		else return mappingsProvider;
	}
	
	public MappedProvider getMappedProvider() {
		if(mappedProvider == null) throw new IllegalStateException("MappedProvider hasn't been installed yet!");
		else return mappedProvider;
	}
	
	public RemappedDependenciesProvider getRemappedDependenciesProvider() {
		if(remappedDependenciesProvider == null) throw new IllegalStateException("RemappedDependenciesProvider hasn't been installed yet!");
		return remappedDependenciesProvider;
	}
	
	public DevLaunchInjectorProvider getDevLaunchInjectorProvider() {
		if(devLaunchInjectorProvider == null) throw new IllegalStateException("DevLaunchInjectorProvider hasn't been installed yet!");
		else return devLaunchInjectorProvider;
	}
}

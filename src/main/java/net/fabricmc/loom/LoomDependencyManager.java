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
import net.fabricmc.loom.providers.ForgeDependenciesProvider;
import net.fabricmc.loom.providers.ForgePatchedAccessTxdProvider;
import net.fabricmc.loom.providers.ForgePatchedProvider;
import net.fabricmc.loom.providers.ForgeProvider;
import net.fabricmc.loom.providers.MappedProvider;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MergedProvider;
import net.fabricmc.loom.providers.MinecraftDependenciesProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.providers.ProviderGraph;
import net.fabricmc.loom.providers.RemappedDependenciesProvider;
import org.gradle.api.Project;

/**
 * A nexus for derived depenencies (like "minecraft, but merged and remapped").<br>
 * TODO: replace with ProviderGraph
 */
public class LoomDependencyManager {
	public LoomDependencyManager(Project project, LoomGradleExtension extension) {
		this.graph = new ProviderGraph(project, extension);
	}
	
	private final ProviderGraph graph;
	
	public ForgeProvider getForgeProvider() {
		return graph.getProviderOfType(ForgeProvider.class);
	}
	
	public ForgeDependenciesProvider getForgeDependenciesProvider() {
		return graph.getProviderOfType(ForgeDependenciesProvider.class);
	}
	
	public MinecraftProvider getMinecraftProvider() {
		return graph.getProviderOfType(MinecraftProvider.class);
	}
	
	public AssetsProvider getAssetsProvider() {
		return graph.getProviderOfType(AssetsProvider.class);
	}
	
	public MinecraftDependenciesProvider getMinecraftDependenciesProvider() {
		return graph.getProviderOfType(MinecraftDependenciesProvider.class);
	}
	
	public MergedProvider getMergedProvider() {
		return graph.getProviderOfType(MergedProvider.class);
	}
	
	public ForgePatchedProvider getForgePatchedProvider() {
		return graph.getProviderOfType(ForgePatchedProvider.class);
	}
	
	public ForgePatchedAccessTxdProvider getForgePatchedAccessTxdProvider() {
		return graph.getProviderOfType(ForgePatchedAccessTxdProvider.class);
	}
	
	public MappingsProvider getMappingsProvider() {
		return graph.getProviderOfType(MappingsProvider.class);
	}
	
	public MappedProvider getMappedProvider() {
		return graph.getProviderOfType(MappedProvider.class);
	}
	
	public RemappedDependenciesProvider getRemappedDependenciesProvider() {
		return graph.getProviderOfType(RemappedDependenciesProvider.class);
	}
}

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
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A nexus for derived depenencies (like "minecraft, but merged and remapped").
 * Anything that accesses this class is signaling that it has to run <i>after</i> said dependency is derived (an ordering relationship).
 */
public class LoomDependencyManager {
	public LoomDependencyManager(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.graph = new ProviderGraph(project, extension);
		
		this.forgeProvider = graph.getProviderOfType(ForgeProvider.class);
		this.forgeDependenciesProvider = graph.getProviderOfType(ForgeDependenciesProvider.class);
		this.minecraftProvider = graph.getProviderOfType(MinecraftProvider.class);
		this.assetsProvider = graph.getProviderOfType(AssetsProvider.class);
		this.minecraftDependenciesProvider = graph.getProviderOfType(MinecraftDependenciesProvider.class);
		this.mergedProvider = graph.getProviderOfType(MergedProvider.class);
		this.forgePatchedProvider = graph.getProviderOfType(ForgePatchedProvider.class);
		this.forgePatchedAccessTxdProvider = graph.getProviderOfType(ForgePatchedAccessTxdProvider.class);
		this.mappingsProvider = graph.getProviderOfType(MappingsProvider.class);
		this.mappedProvider = graph.getProviderOfType(MappedProvider.class);
		this.remappedDependenciesProvider = graph.getProviderOfType(RemappedDependenciesProvider.class);
	}
	
	private final Project project;
	private final ProviderGraph graph;
	
	private final ForgeProvider forgeProvider;
	private final ForgeDependenciesProvider forgeDependenciesProvider;
	private final MinecraftProvider minecraftProvider;
	private final AssetsProvider assetsProvider;
	private final MinecraftDependenciesProvider minecraftDependenciesProvider;
	private final MergedProvider mergedProvider;
	private final ForgePatchedProvider forgePatchedProvider;
	private final ForgePatchedAccessTxdProvider forgePatchedAccessTxdProvider;
	private final MappingsProvider mappingsProvider;
	private final MappedProvider mappedProvider;
	private final RemappedDependenciesProvider remappedDependenciesProvider;
	
	public void installForgeProvider() {
		project.getLogger().lifecycle(":running dep provider 'ForgeProvider'");
		
		try {
			forgeProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide ForgeProvider: " + e.getMessage(), e);
		}
	}
	
	public void installForgeDependenciesProvider() {
		project.getLogger().lifecycle(":running dep provider 'ForgeDependenciesProvider'");
		
		try {
			forgeDependenciesProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide ForgeDependenciesProvider: " + e.getMessage(), e);
		}
	}
	
	public void installMinecraftProvider() {
		project.getLogger().lifecycle(":running dep provider 'MinecraftProvider'");
		
		try {
			minecraftProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide MinecraftProvider: " + e.getMessage(), e);
		}
	}
	
	public void installAssetsProvider() {
		project.getLogger().lifecycle(":running dep provider 'AssetsProvider'");
		
		try {
			assetsProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide AssetsProvider: " + e.getMessage(), e);
		}
	}
	
	public void installMinecraftDependenciesProvider() {
		project.getLogger().lifecycle(":running dep provider 'MinecraftDependenciesProvider'");
		
		try {
			minecraftDependenciesProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide MinecraftDependenciesProvider: " + e.getMessage(), e);
		}
	}
	
	public void installMergedProvider() {
		project.getLogger().lifecycle(":running dep provider 'MergedProvider'");
		
		try {
			mergedProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide MergedProvider: " + e.getMessage(), e);
		}
	}
	
	public void installForgePatchedProvider() {
		project.getLogger().lifecycle(":running dep provider 'ForgePatchedProvider'");
		
		try {
			forgePatchedProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide ForgePatchedProvider: " + e.getMessage(), e);
		}
	}
	
	public void installForgePatchedAccessTxdProvider() {
		project.getLogger().lifecycle(":running dep provider 'ForgePatchedAccessTxdProvider'");
		
		try {
			forgePatchedAccessTxdProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide ForgePatchedAccessTxdProvider: " + e.getMessage(), e);
		}
	}
	
	public void installMappingsProvider() {
		project.getLogger().lifecycle(":running dep provider 'MappingsProvider'");
		
		try {
			mappingsProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide MappingsProvider: " + e.getMessage(), e);
		}
	}
	
	public void installMappedProvider() {
		project.getLogger().lifecycle(":running dep provider 'MappedProvider'");
		
		try {
			mappedProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide MappedProvider: " + e.getMessage(), e);
		}
	}
	
	public void installRemappedDependenciesProvider() {
		project.getLogger().lifecycle(":running dep provider 'RemappedDependenciesProvider'");
		
		try {
			remappedDependenciesProvider.decorateProject();
		} catch (Exception e) {
			throw new RuntimeException("Failed to provide RemappedDependenciesProvider: " + e.getMessage(), e);
		}
	}
	
	public ForgeProvider getForgeProvider() {
		if(!forgeProvider.installed) throw new IllegalStateException("ForgeProvider hasn't been installed yet!");
		else return forgeProvider;
	}
	
	public MinecraftProvider getMinecraftProvider() {
		if(!minecraftProvider.installed) throw new IllegalStateException("MinecraftProvider hasn't been installed yet!");
		else return minecraftProvider;
	}
	
	public AssetsProvider getAssetsProvider() {
		if(!assetsProvider.installed) throw new IllegalStateException("AssetsProvider hasn't been installed yet!");
		return assetsProvider;
	}
	
	public MinecraftDependenciesProvider getMinecraftDependenciesProvider() {
		if(!minecraftDependenciesProvider.installed) throw new IllegalStateException("MinecraftDependenciesProvider hasn't been installed yet!");
		else return minecraftDependenciesProvider;
	}
	
	public MergedProvider getMergedProvider() {
		if(!mergedProvider.installed) throw new IllegalStateException("MergedProvider hasn't been installed yet!");
		else return mergedProvider;
	}
	
	public ForgePatchedProvider getForgePatchedProvider() {
		if(!forgePatchedProvider.installed) throw new IllegalStateException("ForgePatchedProvider hasn't been installed yet!");
		else return forgePatchedProvider;
	}
	
	public ForgePatchedAccessTxdProvider getForgePatchedAccessTxdProvider() {
		if(!forgePatchedAccessTxdProvider.installed) throw new IllegalStateException("ForgePatchedAccessTxdProvider hasn't been installed yet!");
		else return forgePatchedAccessTxdProvider;
	}
	
	public MappingsProvider getMappingsProvider() {
		if(!mappingsProvider.installed) throw new IllegalStateException("MappingsProvider hasn't been installed yet!");
		else return mappingsProvider;
	}
	
	public MappedProvider getMappedProvider() {
		if(!mappedProvider.installed) throw new IllegalStateException("MappedProvider hasn't been installed yet!");
		else return mappedProvider;
	}
	
	public RemappedDependenciesProvider getRemappedDependenciesProvider() {
		if(!remappedDependenciesProvider.installed) throw new IllegalStateException("RemappedDependenciesProvider hasn't been installed yet!");
		return remappedDependenciesProvider;
	}
	
	public List<TaskProvider<?>> getCleaningTasks() {
		return new ArrayList<>(Arrays.asList(
			forgeProvider.addCleaningTask(),
			minecraftProvider.addCleaningTask(),
			assetsProvider.addCleaningTask(),
			minecraftDependenciesProvider.addCleaningTask(),
			mergedProvider.addCleaningTask(),
			forgePatchedProvider.addCleaningTask(),
			forgePatchedAccessTxdProvider.addCleaningTask(),
			mappingsProvider.addCleaningTask(),
			mappedProvider.addCleaningTask(),
			remappedDependenciesProvider.addCleaningTask()
		));
	}
}

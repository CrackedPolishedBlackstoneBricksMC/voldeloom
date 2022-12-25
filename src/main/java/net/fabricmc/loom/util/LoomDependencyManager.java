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

package net.fabricmc.loom.util;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.forge.ForgeProvider;
import net.fabricmc.loom.providers.LaunchProvider;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.util.DependencyProvider.DependencyInfo;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO(VOLDELOOM-DISASTER): Phase this out in favor of, say, real Gradle tasks.
 *  Anything that accesses this class is signaling that
 *  - it makes use of a "derived dependency" (like "minecraft, but merged and remapped")
 *  - it has to run *after* said dependency is set up (an ordering relationship)
 */
public class LoomDependencyManager {
	private ForgeProvider forgeProvider;
	private MinecraftProvider minecraftProvider;
	private MappingsProvider mappingsProvider;
	private LaunchProvider launchProvider;
	
	public ForgeProvider getForgeProvider() {
		if(forgeProvider == null) throw new IllegalStateException("Null ForgeProvider");
		return forgeProvider;
	}
	
	public void setForgeProvider(ForgeProvider forgeProvider) {
		this.forgeProvider = forgeProvider;
	}
	
	public MinecraftProvider getMinecraftProvider() {
		if(minecraftProvider == null) throw new IllegalStateException("Null MinecraftProvider");
		return minecraftProvider;
	}
	
	public void setMinecraftProvider(MinecraftProvider minecraftProvider) {
		this.minecraftProvider = minecraftProvider;
	}
	
	public MappingsProvider getMappingsProvider() {
		if(mappingsProvider == null) throw new IllegalStateException("Null MappingsProvider");
		return mappingsProvider;
	}
	
	public void setMappingsProvider(MappingsProvider mappingsProvider) {
		this.mappingsProvider = mappingsProvider;
	}
	
	public LaunchProvider getLaunchProvider() {
		if(launchProvider == null) throw new IllegalStateException("Null LaunchProvider");
		return launchProvider;
	}
	
	public void setLaunchProvider(LaunchProvider launchProvider) {
		this.launchProvider = launchProvider;
	}
	
	//TODO: replace this with method inside the providers, too
	public void runProvider(Project project, DependencyProvider provider) {
		Configuration configuration = project.getConfigurations().getByName(provider.getTargetConfig());
		configuration.getDependencies().forEach(dependency -> {
			DependencyProvider.DependencyInfo info = DependencyInfo.create(project, dependency, configuration);
			
			try {
				provider.provide(info);
			} catch (Exception e) {
				throw new RuntimeException("Failed to provide " + dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion() + " : " + e.getMessage(), e);
			}
		});
	}
	
	//todo have not analyzed this code yet
	public void doWeirdModRemapStuff(Project project, LoomGradleExtension extension) {
		List<Runnable> afterTasks = new ArrayList<>();
		String mappingsKey = mappingsProvider.mappingsName + "." + mappingsProvider.minecraftVersion.replace(' ', '_').replace('.', '_').replace('-', '_') + "." + mappingsProvider.mappingsVersion;
		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			ModCompileRemapper.remapDependencies(
				project,
				mappingsKey,
				extension,
				entry.maybeCreateSourceConfiguration(project.getConfigurations()),
				entry.maybeCreateRemappedConfiguration(project.getConfigurations()),
				entry.maybeCreateTargetConfiguration(project.getConfigurations()),
				afterTasks::add
			);
		}
		
		for (Runnable runnable : afterTasks) {
			runnable.run();
		}
	}
}

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
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.DependencyProvider.DependencyInfo;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoomDependencyManager {
	private static class ProviderList {
		private final String key;
		private final List<DependencyProvider> providers = new ArrayList<>();

		ProviderList(String key) {
			this.key = key;
		}
	}

	private List<DependencyProvider> dependencyProviderList = new ArrayList<>();

	public void addProvider(DependencyProvider provider) {
		if (dependencyProviderList.contains(provider)) {
			throw new RuntimeException("Provider is already registered");
		}

		if (getProvider(provider.getClass()) != null) {
			throw new RuntimeException("Provider of this type is already registered");
		}

		provider.register(this);
		dependencyProviderList.add(provider);
	}

	public <T> T getProvider(Class<T> clazz) {
		for (DependencyProvider provider : dependencyProviderList) {
			if (provider.getClass() == clazz) {
				return (T) provider;
			}
		}

		return null;
	}

	public void handleDependencies(Project project) {
		MappingsProvider mappingsProvider = null;

		project.getLogger().lifecycle(":setting up loom dependencies");
		Map<String, ProviderList> providerListMap = new HashMap<>();
		List<ProviderList> targetProviders = new ArrayList<>();

		for (DependencyProvider provider : dependencyProviderList) {
			providerListMap.computeIfAbsent(provider.getTargetConfig(), (k) -> {
				ProviderList list = new ProviderList(k);
				targetProviders.add(list);
				return list;
			}).providers.add(provider);

			if (provider instanceof MappingsProvider) {
				mappingsProvider = (MappingsProvider) provider;
			}
		}

		if (mappingsProvider == null) {
			throw new RuntimeException("Could not find MappingsProvider instance!");
		}

		for (ProviderList list : targetProviders) {
			Configuration configuration = project.getConfigurations().getByName(list.key);
			configuration.getDependencies().forEach(dependency -> {
				DependencyProvider.DependencyInfo info = DependencyInfo.create(project, dependency, configuration);
				
				for (DependencyProvider provider : list.providers) {
					try {
						provider.provide(info);
					} catch (Exception e) {
						throw new RuntimeException("Failed to provide " + dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion() + " : " + e.getMessage(), e);
					}
				}
			});
		}

		{
			List<Runnable> afterTasks = new ArrayList<>();
			
			String mappingsKey = mappingsProvider.mappingsName + "." + mappingsProvider.minecraftVersion.replace(' ', '_').replace('.', '_').replace('-', '_') + "." + mappingsProvider.mappingsVersion;
			LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

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
}

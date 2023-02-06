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

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.ProviderGraph;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dependency providers are a Loom concept for "things that have to run in afterEvaluate".
 * 
 * @see ProviderGraph
 */
public abstract class DependencyProvider {
	public DependencyProvider(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.extension = extension;
	}
	
	protected final Project project;
	protected final LoomGradleExtension extension;
	
	protected abstract void performInstall() throws Exception;
	//TODO: only guaranteed to be populated inside/after decorateProject
	protected abstract Collection<Path> pathsToClean();
	
	private boolean installed = false;
	
	public void install() {
		if(installed) return;
		
		project.getLogger().lifecycle(":installing dep provider '{}'", getClass().getSimpleName());
		try {
			performInstall();
		} catch (Exception e) {
			throw new RuntimeException("Failed to install " + getClass().getSimpleName() + ": " + e.getMessage(), e);
		}
		project.getLogger().lifecycle(":finished installing dep provider '{}'!", getClass().getSimpleName());
		installed = true;
	}
	
	public void assertInstalled() {
		//but what if i were to install it now, and disguise it as my own assertion?
		//delightfully devilish, seymour
		install();
		
		if(!installed) throw new IllegalStateException(getClass().getSimpleName() + " hasn't been installed yet!");
	}
	
	public void cleanIfRefreshDependencies() {
		if(Constants.refreshDependencies) {
			project.getLogger().lifecycle("|-> Deleting outputs of " + getClass().getSimpleName() + " because --refresh-dependencies was passed");
			LoomGradlePlugin.delete(project, pathsToClean());
		}
	}
	
	///
	
	protected DependencyInfo getSingleDependency(String targetConfig) {
		Configuration config = project.getConfigurations().getByName(targetConfig);
		DependencySet set = config.getDependencies();
		
		if(set.size() == 0) {
			throw new IllegalStateException("Expected configuration '" + config.getName() + "' to resolve to one dependency, but found zero.");
		} else if(set.size() == 1) {
			return new DependencyInfo(project, set.iterator().next(), config);
		} else {
			StringBuilder builder = new StringBuilder("Expected configuration '");
			builder.append(config.getName());
			builder.append("' to resovle to one dependency, but found ");
			builder.append(set.size());
			builder.append(":");
			
			for (Dependency f : set) {
				builder.append("\n\t- ").append(f.toString());
			}
			
			throw new IllegalStateException(builder.toString());
		}
	}
	
	public static class DependencyInfo {
		final Project project;
		final Dependency dependency;
		final Configuration sourceConfiguration;

		public static DependencyInfo create(Project project, Dependency dependency, Configuration sourceConfiguration) {
			if (dependency instanceof SelfResolvingDependency) {
				return new FileDependencyInfo(project, (SelfResolvingDependency) dependency, sourceConfiguration);
			} else {
				return new DependencyInfo(project, dependency, sourceConfiguration);
			}
		}

		private DependencyInfo(Project project, Dependency dependency, Configuration sourceConfiguration) {
			this.project = project;
			this.dependency = dependency;
			this.sourceConfiguration = sourceConfiguration;
		}

		public Dependency getDependency() {
			return dependency;
		}

		public String getResolvedVersion() {
			for (ResolvedDependency rd : sourceConfiguration.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
				if (rd.getModuleGroup().equals(dependency.getGroup()) && rd.getModuleName().equals(dependency.getName())) {
					return rd.getModuleVersion();
				}
			}

			return dependency.getVersion();
		}

		public Configuration getSourceConfiguration() {
			return sourceConfiguration;
		}

		// TXDO: Can this be done with stable APIs only?
		//(VOLDELOOM-DISASTER) It's stable in Gradle 7, ya got lucky
		//@SuppressWarnings("UnstableApiUsage")
		public Set<File> resolve() {
			return sourceConfiguration.files(dependency);
		}
		
		public Path resolveSinglePath() {
			Set<File> files = resolve();
			
			if(files.size() == 0) {
				throw new IllegalStateException("Expected configuration '" + sourceConfiguration.getName() + "' to resolve to one file, but found zero.");
			} else if(files.size() == 1) {
				return files.iterator().next().toPath();
			} else {
				StringBuilder builder = new StringBuilder("Expected configuration '");
				builder.append(sourceConfiguration.getName());
				builder.append("' to resovle to one file, but found ");
				builder.append(files.size());
				builder.append(":");
				
				for (File f : files) {
					builder.append("\n\t- ").append(f.getAbsolutePath());
				}
				
				throw new IllegalStateException(builder.toString());
			}
		}

		@Override
		public String toString() {
			return getDepString();
		}

		public String getDepString() {
			return dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion();
		}

		public String getResolvedDepString() {
			return dependency.getGroup() + ":" + dependency.getName() + ":" + getResolvedVersion();
		}
	}

	public static class FileDependencyInfo extends DependencyInfo {
		protected final Map<String, File> classifierToFile = new HashMap<>();
		protected final String group, name, version;

		FileDependencyInfo(Project project, SelfResolvingDependency dependency, Configuration configuration) {
			super(project, dependency, configuration);

			Set<File> files = dependency.resolve();
			switch (files.size()) {
			case 0: //Don't think Gradle would ever let you do this
				throw new IllegalStateException("Empty dependency?");

			case 1: //Single file dependency
				classifierToFile.put("", Iterables.getOnlyElement(files));
				break;

			default: //File collection, try work out the classifiers
				List<File> sortedFiles = files.stream().sorted(Comparator.comparing(File::getName, Comparator.comparingInt(String::length))).collect(Collectors.toList());
				//First element in sortedFiles is the one with the shortest name, we presume all the others are different classifier types of this
				File shortest = sortedFiles.remove(0);
				String shortestName = removeExtension(shortest); //name.jar -> name

				for (File file : sortedFiles) {
					if (!file.getName().startsWith(shortestName)) {
						//If there is another file which doesn't start with the same name as the presumed classifier-less one we're out of our depth
						throw new IllegalArgumentException("Unable to resolve classifiers for " + this + " (failed to sort " + files + ')');
					}
				}

				//We appear to be right, therefore this is the normal dependency file we want
				classifierToFile.put("", shortest);
				int start = shortestName.length();

				for (File file : sortedFiles) {
					//Now we just have to work out what classifier type the other files are, this shouldn't even return an empty string
					String classifier = removeExtension(file).substring(start);

					//The classifier could well be separated with a dash (thing name.jar and name-sources.jar), we don't want that leading dash
					if (classifierToFile.put(classifier.charAt(0) == '-' ? classifier.substring(1) : classifier, file) != null) {
						throw new InvalidUserDataException("Duplicate classifiers for " + this + " (\"" + file.getName().substring(start) + "\" in " + files + ')');
					}
				}
			}

			if (dependency.getGroup() != null && dependency.getVersion() != null) {
				group = dependency.getGroup();
				name = dependency.getName();
				version = dependency.getVersion();
			} else {
				group = "net.fabricmc.synthetic";
				File root = classifierToFile.get(""); //We've built the classifierToFile map, now to try find a name and version for our dependency

				if("jar".equals(getExtension(root)) && ZipUtil.containsEntry(root, "fabric.mod.json")) {
					//It's a Fabric mod, see how much we can extract out
					JsonObject json = new Gson().fromJson(new String(ZipUtil.unpackEntry(root, "fabric.mod.json"), StandardCharsets.UTF_8), JsonObject.class);

					if (json == null || !json.has("id") || !json.has("version")) {
						throw new IllegalArgumentException("Invalid Fabric mod jar: " + root + " (malformed json: " + json + ')');
					}

					if (json.has("name")) { //Go for the name field if it's got one
						name = json.get("name").getAsString();
					} else {
						name = json.get("id").getAsString();
					}

					version = json.get("version").getAsString();
				} else {
					//Not a Fabric mod, just have to make something up
					name = removeExtension(root);
					version = "1.0";
				}
			}
		}

		@Override
		public String getResolvedVersion() {
			return version;
		}

		@Override
		public String getDepString() {
			//Use our custom name and version with the dummy group rather than the null:unspecified:null it would otherwise return
			return group + ':' + name + ':' + version;
		}

		@Override
		public String getResolvedDepString() {
			return getDepString();
		}
		
		public static String getExtension(File in) {
			String filename = in.getName();
			int dotIndex = filename.indexOf('.');
			if(dotIndex == -1 || dotIndex == filename.length() - 1) return "";
			else return filename.substring(dotIndex + 1);
		}
		
		public static String removeExtension(File in) {
			String filename = in.getName();
			int dotIndex = filename.indexOf('.');
			if(dotIndex == -1) return filename;
			else return filename.substring(0, dotIndex);
		}
	}
	
	protected Collection<Path> andEtags(Collection<Path> in) {
		ArrayList<Path> out = new ArrayList<>(in);
		for(Path i : in) {
			out.add(i.resolveSibling(i.getFileName().toString() + ".etag"));
		}
		return out;
	}
}

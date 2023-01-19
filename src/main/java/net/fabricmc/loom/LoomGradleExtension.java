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

import net.fabricmc.loom.util.GradleSupport;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.internal.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A Gradle extension. When you write <code>minecraft {</code> in a project's build.gradle, you get access to one of these.
 * The extension is added to the project at the top of LoomGradlePlugin.
 * @see LoomGradlePlugin
 * <p>
 * Originally (in Loom 0.4) this contained a million things. I am trying to strip it back to mainly be a thin configuration dsl for Gradle.
 * Obvious exception is anything relating to getDependencyManager.
 */
public class LoomGradleExtension {
	public LoomGradleExtension(@SuppressWarnings("unused") Project project) { //Gradle reflectively finds this ctor
		runConfigs = project.container(RunConfig.class, name -> new RunConfig(project, name));
		remappedConfigurationEntries = project.container(RemappedConfigurationEntry.class, inputName -> new RemappedConfigurationEntry(project, inputName));
		dependencyManager = new LoomDependencyManager(project, this);
	}
	
	/**
	 * A common error when updating to Gradle 7 is forgetting to switch `compile` to `implementation`.
	 * The plugin adds a rule to the project's configuration container that logs a message when you access, say, `modCompile`,
	 * but you're on an `implementation`-flavored Gradle.
	 */
	public boolean warnOnProbablyWrongConfigurationNames = true;
	
	/**
	 * If this is set to `false`, your mod won't get remapped to proguard names upon distribution.
	 * TODO: Not sure why you'd want this when the `-dev` jars do the same thing, lol
	 *  I heard Loom was initially developed under the assumption parts would be split off into other games (perhaps games that didn't use remapping),
	 *  so this is possibly vestige from then
	 */
	public boolean remapMod = true;
	
	/**
	 * If nonnull, this URL will be contacted to download the Minecraft per-version manifest, instead of reading from version_manifest.json. 
	 * @see net.fabricmc.loom.providers.MinecraftProvider
	 */
	public String customManifestUrl = null;
	
	/**
	 * Server that Minecraft's libraries are to be downloaded from, including trailing `/`.
	 * TODO: It appears that only native libraries are downloaded from this URL, and the rest are resolved over Maven normally.
	 */
	public String librariesBaseUrl = "https://libraries.minecraft.net/";
	
	/**
	 * Server that Minecraft Forge's libraries are to be downloaded from, including trailing `/`.
	 * The original server that Forge uses is long-dead; pick your favorite mirror.
	 */
	public String fmlLibrariesBaseUrl = "https://files.prismlauncher.org/fmllibs/";
	
	/**
	 * Server that Minecraft's assets are to be downloaded from, including trailing `/`.
	 */
	public String resourcesBaseUrl = "http://resources.download.minecraft.net/";
	
	/**
	 * Holder for run configurations (essentially a {@code Map<String, RunConfig>}).
	 * 
	 * In Groovy, prefer to use the `runs` method to configure instead.
	 */
	public final NamedDomainObjectContainer<RunConfig> runConfigs;
	
	/**
	 * Holder for remapped configuration entries. These are how Voldeloom manages artifacts in the `modCompileOnly`-et-al configurations.
	 * 
	 * In Groovy, prefer to use the `remappedConfigs` method to configure instead.
	 */
	public final NamedDomainObjectContainer<RemappedConfigurationEntry> remappedConfigurationEntries;
	
	/**
	 * If you're on Gradle 6 or above and this is `true`, Voldeloom will automatically provision a Java toolchain and use it for run configurations.
	 * This means you can execute Gradle with whatever you want, but still get a compatible JVM to use for running the game.
	 * (Gradles earlier than 6 do not have a toolchains feature, so they must be invoked using a compatible JVM, which is probably Java 8.)
	 */
	public boolean autoConfigureToolchains = true;
	
	/**
	 * This *JavaVersion* contains the default toolchain Java version, used when a run configuration does not configure a Java version of its own.
	 *
	 * Note that it's a JavaVersion, and not a JavaLanguageVersion. Ditto for the reasoning.
	 * The setter methods will accept JavaLanguageVersions and convert them to JavaVersions.
	 */
	public JavaVersion defaultRunToolchainVersion = JavaVersion.VERSION_1_8;
	
	/**
	 * This *string* contains the default toolchain vendor, used when a run configuration does not configure a vendor of its own.
	 * 
	 * Note that it's a string, and not a JvmVendorSpec. This is because Voldeloom remains source-compatible with Gradle 4, which doesn't have that class.
	 * The setter methods will accept JvmVendorSpecs and convert them to strings.
	 */
	public String defaultRunToolchainVendor = "ADOPTIUM";
	
	private final LoomDependencyManager dependencyManager;
	private final List<Path> unmappedModsBuilt = new ArrayList<>();
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

	//AbstractRunTask and SourceRemapper - todo why
	public List<Path> getUnmappedMods() {
		return Collections.unmodifiableList(unmappedModsBuilt);
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void runs(Action<NamedDomainObjectContainer<RunConfig>> action) {
		action.execute(runConfigs);
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void remappedConfigs(Action<NamedDomainObjectContainer<RemappedConfigurationEntry>> action) {
		boolean last = warnOnProbablyWrongConfigurationNames;
		warnOnProbablyWrongConfigurationNames = false;
		
		action.execute(remappedConfigurationEntries);
		
		warnOnProbablyWrongConfigurationNames = last;
	}
	
	public LoomDependencyManager getDependencyManager() {
		return dependencyManager;
	}
	
	//Toolchain support, awkwardly at arm's length because of the Gradle 4 source-compatibility restriction
	public void setDefaultRunToolchainVersion(Object version) {
		this.defaultRunToolchainVersion = GradleSupport.convertToJavaVersion(version);
	}
	
	public void setDefaultRunToolchainVendor(Object vendor) {
		this.defaultRunToolchainVendor = GradleSupport.convertToVendorString(vendor);
	}
	
	public void setToolchain(Object toolchain) {
		Pair<JavaVersion, String> parsedToolchain = GradleSupport.readToolchainSpec(toolchain);
		this.defaultRunToolchainVersion = parsedToolchain.left;
		this.defaultRunToolchainVendor = parsedToolchain.right;
	}
}

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

import net.fabricmc.loom.mcp.layer.LayeredMcpMappings;
import net.fabricmc.loom.util.GradleSupport;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Gradle extension. When you write <code>minecraft {</code> in a project's build.gradle, you get access to one of these.
 * The extension is added to the project at the top of LoomGradlePlugin.
 * @see LoomGradlePlugin
 * <p>
 * Originally (in Loom 0.4) this contained a million things. I am trying to strip it back to mainly be a thin configuration dsl for Gradle.
 * Obvious exception is anything relating to getDependencyManager.
 */
@SuppressWarnings("CanBeFinal") //settable from buildscript
public class LoomGradleExtension {
	public LoomGradleExtension(Project project) { //Gradle reflectively finds this ctor
		this.project = project;
		runConfigs = project.container(RunConfig.class, name -> new RunConfig(project, name));
		remappedConfigurationEntries = project.container(RemappedConfigurationEntry.class, inputName -> new RemappedConfigurationEntry(project, inputName));
		providers = new ProviderGraph(project, this);
		forgeCapabilities = new ForgeCapabilities(project, this);
		
		if(project.getGradle().getStartParameter().isOffline()) {
			offline = true;
			project.getLogger().lifecycle("!! Enabling Voldeloom's offline mode because Gradle was started with --offline");
		} else if(System.getProperty("voldeloom.offline") != null) {
			offline = true;
			project.getLogger().lifecycle("!! Enabling Voldeloom's offline mode because a `voldeloom.offline` system property exists");
		} else if(project.hasProperty("voldeloom.offline")) {
			offline = true;
			project.getLogger().lifecycle("!! Enabling Voldeloom's offline mode because a `voldeloom.offline` project property exists");
		} else offline = false;
		
		if(project.getGradle().getStartParameter().isRefreshDependencies()) {
			refreshDependencies = true;
			project.getLogger().lifecycle("!! Enabling Voldeloom's refreshDependencies mode because Gradle was started with --refresh-dependencies");
		} else if(System.getProperty("voldeloom.refreshDependencies") != null) {
			refreshDependencies = true;
			project.getLogger().lifecycle("!! Enabling Voldeloom's refreshDependencies mode because a `voldeloom.refreshDependencies` system property exists");
		} else if(project.hasProperty("voldeloom.refreshDependencies")) {
			refreshDependencies = true;
			project.getLogger().lifecycle("!! Enabling Voldeloom's refreshDependencies mode because a `voldeloom.refreshDependencies` project property exists");
		} else refreshDependencies = false;
	}
	
	private final Project project;
	
	/**
	 * A common error when updating to Gradle 7 is forgetting to switch `compile` to `implementation`.
	 * The plugin adds a rule to the project's configuration container that logs a message when you access, say, `modCompile`,
	 * but you're on an `implementation`-flavored Gradle.
	 */
	public boolean warnOnProbablyWrongConfigurationNames = true;
	
	/**
	 * If nonnull, this URL will be contacted to download the Minecraft per-version manifest, instead of reading from version_manifest.json. 
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
	public String resourcesBaseUrl = "https://resources.download.minecraft.net/";
	
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
	 * Note that it's a JavaVersion, and not a JavaLanguageVersion. This is because Voldeloom remains source-compatible with Gradle 4, which doesn't have that class.
	 * The setter methods will accept JavaLanguageVersions and convert them to JavaVersions.
	 */
	public JavaVersion defaultRunToolchainVersion = JavaVersion.VERSION_1_8;
	
	/**
	 * This *string* contains the default toolchain vendor, used when a run configuration does not configure a vendor of its own.
	 * 
	 * Note that it's a string, and not a JvmVendorSpec. Same reason as above.
	 * The setter methods will accept JvmVendorSpecs and convert them to strings.
	 */
	public String defaultRunToolchainVendor = "ADOPTIUM";
	
	/**
	 * Metadata about what this version of Forge can do, and what this era of the mod ecosystem expects.
	 */
	public final ForgeCapabilities forgeCapabilities;
	
	/**
	 * If 'true', Voldeloom will not download anything from the internet. When a download is required, the plugin will throw
	 * an exception if the file doesn't already exist on your computer.
	 * <p>
	 * You can configure this property:
	 * <ul>
	 *   <li>manually through this Gradle extension (although it will only take effect after reaching that point in the buildscript),</li>
	 *   <li>by passing {@code --offline} to Gradle,</li>
	 *   <li>by setting the {@code voldeloom.offline} project property (possibly by passing {@code -Pvoldeloom.offline} to Gradle),</li>
	 *   <li>by setting the {@code voldeloom.offline} system property (possibly using an environment variable)</li>
	 * </ul>
	 */
	public boolean offline;
	
	/**
	 * If 'true', Voldeloom will delete and recompute all derived artifacts, such as "minecraft but remapped".<br>
	 * (The exception is that the assets will not be redownloaded. Asset-related issues are rarely the problem, and they take a long time.)
	 * <p>
	 * You can configure this property:
	 * <ul>
	 *   <li>manually through this Gradle extension (although it will only take effect after reaching that point in the buildscript),</li>
	 *   <li>by passing {@code --refresh-dependencies} to Gradle,</li>
	 *   <li>by setting the {@code voldeloom.refreshDependencies} project property (possibly by passing {@code -Pvoldeloom.refreshDependencies} to Gradle),</li>
	 *   <li>by setting the {@code voldeloom.refreshDependencies} system property (possibly using an environment variable)</li>
	 * </ul>
	 */
	public boolean refreshDependencies;
	
	/**
	 * Callback with a bit more precision than "afterEvaluate". Evaluated before the internal ProviderGraph is evaluated
	 * and before the project has been configured with all the Minecraft-related dependencies.
	 * In Groovy, prefer to use the "beforeMinecraftSetup" function instead.
	 */
	public List<Action<? super Project>> beforeMinecraftSetupActions = new ArrayList<>();
	
	/**
	 * Callback with a bit more precision than "afterEvaluate". Evaluated after Voldeloom has finished its afterEvaluate block.
	 * In Groovy, prefer to use the "afterMinecraftSetup" function instead.
	 */
	public List<Action<? super Project>> afterMinecraftSetupActions = new ArrayList<>();
	
	private final ProviderGraph providers;
	
	private final List<Path> unmappedModsBuilt = new ArrayList<>();

	//set in LoomGradlePlugin afterEvaluate
	public void addUnmappedMod(Path file) {
		unmappedModsBuilt.add(file);
	}

	//AbstractRunTask - todo why, can i use a configuration instead
	public List<Path> getUnmappedMods() {
		return Collections.unmodifiableList(unmappedModsBuilt);
	}
	
	public ProviderGraph getProviderGraph() {
		return providers;
	}
	
	@SuppressWarnings("unused") //Gradle api
	public Dependency layered(Action<LayeredMcpMappings> action) {
		LayeredMcpMappings layered = new LayeredMcpMappings(project, this);
		action.execute(layered);
		return layered.createDependency();
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void runs(Action<NamedDomainObjectContainer<RunConfig>> action) {
		action.execute(runConfigs);
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void forgeCapabilities(Action<ForgeCapabilities> action) {
		action.execute(forgeCapabilities);
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void remappedConfigs(Action<NamedDomainObjectContainer<RemappedConfigurationEntry>> action) {
		//if you're manually adding a configuration with a "wrong" name I'll assume you're doing it on purpose
		boolean last = warnOnProbablyWrongConfigurationNames;
		warnOnProbablyWrongConfigurationNames = false;
		
		action.execute(remappedConfigurationEntries);
		
		warnOnProbablyWrongConfigurationNames = last;
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void beforeMinecraftSetup(Action<? super Project> action) {
		beforeMinecraftSetupActions.add(action);
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void afterMinecraftSetup(Action<? super Project> action) {
		afterMinecraftSetupActions.add(action);
	}
	
	//Toolchain support, awkwardly at arm's length because of the Gradle 4 source-compatibility restriction
	@SuppressWarnings("unused") //Gradle api
	public void setDefaultRunToolchainVersion(Object version) {
		this.defaultRunToolchainVersion = GradleSupport.convertToJavaVersion(version);
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void setDefaultRunToolchainVendor(Object vendor) {
		this.defaultRunToolchainVendor = GradleSupport.convertToVendorString(vendor);
	}
	
	@SuppressWarnings("unused") //Gradle api
	public void setToolchain(Object toolchain) {
		GradleSupport.ToolchainSpecResult parsedToolchain = GradleSupport.readToolchainSpec(toolchain);
		this.defaultRunToolchainVersion = parsedToolchain.javaVersion;
		this.defaultRunToolchainVendor = parsedToolchain.vendorString;
	}
}

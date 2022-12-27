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

import groovy.util.Node;
import net.fabricmc.loom.task.ShimForgeClientLibraries;
import net.fabricmc.loom.providers.ForgeProvider;
import net.fabricmc.loom.providers.DevLaunchInjectorProvider;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.AssetsProvider;
import net.fabricmc.loom.providers.MappedProvider;
import net.fabricmc.loom.providers.ForgePatchedAccessTxdProvider;
import net.fabricmc.loom.providers.ForgePatchedProvider;
import net.fabricmc.loom.providers.LibraryProvider;
import net.fabricmc.loom.providers.MergedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.task.AbstractDecompileTask;
import net.fabricmc.loom.task.CleanLoomBinaries;
import net.fabricmc.loom.task.CleanLoomMappings;
import net.fabricmc.loom.task.GenEclipseRunsTask;
import net.fabricmc.loom.task.GenIdeaProjectTask;
import net.fabricmc.loom.task.GenVsCodeProjectTask;
import net.fabricmc.loom.task.MigrateMappingsTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapLineNumbersTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.task.RunClientTask;
import net.fabricmc.loom.task.RunServerTask;
import net.fabricmc.loom.task.ShimResourcesTask;
import net.fabricmc.loom.task.fernflower.FernFlowerTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.GroovyXmlUtil;
import net.fabricmc.loom.util.ModCompileRemapper;
import net.fabricmc.loom.util.RemappedConfigurationEntry;
import net.fabricmc.loom.util.SetupIntelijRunConfigs;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class LoomGradlePlugin implements Plugin<Project> {
	/**
	 * Deletes a file or directory using Gradle's machinery. Announces each file to be deleted.
	 * Gradle's machinery accepts a million different types of object here, but the most important ones are `File` and `Path`.
	 * Deleting a directory will recursively delete all files inside it, too.
	 */
	public static void delete(Project project, Object... things) {
		project.delete(deleteSpec -> {
			deleteSpec.setFollowSymlinks(false);
			
			for(Object thing : things) {
				project.getLogger().info("Deleting " + thing);
				deleteSpec.delete(thing);
			}
		});
	}
	
	@Override
	public void apply(Project project) {
		project.getLogger().lifecycle("applying Voldeloom " + getClass().getPackage().getImplementationVersion() + " to " + project.getDisplayName());
		project.getLogger().lifecycle("Java version is " + System.getProperty("java.version") + ". I hope it works :)");
		
		//Apply a handful of bonus plugins. This acts the same as writing `apply plugin: 'java'` in the buildscript.
		project.getPlugins().apply("java");
		project.getPlugins().apply("eclipse");
		project.getPlugins().apply("idea");
		GradleSupport.init(project); //has to be done after `java` is applied i think
		
		//Create a DSL extension. This defines a `minecraft { }` block in the buildscript, that you may configure settings with.
		//The user's configuration is not available yet, because we're still executing the "apply plugin" line at this point.
		//It will be ready inside `project.afterEvaluate` blocks and during task execution.
		project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
		
		//Configure a few bonus Maven repositories. This acts the same as entering them in a `repositories { }` block in the buildscript.
		project.getRepositories().maven(repo -> {
			repo.setName("Mojang");
			repo.setUrl("https://libraries.minecraft.net/");
		});
		project.getRepositories().maven(repo -> {
			//(VOLDELOOM-DISASTER) Add Forge's repository as well, set up the incantations required to make it work
			repo.setName("Minecraft Forge");
			repo.setUrl("https://maven.minecraftforge.net/");
			GradleSupport.maybeSetIncludeGroup(repo, "net.minecraftforge");
			//Gradle 5 and above, by default, assumes an artifact doesn't exist if it can't find a maven_metadata.xml, to cut down on the amount
			//of spurious 404 requests. But Forge doesn't publish any maven pom files for their old versions, so this opts in to the old behavior.
			//I don't believe this breaks Gradle 4.
			repo.metadataSources(MavenArtifactRepository.MetadataSources::artifact);
		});
		//Remapped mods TODO fix
		project.getRepositories().flatDir(repo -> {
			repo.dir(WellKnownLocations.getRemappedModCache(project));
			repo.setName("UserLocalRemappedMods");
		});
		//Needed for a dep of ASM 4.1 which is a dep of Launchwrapper which is a dep of Minecraft
		//Also, apparently needed for random parent POMs like org.lwjgl.lwjgl:parent:2.9.0, don't ask me, i have no clue
		project.getRepositories().mavenCentral();
		
		//Next, we define a bunch of Configurations. (More on them here: https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.Configuration.html )
		//A configuration is a set of artifacts and their dependencies. These are typically referenced by-name, so we don't need to shelve the objects anywhere.
		//Later, tasks can query the artifacts inside a particular Configuration (through `configuration.resolve()` or whatever)
		//E.g. when a modder writes something like `minecraft "com.mojang:minecraft:1.4.7"`, we query the "minecraft" configuration to retrieve it.
		//
		//Oh, and there's inheritance relationships between them too.
		//You can read a bit more about what "extendsFrom" does here: https://docs.gradle.org/current/userguide/declaring_dependencies.html#sub:config-inheritance-composition
		//In short, I think that if configuration A extends B, all the artifacts in B have to be ready before A can be ready.
		Configuration compileOrImplementation = GradleSupport.getCompileOrImplementationConfiguration(project.getConfigurations());
		
		//All mods on the compilation classpath, including the mod under development.
		Configuration modCompileClasspath = project.getConfigurations().maybeCreate(Constants.MOD_COMPILE_CLASSPATH).setTransitive(true);
		//TODO: dont know what this one's used for lol
		Configuration modCompileClasspathMapped = project.getConfigurations().maybeCreate(Constants.MOD_COMPILE_CLASSPATH_MAPPED).setTransitive(false);
		
		//Vanilla Minecraft, straight off Mojang's server.
		Configuration minecraft = project.getConfigurations().maybeCreate(Constants.MINECRAFT).setTransitive(false);
		
		//The dependencies for Minecraft itself, such as LWJGL.
		Configuration minecraftDependencies = project.getConfigurations().maybeCreate(Constants.MINECRAFT_DEPENDENCIES).setTransitive(false);
		
		//Vanilla Minecraft, remapped to the mappings chosen by the modder.
		Configuration minecraftNamed = project.getConfigurations().maybeCreate(Constants.MINECRAFT_NAMED).setTransitive(false);
		compileOrImplementation.extendsFrom(minecraftNamed);
		minecraftNamed.extendsFrom(minecraftDependencies);
		
		//Mappings. This one's the raw MCP artifact,
		project.getConfigurations().maybeCreate(Constants.MAPPINGS).setTransitive(true);
		//and this one's the mappings in a format tiny-remapper can understand. Kinda a hack.
		Configuration mappingsFinal = project.getConfigurations().maybeCreate(Constants.MAPPINGS_FINAL).setTransitive(true);
		compileOrImplementation.extendsFrom(mappingsFinal);
		
		//Forge! TODO, what exactly is in this one and how does it get there, lol
		project.getConfigurations().maybeCreate(Constants.FORGE).setTransitive(false); //disable transitive to be safe -- forge will load its deps at runtime.
		
		//Modded *versions* of existing Java configuration types, such as `modCompile`, `modApi`, etc
		for(RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			//e.g. "modImplementation"
			Configuration modSource = entry.maybeCreateSourceConfiguration(project.getConfigurations()).setTransitive(true);
			//e.g. "modImplementationMapped"
			Configuration modRemapped = entry.maybeCreateRemappedConfiguration(project.getConfigurations()).setTransitive(false); // Don't get transitive deps of already remapped mods
			//e.g. "implementation"
			Configuration modTarget = entry.maybeCreateTargetConfiguration(project.getConfigurations());
			modTarget.extendsFrom(modRemapped);
			
			if(entry.isOnModCompileClasspath()) {
				modCompileClasspath.extendsFrom(modSource);
				modCompileClasspathMapped.extendsFrom(modRemapped);
			}
		}
		
		//Preconfigure a couple things in IntelliJ. Nothing you can't do yourself by clicking on things, but, well, now you don't have to.
		//This can also be configured by writing your own `idea { }` block in the script.
		IdeaModel ideaModel = (IdeaModel) project.getExtensions().getByName("idea");
		//Tell IDEA not to search in these locations when indexing, doing find-usages, etc
		ideaModel.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
		//Enable the janky automatic downloaders? TODO is this in-scope lmao
		ideaModel.getModule().setDownloadJavadoc(true);
		ideaModel.getModule().setDownloadSources(true);
		//Now you don't have to configure the build directory manually!!!!! Why is this a thing!!!! I love intellij
		ideaModel.getModule().setInheritOutputDirs(true);
		
		//And now, we add a bunch of Gradle tasks.
		//Note that `register` doesn't add the task right away, but Gradle will reflectively creates it when someone asks for it,
		//and (if a closure is specified) call the closure to configure the task. Optimization thing, I guess.
		TaskContainer tasks = project.getTasks();
		//Cleaning:
		tasks.register("cleanLoomBinaries", CleanLoomBinaries.class);
		tasks.register("cleanLoomMappings", CleanLoomMappings.class);
		tasks.register("cleanLoom").configure(task -> {
			task.setGroup("fabric");
			task.dependsOn("cleanLoomBinaries", "cleanLoomMappings");
		});
		
		//Utility:
		tasks.register("migrateMappings", MigrateMappingsTask.class);
		
		//Remapping artifacts:
		tasks.register("remapJar", RemapJarTask.class);
		tasks.register("remapSourcesJar", RemapSourcesJarTask.class, task -> task.setGroup("fabric"));
		
		//genSources:
		tasks.register("genSourcesDecompile", FernFlowerTask.class);
		tasks.register("genSourcesRemapLineNumbers", RemapLineNumbersTask.class, task -> task.dependsOn("genSourcesDecompile"));
		tasks.register("genSources", task -> {
			task.setGroup("fabric");
			task.getOutputs().upToDateWhen(t -> false);
			task.dependsOn("genSourcesRemapLineNumbers");
		});
		
		//IDE integration and run configs:
		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, t -> t.dependsOn("idea"));
		tasks.register("genEclipseRuns", GenEclipseRunsTask.class);
		tasks.register("vscode", GenVsCodeProjectTask.class);
		tasks.register("shimForgeClientLibraries", ShimForgeClientLibraries.class);
		tasks.register("shimResources", ShimResourcesTask.class);
		tasks.register("runClient", RunClientTask.class, t -> t.dependsOn("assemble", "shimForgeClientLibraries", "shimResources"));
		tasks.register("runServer", RunServerTask.class, t -> t.dependsOn("assemble"));
		
		//TODO is it safe to configure this now? I ask because upstream did it in afterEvaluate
		tasks.named("idea").configure(t -> t.finalizedBy(tasks.named("genIdeaWorkspace")));
		tasks.named("eclipse").configure(t -> t.finalizedBy(tasks.named("genEclipseRuns")));
		
		//So. build.gradle files *look* declarative, but recall that they are imperative programs, executed top-to-bottom.
		//All of the above happens immediately upon encountering the `apply plugin` line. The rest of the script hasn't executed yet.
		//But what if we want to make choices based on the things the user configured in LoomGradleExtensions?
		//With an afterEvaluate block, we can ask to have control passed back to us after the build.gradle file is finished executing.
		project.afterEvaluate(this::afterEvaluate);
	}
	
	private void afterEvaluate(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		
		LoomDependencyManager dmgr = extension.getDependencyManager();
		//This is where the Magic happens.
		//These dependencies are dynamic; their content depends on the values of the stuff configured in LoomGradleExtension
		//Even though the process of creating them *looks* like a task graph, task execution is too late to configure dependencies
		//Without the ability to leverage Gradle's task graph, doing it all in `afterEvaluate` is the best we can do, regrettably.
		//At least I can fake a task graph via constructor parameters...
		
		//forge jar
		ForgeProvider forge = dmgr.installForgeProvider(new ForgeProvider(project, extension));
		//vanilla minecraft
		MinecraftProvider mc = dmgr.installMinecraftProvider(new MinecraftProvider(project, extension, forge));
		AssetsProvider assets = dmgr.installAssetsProvider(new AssetsProvider(project, extension, mc));
		LibraryProvider libs = dmgr.installLibraryProvider(new LibraryProvider(project, extension, mc));
		MergedProvider merged = dmgr.installMergedProvider(new MergedProvider(project, extension, mc));
		
		//forge + vanilla
		ForgePatchedProvider forgePatched = dmgr.installForgePatchedProvider(new ForgePatchedProvider(project, extension, mc, merged, forge));
		ForgePatchedAccessTxdProvider patchedTxd = dmgr.installForgePatchedAccessTxdProvider(new ForgePatchedAccessTxdProvider(project, extension, mc, forge, forgePatched));
		
		//mappings
		MappingsProvider mappings = dmgr.installMappingsProvider(new MappingsProvider(project, extension, mc, forgePatched));
		
		//forge + vanilla + mappings
		MappedProvider mapped = dmgr.installMappedProvider(new MappedProvider(project, extension, mc, libs, patchedTxd, mappings));
		
		//dev-launch-injector stuff that's not used at all
		DevLaunchInjectorProvider dli = dmgr.installDevLaunchInjectorProvider(new DevLaunchInjectorProvider(project, extension, mc, libs));
		
		//very strange block related to `modCompile`etc configurations that i moved here from LoomDependencyManager
		//todo this probably needs rewriting
		{
			List<Runnable> afterTasks = new ArrayList<>();
			String mappingsKey = mappings.mappingsName + "." + mappings.minecraftVersion.replace(' ', '_').replace('.', '_').replace('-', '_') + "." + mappings.mappingsVersion;
			for(RemappedConfigurationEntry entry1 : Constants.MOD_COMPILE_ENTRIES) {
				ModCompileRemapper.remapDependencies(
					project,
					mappingsKey,
					extension,
					entry1.maybeCreateSourceConfiguration(project.getConfigurations()),
					entry1.maybeCreateRemappedConfiguration(project.getConfigurations()),
					entry1.maybeCreateTargetConfiguration(project.getConfigurations()),
					afterTasks::add
				);
			}
			
			for(Runnable runnable : afterTasks) {
				runnable.run();
			}
		}
		
		//Misc wiring-up of genSources-related tasks.
		AbstractDecompileTask genSourcesDecompileTask = (AbstractDecompileTask) project.getTasks().getByName("genSourcesDecompile");
		RemapLineNumbersTask genSourcesRemapLineNumbersTask = (RemapLineNumbersTask) project.getTasks().getByName("genSourcesRemapLineNumbers");
		Task genSourcesTask = project.getTasks().getByName("genSources");
		
		File mappedJar = mapped.getMappedJar();
		File linemappedJar = getMappedByproduct(extension, "-linemapped.jar");
		File sourcesJar = getMappedByproduct(extension, "-sources.jar");
		File linemapFile = getMappedByproduct(extension, "-sources.lmap");
		
		genSourcesDecompileTask.setInput(mappedJar);
		genSourcesDecompileTask.setOutput(sourcesJar);
		genSourcesDecompileTask.setLineMapFile(linemapFile);
		genSourcesDecompileTask.setLibraries(libs.getNonNativeLibraries());
		
		genSourcesRemapLineNumbersTask.setInput(mappedJar);
		genSourcesRemapLineNumbersTask.setLineMapFile(linemapFile);
		genSourcesRemapLineNumbersTask.setOutput(linemappedJar);
		
		Path mappedJarPath = mappedJar.toPath();
		Path linemappedJarPath = linemappedJar.toPath();
		
		//TODO(VOLDELOOM-DISASTER): is there a reason the mapped jar is moved into place in a roundabout way? Cachebusting?
		genSourcesTask.doLast((tt) -> {
			if(Files.exists(linemappedJarPath)) {
				try {
					project.delete(mappedJarPath);
					Files.copy(linemappedJarPath, mappedJarPath);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
		
		//IntelliJ run configs jank
		if(extension.autoGenIDERuns && project.getRootProject() == project) {
			SetupIntelijRunConfigs.setup(project, extension);
		}
		
		//TODO(VOLDELOOM-DISASTER): This is configurable for basically no reason lol
		//Enables the default mod remapper
		AbstractArchiveTask jarTask = (AbstractArchiveTask) project.getTasks().getByName("jar");
		if(extension.remapMod) {
			RemapJarTask remapJarTask = (RemapJarTask) project.getTasks().findByName("remapJar");
			
			if (!remapJarTask.getInput().isPresent()) {
				jarTask.setClassifier("dev");
				remapJarTask.setClassifier("");
				remapJarTask.getInput().set(jarTask.getArchivePath());
			}
			
			extension.addUnmappedMod(jarTask.getArchivePath().toPath());
			
			project.getArtifacts().add("archives", remapJarTask);
			remapJarTask.dependsOn(jarTask);
			project.getTasks().getByName("build").dependsOn(remapJarTask);
			
			//And configure source remapping, to get a -sources-dev jar or something.
			try {
				AbstractArchiveTask sourcesTask = (AbstractArchiveTask) project.getTasks().getByName("sourcesJar");
				RemapSourcesJarTask remapSourcesJarTask = (RemapSourcesJarTask) project.getTasks().findByName("remapSourcesJar");
				remapSourcesJarTask.setInput(sourcesTask.getArchivePath());
				remapSourcesJarTask.setOutput(sourcesTask.getArchivePath());
				remapSourcesJarTask.doLast(task -> project.getArtifacts().add("archives", remapSourcesJarTask.getOutput()));
				remapSourcesJarTask.dependsOn("sourcesJar");
				project.getTasks().getByName("build").dependsOn(remapSourcesJarTask);
			} catch (UnknownTaskException e) {
				// pass
			}
		} else {
			extension.addUnmappedMod(jarTask.getArchivePath().toPath());
		}
		
		//Configure a few Maven publishing settings. I (quat)'m not familiar with maven publishing so idk
		//I think this adds stuff declared in the modCompile, modImplementation etc configurations into the maven pom
		PublishingExtension mavenPublish = project.getExtensions().findByType(PublishingExtension.class);
		if(mavenPublish != null) {
			List<MavenPublication> mavenPubs = mavenPublish.getPublications().stream()
				.filter(p -> p instanceof MavenPublication)
				.map(p -> (MavenPublication) p)
				.collect(Collectors.toList());
			
			for(RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				if(!entry.hasMavenScope()) continue;
				Configuration compileModsConfig = entry.maybeCreateSourceConfiguration(project.getConfigurations());
				for(MavenPublication pub : mavenPubs) {
					pub.pom(pom -> pom.withXml(xml -> {
						Node dependencies = GroovyXmlUtil.getOrCreateNode(xml.asNode(), "dependencies");
						Set<String> foundArtifacts = new HashSet<>();
						
						GroovyXmlUtil.childrenNodesStream(dependencies).filter((n) -> "dependency".equals(n.name())).forEach((n) -> {
							Optional<Node> groupId = GroovyXmlUtil.getNode(n, "groupId");
							Optional<Node> artifactId = GroovyXmlUtil.getNode(n, "artifactId");
							
							if(groupId.isPresent() && artifactId.isPresent()) {
								foundArtifacts.add(groupId.get().text() + ":" + artifactId.get().text());
							}
						});
						
						for(Dependency dependency : compileModsConfig.getAllDependencies()) {
							if(foundArtifacts.contains(dependency.getGroup() + ":" + dependency.getName())) {
								continue;
							}
							
							Node depNode = dependencies.appendNode("dependency");
							depNode.appendNode("groupId", dependency.getGroup());
							depNode.appendNode("artifactId", dependency.getName());
							depNode.appendNode("version", dependency.getVersion());
							depNode.appendNode("scope", entry.getMavenScope());
						}
					}));
				}
			}
		}
	}
	
	//Intellij IDEA's "import gradle project" button spins up a JVM, sets the "idea.sync.active" property to "true", then runs the 'idea' task.
	//I think that's what it does. Anyway, it's a litte bit fragile, sometimes you have to dance around it.
	public static boolean ideaSync() {
		return Boolean.parseBoolean(System.getProperty("idea.sync.active", "false"));
	}
	
	public static File getMappedByproduct(LoomGradleExtension extension, String suffix) {
		String path = extension.getDependencyManager().getMappedProvider().getMappedJar().getAbsolutePath();
		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) throw new RuntimeException("Invalid mapped JAR path: " + path);
		else return new File(path.substring(0, path.length() - 4) + suffix);
	}
}

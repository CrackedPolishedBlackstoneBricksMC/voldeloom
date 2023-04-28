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
import net.fabricmc.loom.task.ConfigurationDebugTask;
import net.fabricmc.loom.task.GenSourcesTask;
import net.fabricmc.loom.task.RemappedConfigEntryFolderCopyTask;
import net.fabricmc.loom.task.ReobfJarTask;
import net.fabricmc.loom.task.RunTask;
import net.fabricmc.loom.task.ShimForgeLibrariesTask;
import net.fabricmc.loom.task.ShimResourcesTask;
import net.fabricmc.loom.task.runs.GenEclipseRunsTask;
import net.fabricmc.loom.task.runs.GenIdeaProjectTask;
import net.fabricmc.loom.task.runs.GenIdeaRunConfigsTask;
import net.fabricmc.loom.task.runs.GenVsCodeProjectTask;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.GroovyXmlUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main entrypoint for the plugin.
 */
@SuppressWarnings({
	"UnstableApiUsage", //When the IDE is working against Gradle 4, a lot of the Gradle API was incubating
	"RedundantSuppression" //And when it's woring against Gradle 7, it got stabilized
})
public class LoomGradlePlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		//Hi!
		project.getLogger().lifecycle("=====");
		project.getLogger().lifecycle("Applying Voldeloom {} to {}.", getClass().getPackage().getImplementationVersion(), project.getDisplayName());
		project.getLogger().lifecycle("Java version is '{}', Gradle version is '{}'. I hope it works :)", System.getProperty("java.version"), project.getGradle().getGradleVersion());
		
		if(project.getGradle().getGradleVersion().startsWith("8.")) {
			project.getLogger().warn("!! Gradle 8 support is very untested! I recommend 7.6 at the moment. Good luck though!");
		}
		
		project.getLogger().lifecycle("=====");
		
		//Apply a handful of bonus plugins. This acts the same as writing `apply plugin: 'java'` in the buildscript.
		project.getPlugins().apply("java");
		project.getPlugins().apply("eclipse");
		project.getPlugins().apply("idea");
		GradleSupport.init(project); //has to be done after `java` is applied i think
		
		//Create a DSL extension. This defines a `volde { }` block in the buildscript, that you may configure settings with.
		//The user's configuration is not available yet, because we're still executing the "apply plugin" line at this point.
		//Executing the rest of the buildscript will configure it, and it will be ready inside `project.afterEvaluate`
		//blocks and during task execution.
		LoomGradleExtension extensionUnconfigured = project.getExtensions().create("volde", LoomGradleExtension.class, project);
		
		//Configure a few bonus Maven repositories. This acts the same as entering them in a `repositories { }` block in the buildscript.
		project.getRepositories().maven(repo -> {
			repo.setName("Mojang");
			repo.setUrl("https://libraries.minecraft.net/");
		});
		project.getRepositories().maven(repo -> {
			repo.setName("Minecraft Forge");
			repo.setUrl("https://maven.minecraftforge.net/");
			GradleSupport.maybeSetIncludeGroup(repo, "net.minecraftforge");
			//Gradle 5 and above, by default, assumes an artifact doesn't exist if it can't find a maven_metadata.xml, to cut down on the amount
			//of spurious 404 requests. But Forge doesn't publish any maven pom files for their old versions, so this opts in to the old behavior.
			//I don't believe this breaks Gradle 4.
			repo.metadataSources(MavenArtifactRepository.MetadataSources::artifact);
		});
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
		
		//Vanilla Minecraft, straight off Mojang's server.
		project.getConfigurations().maybeCreate(Constants.MINECRAFT).setTransitive(false);
		//The dependencies for Minecraft itself, such as LWJGL.
		Configuration minecraftDependencies = project.getConfigurations().maybeCreate(Constants.MINECRAFT_DEPENDENCIES).setTransitive(false);
		
		//Forge!
		project.getConfigurations().maybeCreate(Constants.FORGE).setTransitive(false);
		//and the split configs (used for 1.2.5)
		project.getConfigurations().maybeCreate(Constants.FORGE_CLIENT).setTransitive(false);
		project.getConfigurations().maybeCreate(Constants.FORGE_SERVER).setTransitive(false);
		//The dependencies for Forge itself.
		Configuration forgeDependencies = project.getConfigurations().maybeCreate(Constants.FORGE_DEPENDENCIES).setTransitive(false);
		
		//Mappings. This is the raw MCP artifact, and the processed mapping jar is located elsewhere (using provider system).
		project.getConfigurations().maybeCreate(Constants.MAPPINGS).setTransitive(true);
		//compileOrImplementation.extendsFrom(mappings); //Not needed I don't think
		
		//Custom access transformers.
		project.getConfigurations().maybeCreate(Constants.CUSTOM_ACCESS_TRANSFORMERS).setTransitive(false);
		
		//Vanilla Minecraft + Forge, remapped to the mappings chosen by the modder.
		Configuration minecraftNamed = project.getConfigurations().maybeCreate(Constants.MINECRAFT_NAMED).setTransitive(false);
		compileOrImplementation.extendsFrom(minecraftNamed);
		minecraftNamed.extendsFrom(minecraftDependencies);
		minecraftNamed.extendsFrom(forgeDependencies);
		
		//Mod dependency types!
		//First, I'd like to know about every mod participating in the remap process
		Configuration everyUnmappedMod = project.getConfigurations().maybeCreate(Constants.EVERY_UNMAPPED_MOD).setTransitive(false);
		extensionUnconfigured.remappedConfigurationEntries.whenObjectAdded(entry -> everyUnmappedMod.extendsFrom(entry.inputConfig));
		
		//Then preconfigure a few. TODO figure out how `api` works lol
		//First do some Gradle back-compat stuff. We should create configurations with the same naming conventions as the ones in the current Gradle version.
		String modImplementationName, modRuntimeOnlyName;
		Map<String, String> corrections = new HashMap<>();
		if(GradleSupport.compileOrImplementation.equals("compile")) {
			//Gradle 6-
			modImplementationName = "modCompile";
			corrections.put("modImplementation", "modCompile");
			corrections.put("coremodImplementation", "coremodCompile");
		} else { 
			//Gradle 7
			modImplementationName = "modImplementation";
			corrections.put("modCompile", "modImplementation");
			corrections.put("coremodCompile", "coremodImplementation");
		}
		
		if(GradleSupport.runtimeOrRuntimeOnly.equals("runtime")) {
			//Gradle 6-
			modRuntimeOnlyName = "modRuntime";
			corrections.put("modRuntimeOnly", "modRuntime");
			corrections.put("coremodRuntimeOnly", "coremodRuntime");
		} else {
			//Gradle 7
			modRuntimeOnlyName = "modRuntimeOnly";
			corrections.put("modRuntime", "modRuntimeOnly");
			corrections.put("coremodRuntime", "coremodRuntimeOnly");
		}
		
		//Rules (on NamedDomainObjectCollections, such as the ConfigurtionContainer) get called when someone attempts to look up a name that doesn't exist.
		//I use this to print warnings for common incorrect access patterns. Throwing an exception is also possible, and will crash with a nicer error,
		//but I figure those might get in the way if someone wants to do Crimes:tm:...
		project.getConfigurations().addRule("Detector for incorrect Voldeloom configuration names", wrongName -> {
			if(!extensionUnconfigured.warnOnProbablyWrongConfigurationNames) return; //Your funeral.
			
			String rightName = corrections.get(wrongName);
			if(rightName != null) {
				project.getLogger().error("!! [Voldeloom] The '" + wrongName + "' configuration isn't created when running on Gradle " + project.getGradle().getGradleVersion() + ".");
				project.getLogger().error("!! Please use the '" + rightName + "' configuration on this version instead.");
			}
			
			if("coremodCompileOnly".equals(wrongName)) {
				project.getLogger().error("!! [Voldeloom] No need to use 'coremodCompileOnly', simply use regular 'modCompileOnly'.");
				project.getLogger().error("!! Coremod special-casing is only relevant to getting mods onto the runtime classpath.");
			}
		});
		
		//Ok now actually add them.
		extensionUnconfigured.remappedConfigurationEntries.create(modImplementationName, mod -> {
			mod.mavenScope("compile");
			project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(mod.outputConfig);
		});
		extensionUnconfigured.remappedConfigurationEntries.create("modCompileOnly", mod -> {
			mod.mavenScope("compile");
			project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(mod.outputConfig);
		});
		extensionUnconfigured.remappedConfigurationEntries.create(modRuntimeOnlyName, mod -> {
			mod.mavenScope("runtime");
			project.getConfigurations().getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME).extendsFrom(mod.outputConfig);
		});
		extensionUnconfigured.remappedConfigurationEntries.create("modLocalRuntime", mod -> {
			//No maven scope
			project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(mod.outputConfig);
		});
		//and some for coremods
		extensionUnconfigured.remappedConfigurationEntries.create("core" + modImplementationName, mod -> {
			mod.mavenScope("compile").copyToFolder("coremods");
			project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(mod.outputConfig);
			//doesn't extend from compile/implementation since copyToFolder will take care of the runtime-classpath aspect
		});
		extensionUnconfigured.remappedConfigurationEntries.create("core" + modRuntimeOnlyName, mod -> {
			mod.mavenScope("runtime").copyToFolder("coremods");
			//doesn't extend from runtimeOnly since copyToFolder will take care of it
		});
		extensionUnconfigured.remappedConfigurationEntries.create("coremodLocalRuntime", mod -> {
			//No maven scope
			mod.copyToFolder("coremods");
			//doesn't extend from runtimeClasspath since copyToFolder will take care of it
		});
		
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
		//Note that `register` doesn't add the task right away, but Gradle will reflectively create it when someone asks for it,
		//and (if a closure is specified) call the closure to configure the task. Optimization thing, I guess.
		TaskContainer tasks = project.getTasks();
		
		//Remapping artifacts:
		tasks.register("remapJarForRelease", ReobfJarTask.class, t -> t.dependsOn(tasks.named("jar")));
		tasks.named("build").configure(t -> t.dependsOn(tasks.named("remapJarForRelease")));
		
		//IDE integration:
		tasks.register("genSources", GenSourcesTask.class);
		tasks.register("genEclipseRuns", GenEclipseRunsTask.class);
		tasks.register("genIdeaRuns", GenIdeaRunConfigsTask.class);
		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class);
		tasks.register("vscode", GenVsCodeProjectTask.class);
		
		//Support for run configs:
		tasks.register("shimForgeLibraries", ShimForgeLibrariesTask.class);
		tasks.register("remappedConfigEntryFolderCopy", RemappedConfigEntryFolderCopyTask.class);
		tasks.register("shimResources", ShimResourcesTask.class);
		
		//Run configs:
		extensionUnconfigured.runConfigs.whenObjectAdded(cfg -> {
			TaskProvider<RunTask> runTask = tasks.register("run" + cfg.getBaseName().substring(0, 1).toUpperCase(Locale.ROOT) + cfg.getBaseName().substring(1), RunTask.class, cfg);
			runTask.configure(t -> {
				t.dependsOn("assemble", "shimForgeLibraries", "remappedConfigEntryFolderCopy");
				if(cfg.getEnvironment().equals("client")) t.dependsOn("shimResources");
			});
		});
		extensionUnconfigured.runConfigs.add(RunConfig.defaultClientRunConfig(project));
		extensionUnconfigured.runConfigs.add(RunConfig.defaultServerRunConfig(project));
		
		//Debug Funny
		tasks.register("printConfigurationsPlease", ConfigurationDebugTask.class);
		
		//TODO is it safe to configure this now? I ask because upstream did it in afterEvaluate
		//TODO 2 i dont think its actually needed
		//tasks.named("idea").configure(t -> t.finalizedBy(tasks.named("genIdeaWorkspace"), tasks.named("genIdeaRuns")));
		//tasks.named("eclipse").configure(t -> t.finalizedBy(tasks.named("genEclipseRuns")));

		//So. build.gradle files *look* declarative, but recall that they are imperative programs, executed top-to-bottom.
		//All of the above happens immediately upon encountering the `apply plugin` line. The rest of the script hasn't executed yet.
		//But what if we want to make choices based on the things the user configured in LoomGradleExtensions?
		//With an afterEvaluate block, we can ask to have control passed back to us after the build.gradle file is finished executing.
		project.afterEvaluate(this::afterEvaluate);
	}
	
	private void afterEvaluate(Project project) {
		//"For some crazy reason afterEvaluate is still invoked when the configuration fails" - modmuss in loom 1.
		//He's right, this is really weird behavior. Exit as soon as possible before we cause more failures
		if(project.getState().getFailure() != null) return;
		
		project.getLogger().lifecycle(":Beginning Voldeloom afterEvaluate sauce");
		
		//The extension's been configured, grab it.
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		if(extension.offline) project.getLogger().warn("!! We're in offline mode - downloads will abort");
		if(extension.refreshDependencies) project.getLogger().warn("!! We're in refresh-dependencies mode - intermediate products will be recomputed");
		
		//Pre-setup actions requested by the user
		extension.beforeMinecraftSetupActions.forEach(action -> action.execute(project));
		
		//Scaffold the "provider" system. This is a loose term for "the things that have to run now, after the user configured
		//their settings in LoomGradleExtension, but before we're not allowed to mutate the project dependencies anymore".
		extension.getProviderGraph().trySetup();
		
		//Configure the for-release remapper
		AbstractArchiveTask jarTask = (AbstractArchiveTask) project.getTasks().getByName("jar");
		ReobfJarTask reobfJarTask = (ReobfJarTask) project.getTasks().findByName("remapJarForRelease");
		Check.notNull(reobfJarTask, "reobfJarTask");
		
		if(!reobfJarTask.getInput().isPresent()) {
			//unless you configured it otherwise, move the regular jar to having the -dev classifier,
			//and the remapped-for-release one to have no classifier
			GradleSupport.setClassifier(jarTask, "dev");
			GradleSupport.setClassifier(reobfJarTask, "");
			reobfJarTask.getInput().set(GradleSupport.getArchiveFile(jarTask));
		}
		project.getArtifacts().add("archives", reobfJarTask);
		
		//add to classpath for runClient (TODO do this a different way? configurations? artifacts?)
		extension.addUnmappedMod(GradleSupport.getArchiveFile(jarTask).toPath());
		
		//Configure a few Maven publishing settings. I (quat)'m not familiar with maven publishing so idk
		//I think this adds stuff declared in the modCompile, modImplementation etc configurations into the maven pom
		PublishingExtension mavenPublish = project.getExtensions().findByType(PublishingExtension.class);
		if(mavenPublish != null) {
			List<MavenPublication> mavenPubs = mavenPublish.getPublications().stream()
				.filter(p -> p instanceof MavenPublication)
				.map(p -> (MavenPublication) p)
				.collect(Collectors.toList());
			
			for(RemappedConfigurationEntry entry : extension.remappedConfigurationEntries) {
				String mavenScope = entry.getMavenScope();
				if(mavenScope == null || mavenScope.isEmpty()) continue;
				
				Configuration compileModsConfig = entry.getInputConfig();
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
							depNode.appendNode("scope", mavenScope);
						}
					}));
				}
			}
		}
		
		//User's post-setup actions
		if(!extension.afterMinecraftSetupActions.isEmpty()) {
			project.getLogger().info(":running " + extension.afterMinecraftSetupActions.size() + " afterMinecraftSetup action(s)");
			extension.afterMinecraftSetupActions.forEach(action -> action.execute(project));
		}
	}
	
	/**
	 * Okay, this is a random utility method. Sorry.
	 * <p>
	 * Deletes a file or directory using Gradle's machinery, announcing each file to be deleted.<br>
	 * Gradle's machinery accepts a million different types of object here, but the most important ones are {@code File} and {@code Path}.<br>
	 * Deleting a directory will recursively delete all files inside it, too.
	 */
	public static void delete(Project project, Object... things) {
		project.delete(deleteSpec -> {
			deleteSpec.setFollowSymlinks(false);
			for(Object thing : things) {
				project.getLogger().lifecycle("Deleting " + thing);
			}
			
			deleteSpec.delete(things);
		});
	}
	
	/**
	 * Also a random utility method. Replaces a file extension.
	 * @param path The path to replace the extension of.
	 * @param suffix The new extension. If this doesn't begin with a dot, it'll look like a suffix was appended to the filename, too.
	 */
	public static Path replaceExtension(Path path, String suffix) {
		String originalFilename = path.getFileName().toString();
		int dotIndex = originalFilename.lastIndexOf('.');
		if(dotIndex != -1 /* no file extension */ && dotIndex != 0 /* the dot is used to mark a hidden file */) {
			originalFilename = originalFilename.substring(0, dotIndex);
		}
		return path.resolveSibling(originalFilename + suffix);
	}
}

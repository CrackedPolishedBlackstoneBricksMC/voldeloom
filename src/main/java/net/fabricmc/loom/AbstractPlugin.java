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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import groovy.util.Node;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import net.fabricmc.loom.forge.provider.ForgeProvider;
import net.fabricmc.loom.providers.LaunchProvider;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.GroovyXmlUtil;
import net.fabricmc.loom.util.LoomDependencyManager;
import net.fabricmc.loom.util.NestedJars;
import net.fabricmc.loom.util.RemappedConfigurationEntry;
import net.fabricmc.loom.util.SetupIntelijRunConfigs;

public class AbstractPlugin implements Plugin<Project> {
	protected Project project;

	//(WEIRD VOLDELOOM STUFF)
	public static String compileOrImplementation;
	public static String runtimeOrRuntimeOnly;

	public static boolean isRootProject(Project project) {
		return project.getRootProject() == project;
	}

	private void extendsFrom(String a, String b) {
		project.getConfigurations().getByName(a).extendsFrom(project.getConfigurations().getByName(b));
	}

	@Override
	public void apply(Project target) {
		this.project = target;

		project.getLogger().lifecycle("Voldeloom: " + AbstractPlugin.class.getPackage().getImplementationVersion());

		// Apply default plugins
		project.apply(ImmutableMap.of("plugin", "java"));
		project.apply(ImmutableMap.of("plugin", "eclipse"));
		project.apply(ImmutableMap.of("plugin", "idea"));

		//(WEIRD VOLDELOOM STUFF)
		//Gradle 7 decided to rename "compile" to "implementation" and "runtime" to "runtimeOnly"
		//They're basically the same thing so we can just swap out the names as-appropriate
		try {
			project.getConfigurations().getByName("compile");
			compileOrImplementation = "compile";
			runtimeOrRuntimeOnly = "runtime";
		} catch (UnknownDomainObjectException e) {
			compileOrImplementation = "implementation";
			runtimeOrRuntimeOnly = "runtimeOnly";
		}
		//(/WEIRD VOLDELOOM STUFF)

		project.getExtensions().create("minecraft", LoomGradleExtension.class, project);

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		// Force add Mojang repository
		addMavenRepo(target, "Mojang", "https://libraries.minecraft.net/");

		Configuration modCompileClasspathConfig = project.getConfigurations().maybeCreate(Constants.MOD_COMPILE_CLASSPATH);
		modCompileClasspathConfig.setTransitive(true);
		Configuration modCompileClasspathMappedConfig = project.getConfigurations().maybeCreate(Constants.MOD_COMPILE_CLASSPATH_MAPPED);
		modCompileClasspathMappedConfig.setTransitive(false);

		Configuration minecraftNamedConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_NAMED);
		minecraftNamedConfig.setTransitive(false); // The launchers do not recurse dependencies
		Configuration minecraftDependenciesConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_DEPENDENCIES);
		minecraftDependenciesConfig.setTransitive(false);
		Configuration minecraftConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT);
		minecraftConfig.setTransitive(false);

		Configuration includeConfig = project.getConfigurations().maybeCreate(Constants.INCLUDE);
		includeConfig.setTransitive(false); // Dont get transitive deps

		project.getConfigurations().maybeCreate(Constants.MAPPINGS);
		project.getConfigurations().maybeCreate(Constants.MAPPINGS_FINAL);
		project.getConfigurations().maybeCreate(Constants.FORGE).setTransitive(false); // disable transitive to be safe -- forge will load its deps at runtime.

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			Configuration compileModsConfig = project.getConfigurations().maybeCreate(entry.getSourceConfiguration());
			compileModsConfig.setTransitive(true);
			Configuration compileModsMappedConfig = project.getConfigurations().maybeCreate(entry.getRemappedConfiguration());
			compileModsMappedConfig.setTransitive(false); // Don't get transitive deps of already remapped mods

			extendsFrom(entry.getTargetConfiguration(project.getConfigurations()), entry.getRemappedConfiguration());

			if (entry.isOnModCompileClasspath()) {
				extendsFrom(Constants.MOD_COMPILE_CLASSPATH, entry.getSourceConfiguration());
				extendsFrom(Constants.MOD_COMPILE_CLASSPATH_MAPPED, entry.getRemappedConfiguration());
			}
		}

		extendsFrom(compileOrImplementation, Constants.MINECRAFT_NAMED);

		if (!extension.ideSync()) {
			extendsFrom("annotationProcessor", Constants.MINECRAFT_NAMED);
			extendsFrom("annotationProcessor", Constants.MOD_COMPILE_CLASSPATH_MAPPED);
		}

		extendsFrom(Constants.MINECRAFT_NAMED, Constants.MINECRAFT_DEPENDENCIES);

		extendsFrom(compileOrImplementation, Constants.MAPPINGS_FINAL);

		if (!extension.ideSync()) {
			extendsFrom("annotationProcessor", Constants.MAPPINGS_FINAL);
		}

		configureIDEs();
		configureCompile();
		configureScala();

		Map<Project, Set<Task>> taskMap = project.getAllTasks(true);

		for (Map.Entry<Project, Set<Task>> entry : taskMap.entrySet()) {
			Project project = entry.getKey();
			Set<Task> taskSet = entry.getValue();

			for (Task task : taskSet) {
				if (task instanceof JavaCompile && !(task.getName().contains("Test")) && !(task.getName().contains("test"))) {
					JavaCompile javaCompileTask = (JavaCompile) task;
					javaCompileTask.doFirst(task1 -> {
						project.getLogger().lifecycle(":setting java compiler args");

						try {
							javaCompileTask.getOptions().getCompilerArgs().add("-AinMapFileNamedIntermediary=" + extension.getMappingsProvider().tinyMappings.getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutMapFileNamedIntermediary=" + extension.getMappingsProvider().mappingsMixinExport.getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutRefMapFile=" + new File(javaCompileTask.getDestinationDir(), extension.getRefmapName()).getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AdefaultObfuscationEnv=named:intermediary");
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}
			}
		}

		configureMaven();
	}

	public Project getProject() {
		return project;
	}

	protected void configureScala() {
		project.afterEvaluate(proj -> {
			if (project.getPluginManager().hasPlugin("scala")) {
				ScalaCompile task = (ScalaCompile) project.getTasks().getByName("compileScala");
				LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
				project.getLogger().warn(":configuring scala compilation processing");

				try {
					task.getOptions().getCompilerArgs().add("-AinMapFileNamedIntermediary=" + extension.getMappingsProvider().tinyMappings.getCanonicalPath());
					task.getOptions().getCompilerArgs().add("-AoutMapFileNamedIntermediary=" + extension.getMappingsProvider().mappingsMixinExport.getCanonicalPath());
					task.getOptions().getCompilerArgs().add("-AoutRefMapFile=" + new File(task.getDestinationDir(), extension.getRefmapName()).getCanonicalPath());
					task.getOptions().getCompilerArgs().add("-AdefaultObfuscationEnv=named:intermediary");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Permit to add a Maven repository to a target project.
	 *
	 * @param target The garget project
	 * @param name   The name of the repository
	 * @param url    The URL of the repository
	 * @return An object containing the name and the URL of the repository that can be modified later
	 */
	public MavenArtifactRepository addMavenRepo(Project target, final String name, final String url) {
		return target.getRepositories().maven(repo -> {
			repo.setName(name);
			repo.setUrl(url);
		});
	}

	/**
	 * Add Minecraft dependencies to IDE dependencies.
	 */
	protected void configureIDEs() {
		// IDEA
		IdeaModel ideaModel = (IdeaModel) project.getExtensions().getByName("idea");

		ideaModel.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
		ideaModel.getModule().setDownloadJavadoc(true);
		ideaModel.getModule().setDownloadSources(true);
		ideaModel.getModule().setInheritOutputDirs(true);
	}

	/**
	 * Add Minecraft dependencies to compile time.
	 */
	protected void configureCompile() {
		JavaPluginConvention javaModule = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

		SourceSet main = javaModule.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		Javadoc javadoc = (Javadoc) project.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
		javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

		if (!project.getExtensions().getByType(LoomGradleExtension.class).ideSync()) {
			// Add Mixin dependencies
			project.getDependencies().add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME, "net.fabricmc:fabric-mixin-compile-extensions:" + Constants.MIXIN_COMPILE_EXTENSIONS_VERSION);
		}

		project.afterEvaluate(project1 -> {
			LoomGradleExtension extension = project1.getExtensions().getByType(LoomGradleExtension.class);

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getRootProjectBuildCache());
				flatDirectoryArtifactRepository.setName("UserLocalCacheFiles");
			});

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getRemappedModCache());
				flatDirectoryArtifactRepository.setName("UserLocalRemappedMods");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Fabric");
				mavenArtifactRepository.setUrl("https://maven.fabricmc.net/");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Mojang");
				mavenArtifactRepository.setUrl("https://libraries.minecraft.net/");
			});

			project1.getRepositories().mavenCentral();
			project1.getRepositories().jcenter();

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);

			dependencyManager.addProvider(new ForgeProvider(getProject()));
			dependencyManager.addProvider(new MinecraftProvider(getProject()));
			dependencyManager.addProvider(new MappingsProvider(getProject()));
			dependencyManager.addProvider(new LaunchProvider(getProject()));

			dependencyManager.handleDependencies(project1);

			project1.getTasks().getByName("idea").finalizedBy(project1.getTasks().getByName("genIdeaWorkspace"));
			project1.getTasks().getByName("eclipse").finalizedBy(project1.getTasks().getByName("genEclipseRuns"));

			if (extension.autoGenIDERuns && isRootProject(project1)) {
				SetupIntelijRunConfigs.setup(project1);
			}

			// Enables the default mod remapper
			if (extension.remapMod) {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");
				RemapJarTask remapJarTask = (RemapJarTask) project1.getTasks().findByName("remapJar");

				assert remapJarTask != null;

				if (!remapJarTask.getInput().isPresent()) {
					jarTask.setClassifier("dev");
					remapJarTask.setClassifier("");
					remapJarTask.getInput().set(jarTask.getArchivePath());
				}

				extension.addUnmappedMod(jarTask.getArchivePath().toPath());
				remapJarTask.getAddNestedDependencies().set(true);

				project1.getArtifacts().add("archives", remapJarTask);
				remapJarTask.dependsOn(jarTask);
				project1.getTasks().getByName("build").dependsOn(remapJarTask);

				Map<Project, Set<Task>> taskMap = project.getAllTasks(true);

				for (Map.Entry<Project, Set<Task>> entry : taskMap.entrySet()) {
					Set<Task> taskSet = entry.getValue();

					for (Task task : taskSet) {
						if (task instanceof RemapJarTask && ((RemapJarTask) task).getAddNestedDependencies().getOrElse(false)) {
							//Run all the sub project remap jars tasks before the root projects jar, this is to allow us to include projects
							NestedJars.getRequiredTasks(project1).forEach(task::dependsOn);
						}
					}
				}

				try {
					AbstractArchiveTask sourcesTask = (AbstractArchiveTask) project1.getTasks().getByName("sourcesJar");

					RemapSourcesJarTask remapSourcesJarTask = (RemapSourcesJarTask) project1.getTasks().findByName("remapSourcesJar");
					remapSourcesJarTask.setInput(sourcesTask.getArchivePath());
					remapSourcesJarTask.setOutput(sourcesTask.getArchivePath());
					remapSourcesJarTask.doLast(task -> project1.getArtifacts().add("archives", remapSourcesJarTask.getOutput()));
					remapSourcesJarTask.dependsOn(project1.getTasks().getByName("sourcesJar"));
					project1.getTasks().getByName("build").dependsOn(remapSourcesJarTask);
				} catch (UnknownTaskException e) {
					// pass
				}
			} else {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");
				extension.addUnmappedMod(jarTask.getArchivePath().toPath());
			}
		});
	}

	protected void configureMaven() {
		project.afterEvaluate((p) -> {
			for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				if (!entry.hasMavenScope()) {
					continue;
				}

				Configuration compileModsConfig = p.getConfigurations().getByName(entry.getSourceConfiguration());

				// add modsCompile to maven-publish
				PublishingExtension mavenPublish = p.getExtensions().findByType(PublishingExtension.class);

				if (mavenPublish != null) {
					mavenPublish.publications((publications) -> {
						for (Publication publication : publications) {
							if (publication instanceof MavenPublication) {
								((MavenPublication) publication).pom((pom) -> {
									pom.withXml((xml) -> {
										Node dependencies = GroovyXmlUtil.getOrCreateNode(xml.asNode(), "dependencies");
										Set<String> foundArtifacts = new HashSet<>();

										GroovyXmlUtil.childrenNodesStream(dependencies).filter((n) -> "dependency".equals(n.name())).forEach((n) -> {
											Optional<Node> groupId = GroovyXmlUtil.getNode(n, "groupId");
											Optional<Node> artifactId = GroovyXmlUtil.getNode(n, "artifactId");

											if (groupId.isPresent() && artifactId.isPresent()) {
												foundArtifacts.add(groupId.get().text() + ":" + artifactId.get().text());
											}
										});

										for (Dependency dependency : compileModsConfig.getAllDependencies()) {
											if (foundArtifacts.contains(dependency.getGroup() + ":" + dependency.getName())) {
												continue;
											}

											Node depNode = dependencies.appendNode("dependency");
											depNode.appendNode("groupId", dependency.getGroup());
											depNode.appendNode("artifactId", dependency.getName());
											depNode.appendNode("version", dependency.getVersion());
											depNode.appendNode("scope", entry.getMavenScope());
										}
									});
								});
							}
						}
					});
				}
			}
		});
	}
}

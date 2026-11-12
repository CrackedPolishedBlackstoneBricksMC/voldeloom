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

package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RunConfig;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Archetypical Gradle task that starts Minecraft. Make sure to pass an argument when you create it with TaskContainer#register.
 */
public class RunTask extends JavaExec implements LoomTaskExt {
	@Inject
	public RunTask(RunConfig config) throws Exception {
		setGroup(Constants.TASK_GROUP_RUNNING);
		
		LoomGradleExtension extension = getLoomGradleExtension();
		config = config.cook(extension);
		setDescription("Starts Minecraft using the '" + config.getName() + "' run configuration.");
		
		//Toolchain
		if(config.getAutoConfigureToolchains()) {
			boolean couldSetToolchain = GradleSupport.trySetJavaToolchain(this, config.getRunToolchainVersion(), config.getRunToolchainVendor());
			if(!couldSetToolchain) {
				getLogger().warn("[Voldeloom] Could not provision a Java 8 toolchain for task '{}'.", getName());
				getLogger().warn("According to GradleSupport.trySetJavaToolchain, this version of Gradle ({}) doesn't support toolchains.", getProject().getGradle().getGradleVersion());
				getLogger().warn("Minecraft will run in a forked copy of the current JVM ({}).", JavaVersion.current());
				if(JavaVersion.current().compareTo(JavaVersion.VERSION_1_8) > 0) {
					getLogger().warn("Good luck with that!");
				}
			}
		}

		//Classpath
		List<String> classpath = new ArrayList<>();

		//TODO, this should also go in RunConfig#cook, one purpose of this is picking up on mod dependencies 
		for(File file : getProject().getConfigurations().getByName("runtimeClasspath").getFiles()) {
			classpath.add(file.getAbsolutePath());
		}
		
		//The mod under development (the output of the `jar` task). Don't check that it exists here: this constructor runs
		//when the task is realized, which can be before `jar` has run on a clean build, and the mod would silently go missing.
		//For 1.2.5-, it is loaded from the mods folder instead (see installDevModsIntoModsFolder).
		if(!extension.forgeCapabilities.loadDevModsFromModsFolder.get()) {
			for (Path file : extension.getUnmappedMods()) {
				classpath.add(file.toFile().getAbsolutePath());
			}
		}
		classpath(classpath);
		
		//Arguments
		jvmArgs(config.getVmArgs());
		args(config.getProgramArgs());

		//Main class
		GradleSupport.setMainClass(this, config.getMainClass());

		//Pwd
		Path runDir = config.resolveRunDir().toAbsolutePath();
		String environment = config.getEnvironment();
		//Only the 1.2.5- *client* derives its game directory from APPDATA/user.home; the dedicated server just uses the working directory.
		Path realRunDir = "client".equals(environment)
			? getLoomGradleExtension().forgeCapabilities.minecraftRealPath.get().apply(runDir)
			: runDir;
		Files.createDirectories(realRunDir);
		setWorkingDir(realRunDir);

		//TODO: horrible kludge for 1.2.5 on Windows lol (it reads APPDATA environment variable)
		environment("APPDATA", runDir.toString());

		//Stdin
		setStandardInput(System.in);

		this.environment = environment;
		this.realRunDir = realRunDir;
	}
	
	private final String environment;
	private final Path realRunDir;
	
	//Not a doFirst in the constructor: Gradle prepends the @TaskAction after construction, so that would run after the game exits.
	@Override
	@TaskAction
	public void exec() {
		checkSplitJarSide(environment);
		installDevModsIntoModsFolder(realRunDir);
		super.exec();
	}

	/**
	 * Split-jar Minecraft (1.2.5-) only sets up one side per project; running the other side would start the wrong jar.
	 */
	private void checkSplitJarSide(String environment) {
		String setupSide = getLoomGradleExtension().getProviderGraph().setupSide;
		if(setupSide == null || setupSide.equals("joined") || setupSide.equals(environment)) return;

		throw new IllegalStateException("[Voldeloom] Task '" + getName() + "' starts the " + environment + ", but this project is set up for the " +
			setupSide + " (volde.splitJarSide = \"" + setupSide + "\"). Split-jar Minecraft can only develop one side per project.");
	}

	/**
	 * Forge 1.2.5-: copy the mod under development into the run directory's mods folder, where the loader reads it from.
	 * (It is kept off the run classpath in the constructor, so it can't be discovered twice.)
	 */
	private void installDevModsIntoModsFolder(Path realRunDir) {
		LoomGradleExtension extension = getLoomGradleExtension();
		if(!extension.forgeCapabilities.loadDevModsFromModsFolder.get()) return;

		Path modsDir = realRunDir.resolve("mods");
		try {
			Files.createDirectories(modsDir);

			//Clear out copies from earlier runs (the jar name changes with the project version)
			try(DirectoryStream<Path> old = Files.newDirectoryStream(modsDir, DEV_MOD_PREFIX + "*")) {
				for(Path p : old) Files.deleteIfExists(p);
			}

			for(Path mod : extension.getUnmappedMods()) {
				if(!Files.isRegularFile(mod)) {
					getLogger().warn("[Voldeloom] Mod under development {} doesn't exist; it won't be loaded. Did the 'jar' task run?", mod);
					continue;
				}
				Path dest = modsDir.resolve(DEV_MOD_PREFIX + mod.getFileName());
				Files.copy(mod, dest, StandardCopyOption.REPLACE_EXISTING);
				getLogger().lifecycle("[Voldeloom] Forge 1.2.5-: loading the mod under development from {}", dest);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("[Voldeloom] Couldn't copy the mod under development into " + modsDir, e);
		}
	}

	private static final String DEV_MOD_PREFIX = "voldeloom-dev-";
}

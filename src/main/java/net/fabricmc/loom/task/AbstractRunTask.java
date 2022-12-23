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

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.RunConfig;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public abstract class AbstractRunTask extends JavaExec implements LoomTaskExt {
	public AbstractRunTask(BiFunction<Project, LoomGradleExtension, RunConfig> configProvider) {
		setGroup("minecraftMapped");
		
		//TODO, does this happen too early? Original Loom did not have it this early
		// I moved it up while debugging something nasty but i think the problem was not related to the execution time of this stuff.
		
		LoomGradleExtension extension = getLoomGradleExtension();
		RunConfig config = configProvider.apply(getProject(), extension);
		
		MinecraftVersionInfo minecraftVersionInfo = extension.getMinecraftProvider().getVersionInfo();
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		List<String> libs = new ArrayList<>();

		for(File file : getProject().getConfigurations().getByName("runtimeClasspath").getFiles()) {
			//TODO(VOLDELOOM-DISASTER) this is a big hack loooooll, it conflicts with a forge library
			if(file.toString().endsWith("guava-28.0-jre.jar")) {
				continue;
			}

			libs.add(file.getAbsolutePath());
		}

		for (Path file : extension.getUnmappedMods()) {
			if (Files.isRegularFile(file)) {
				libs.add(file.toFile().getAbsolutePath());
			}
		}

		classpath(libs);
		List<String> argsSplit = new ArrayList<>();
		String[] args = config.programArgs.split(" ");
		int partPos = -1;

		for (int i = 0; i < args.length; i++) {
			if (partPos < 0) {
				if (args[i].startsWith("\"")) {
					if (args[i].endsWith("\"")) {
						argsSplit.add(args[i].substring(1, args[i].length() - 1));
					} else {
						partPos = i;
					}
				} else {
					argsSplit.add(args[i]);
				}
			} else if (args[i].endsWith("\"")) {
				StringBuilder builder = new StringBuilder(args[partPos].substring(1));

				for (int j = partPos + 1; j < i; j++) {
					builder.append(" ").append(args[j]);
				}

				builder.append(" ").append(args[i], 0, args[i].length() - 1);
				argsSplit.add(builder.toString());
				partPos = -1;
			}
		}

		args(argsSplit);
		//jvmArgs(config.vmArgs); //uncommenting this breaks the loading process i dunno why
		systemProperties(config.systemProperties);

		getMainClass().set(config.mainClass); //todo gradle 4? this is relatively new
		//setMain(config.mainClass);

		File workingDir = new File(getProject().getRootDir(), extension.runDir);
		if(!workingDir.exists()) {
			workingDir.mkdirs();
		}
		setWorkingDir(workingDir);
	}
}

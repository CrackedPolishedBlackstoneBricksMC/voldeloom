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
import net.fabricmc.loom.LoomGradlePlugin;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SetupIntelijRunConfigs {
	public static void setup(Project project, LoomGradleExtension extension) {
		File projectDir = project.file(".idea");

		if (!projectDir.exists()) {
			return;
		}

		try {
			generate(project, extension);
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate run configs", e);
		}

		File runDir = new File(project.getRootDir(), extension.runDir);

		if (!runDir.exists()) {
			runDir.mkdirs();
		}
	}

	private static void generate(Project project, LoomGradleExtension extension) throws IOException {
		//Ensures the assets are downloaded when idea is syncing a project
		if (LoomGradlePlugin.ideaSync()) {
			//TODO: old code ensures assets, libs, and natives were downloaded, but when they are converted to tasks,
			// I can instead make whatever task generates the run config depend on that task
		}

		File projectDir = project.file(".idea");
		File runConfigsDir = new File(projectDir, "runConfigurations");
		File clientRunConfigs = new File(runConfigsDir, "Minecraft_Client.xml");
		File serverRunConfigs = new File(runConfigsDir, "Minecraft_Server.xml");

		if (!runConfigsDir.exists()) {
			runConfigsDir.mkdirs();
		}

		String clientRunConfig = RunConfig.clientRunConfig(project, extension).fromDummy("idea_run_config_template.xml");
		String serverRunConfig = RunConfig.serverRunConfig(project, extension).fromDummy("idea_run_config_template.xml");

		if (!clientRunConfigs.exists() || RunConfig.needsUpgrade(clientRunConfigs)) {
			FileUtils.writeStringToFile(clientRunConfigs, clientRunConfig, StandardCharsets.UTF_8);
		}

		if (!serverRunConfigs.exists() || RunConfig.needsUpgrade(serverRunConfigs)) {
			FileUtils.writeStringToFile(serverRunConfigs, serverRunConfig, StandardCharsets.UTF_8);
		}
	}
}

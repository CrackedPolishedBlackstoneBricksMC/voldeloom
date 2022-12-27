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
import net.fabricmc.loom.providers.RunConfigProvider;
import net.fabricmc.loom.util.LoomTaskExt;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GenEclipseRunsTask extends DefaultTask implements LoomTaskExt {
	public GenEclipseRunsTask() {
		setGroup("ide");
	}
	
	//TODO(VOLDELOOM-DISASTER): Untested, I don't have Eclipse
	@TaskAction
	public void genRuns() throws IOException {
		LoomGradleExtension extension = getLoomGradleExtension();
		RunConfigProvider runs = extension.getDependencyManager().getRunConfigProvider();
		
		File clientRunConfigs = new File(getProject().getRootDir(), getProject().getName() + "_client.launch");
		File serverRunConfigs = new File(getProject().getRootDir(), getProject().getName() + "_server.launch");

		String clientRunConfig = runs.getClient().configureTemplate("eclipse_run_config_template.xml");
		String serverRunConfig = runs.getServer().configureTemplate("eclipse_run_config_template.xml");
		
		if (!clientRunConfigs.exists()) {
			FileUtils.writeStringToFile(clientRunConfigs, clientRunConfig, StandardCharsets.UTF_8);
		}
		
		if (!serverRunConfigs.exists()) {
			FileUtils.writeStringToFile(serverRunConfigs, serverRunConfig, StandardCharsets.UTF_8);
		}

		Path runDir = getProject().getRootDir().toPath().resolve(extension.runDir);
		Files.createDirectories(runDir);
	}
}

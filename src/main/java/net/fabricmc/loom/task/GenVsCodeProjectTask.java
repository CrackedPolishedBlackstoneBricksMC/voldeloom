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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.util.RunConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

//Recommended vscode plugins:
// https://marketplace.visualstudio.com/items?itemName=redhat.java
// https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-debug
// https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack

//TODO(VOLDELOOM-DISASTER): Untested cause i don't use vscode for Java
public class GenVsCodeProjectTask extends DefaultTask implements LoomTaskExt {
	public GenVsCodeProjectTask() {
		setGroup("ide");
	}
	
	@TaskAction
	public void genRuns() throws Exception {
		Project project = getProject();
		LoomGradleExtension extension = getLoomGradleExtension();
		
		Path vscodeProjectDir = project.file(".vscode").toPath();
		Files.createDirectories(vscodeProjectDir);

		Path launchJson = vscodeProjectDir.resolve("launch.json");
		Files.deleteIfExists(launchJson);

		VsCodeLaunch launch = new VsCodeLaunch();
		extension.runConfigs
			.stream()
			.filter(RunConfig::isIdeConfigGenerated)
			.map(c -> c.cook(extension))
			.forEach(launch::add);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(launch);
		Files.write(launchJson, json.getBytes(StandardCharsets.UTF_8));
		
		//And create the run directories
		for(RunConfig cfg : extension.runConfigs) {
			if(cfg.isIdeConfigGenerated()) Files.createDirectories(cfg.resolveRunDir());
		}
	}
	
	@SuppressWarnings("unused")
	private static class VsCodeLaunch {
		public String version = "0.2.0";
		public List<VsCodeConfiguration> configurations = new ArrayList<>();

		public void add(RunConfig runConfig) {
			configurations.add(new VsCodeConfiguration(runConfig));
		}
	}

	@SuppressWarnings("unused")
	private static class VsCodeConfiguration {
		public String type = "java";
		public String name;
		public String request = "launch";
		public String cwd = "${workspaceFolder}/run";
		public String console = "internalConsole";
		public boolean stopOnEntry = false;
		public String mainClass;
		public String vmArgs;
		public String args;

		VsCodeConfiguration(RunConfig runConfig) {
			this.name = runConfig.getBaseName();
			this.mainClass = runConfig.getMainClass();
			this.vmArgs = runConfig.stringifyVmArgs();
			this.args = runConfig.stringifyProgramArgs();
		}
	}
}

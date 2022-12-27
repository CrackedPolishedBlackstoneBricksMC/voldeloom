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
import net.fabricmc.loom.providers.DependencyProvider;
import net.fabricmc.loom.providers.RunConfigProvider;
import org.gradle.api.Project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IntellijRunConfigsProvider extends DependencyProvider {
	public IntellijRunConfigsProvider(Project project, LoomGradleExtension extension, RunConfigProvider runs) {
		super(project, extension);
		this.runs = runs;
	}
	
	private final RunConfigProvider runs;
	
	@Override
	public void decorateProject() throws Exception {
		if(project != project.getRootProject()) {
			project.getLogger().lifecycle("Project " + project + " != root project " + project.getRootProject());
			return;
		}
		
		//Ensures the assets are downloaded when idea is syncing a project
		//TODO: I think this is handled by the dep provider system
		// But it will need to be something i look at when i get around to making assets only get downloaded when you runClient
		
		Path runConfigsDir = project.file(".idea").toPath().resolve("runConfigurations");
		Files.createDirectories(runConfigsDir);
		
		Path clientRunConfigFile = runConfigsDir.resolve("Minecraft_Client.xml");
		if(Files.notExists(clientRunConfigFile)) {
			Files.write(clientRunConfigFile, runs.getClient().configureTemplate("idea_run_config_template.xml").getBytes(StandardCharsets.UTF_8));
		}
		
		Path serverRunConfigFile = runConfigsDir.resolve("Minecraft_Client.xml");
		if(Files.notExists(serverRunConfigFile)) {
			Files.write(serverRunConfigFile, runs.getServer().configureTemplate("idea_run_config_template.xml").getBytes(StandardCharsets.UTF_8));
		}
		
		Path runDir = project.getRootDir().toPath().resolve(extension.runDir);
		Files.createDirectories(runDir);
	}
}

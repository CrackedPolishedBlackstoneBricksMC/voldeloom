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

package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.RunConfig;
import org.gradle.api.Project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IntellijRunConfigsProvider extends DependencyProvider {
	public IntellijRunConfigsProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	@Override
	public void decorateProject() throws Exception {
		if(project != project.getRootProject()) {
			project.getLogger().lifecycle("Project " + project + " != root project " + project.getRootProject());
			return;
		}
		
		Path ideaDir = project.file(".idea").toPath();
		if(Files.notExists(ideaDir)) {
			//Guess they're not using IDEA
			return;
		}
		
		Path runConfigsDir = ideaDir.resolve("runConfigurations");
		Files.createDirectories(runConfigsDir);
		
		for(RunConfig cfg : extension.runConfigs) {
			if(!cfg.isIdeConfigGenerated()) continue;
			
			RunConfig cooked = cfg.cook(extension);
			
			Path cfgFile = runConfigsDir.resolve(cooked.getBaseName() + ".xml");
			if(Files.notExists(cfgFile)) {
				Files.write(cfgFile, cooked.configureTemplate("idea_run_config_template.xml").getBytes(StandardCharsets.UTF_8));
			}
			Files.createDirectories(cooked.resolveRunDir());
		}
	}
}

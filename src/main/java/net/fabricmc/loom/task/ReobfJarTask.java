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
import net.fabricmc.loom.newprovider.RemapperMcp;
import net.fabricmc.loom.util.GradleSupport;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gradle task that remaps the mod under development into official names so you can release it!
 * 
 * TODO: Investigate...
 */
@SuppressWarnings({
	"UnstableApiUsage", //When the IDE is working against Gradle 4, a lot of the Gradle API was incubating
	"RedundantSuppression" //And when it's woring against Gradle 7, it got stabilized
})
public class ReobfJarTask extends Jar {
	public ReobfJarTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Remaps the mod under development into the distribution naming scheme, ready for publishing.");
		getOutputs().upToDateWhen(__ -> false); //TODO
	}
	
	private final RegularFileProperty input = GradleSupport.getRegularFileProperty(getProject());

	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		
		if(extension.getProviderGraph().reobfSrg == null) {
			for(int i = 0; i < 10; i++) getLogger().error("[Voldeloom] RELEASE REMAPPING FOR SPLIT JARS (1.2.5) IS CURRENTLY BROKEN!!!!!!! SORRY!!!");
			return;
		}
		
		Path input = this.getInput().getAsFile().get().toPath();
		Path output = this.getArchivePath().toPath(); //TODO: deprecated and think it's missing in gradle 4 (needs GradleSupport reflection hell)
		
		//TODO: correct?
		Set<Path> remapClasspath = project.getConfigurations()
			.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
			.getFiles()
			.stream()
			.map(File::toPath)
			.filter(p -> !input.equals(p) && Files.exists(p))
			.collect(Collectors.toSet());
		
		//TODO: weird
		Files.deleteIfExists(output);
		RemapperMcp.doIt(input, output, extension.getProviderGraph().reobfSrg, getLogger(), null, remapClasspath);

		if(Files.notExists(output)) {
			throw new RuntimeException("Failed to remap " + input + " to " + output + " - file missing!");
		}
	}

	@InputFile
	public RegularFileProperty getInput() {
		return input;
	}
}

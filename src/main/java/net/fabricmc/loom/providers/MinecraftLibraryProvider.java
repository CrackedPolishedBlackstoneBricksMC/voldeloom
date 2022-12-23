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

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class MinecraftLibraryProvider {
	private final Collection<File> libs = new HashSet<>();

	public void provide(MinecraftProvider minecraftProvider, Project project) throws IOException {
		MinecraftVersionInfo versionInfo = minecraftProvider.getVersionInfo();
		
		File minecraftLibs = new File(WellKnownLocations.getUserCache(project), "libraries");
		
		for(MinecraftVersionInfo.Library library : versionInfo.libraries) {
			if(library.allowed() && !library.isNative() && library.getFile(minecraftLibs) != null) {
				if(library.getArtifactName().contains("org.ow2.asm")) {
					//voldeloom: conflicts with forge's ASM 4 dep
					continue;
				}

				project.getDependencies().add(Constants.MINECRAFT_DEPENDENCIES, project.getDependencies().module(library.getArtifactName()));
			}
		}
		
		// voldeloom: add loader's dependencies. versions this old simply do not depend on these.
		//(VOLDELOOM-DISASTER) don't actually; i think whoever wrote that was referring to fabric loader, gson 2.8.6 came out in 2019
		//maybe they were doing some kind of fabric-on-forge thing? :pausefrogeline:
		//project.getDependencies().add(GradleSupport.runtimeOrRuntimeOnly, project.getDependencies().module("org.apache.logging.log4j:log4j-core:2.8.1"));
		//project.getDependencies().add(GradleSupport.runtimeOrRuntimeOnly, project.getDependencies().module("org.apache.logging.log4j:log4j-api:2.8.1"));
		//project.getDependencies().add(GradleSupport.runtimeOrRuntimeOnly, project.getDependencies().module("com.google.code.gson:gson:2.8.6"));
		//project.getDependencies().add(GradleSupport.runtimeOrRuntimeOnly, project.getDependencies().module("com.google.guava:guava:28.0-jre"));
	}

	public Collection<File> getLibraries() {
		return libs;
	}
}

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
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class MinecraftLibraryProvider extends DependencyProvider {
	public MinecraftLibraryProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private final Collection<File> nonNativeLibs = new HashSet<>();
	private File nativesDir;
	
	@Override
	public void decorateProject() throws Exception {
		MinecraftProvider mcProvider = extension.getDependencyManager().getMinecraftProvider();
		MinecraftVersionInfo versionInfo = mcProvider.getVersionInfo();
		
		nativesDir = new File(WellKnownLocations.getUserCache(project), "natives/" + mcProvider.getMinecraftVersion());
		
		File jarStore = WellKnownLocations.getNativesJarStore(project);
		
		for(MinecraftVersionInfo.Library library : versionInfo.libraries) {
			if(!library.allowed()) continue;
			
			if(library.isNative()) {
				File libJarFile = library.getFile(jarStore);
				DownloadUtil.downloadIfChanged(new URL(library.getURL()), libJarFile, project.getLogger());
				//TODO possibly find a way to prevent needing to re-extract after each run, doesnt seem too slow (original Loom comment)
				ZipUtil.unpack(libJarFile, nativesDir);
			} else {
				String depToAdd = library.getArtifactName();
				
				//TODO: Launchwrapper is not used with the `direct` launch method, which is intended to be the voldeloom default.
				// If a launchwrapper-based launch method is used, note that lw requires ASM 4 to be on the classpath.
				// (I might be able to get away with using the same version Forge requests, but I'm currently allowing
				//  Forge to load its own libraries instead of trying to shove them on the classpath myself.)
				// I'm currently not using lw because the version requested for 1.4 does not support --assetIndex
				// with the stock tweaker, which means bothering with lw does not provide much of a value-add.
				// It might also be possible to update LW to version 1.12 (which has targetCompatibility 6), but that
				// requires ASM 5 to run.
//				if(library.getArtifactName().equals("net.minecraft:launchwrapper:1.5")) {
//					continue;
//				} else {
//					depToAdd = library.getArtifactName();
//				}
				
				//It appears downloading the library manually is not necessary, since the minecraft info .json
				//gives maven coordinates which Gradle can resolve the usual way off of mojang's maven
				
				//TODO move "physically depending on things" out
				project.getDependencies().add(Constants.MINECRAFT_DEPENDENCIES, depToAdd);
			}
		}
	}

	//TODO: Voldeloom had a bug where it didn't actually write anything to this collection lol
	// Returning an empty collection here to maintain the buggy behavior. Later I will analyze the impact
	public Collection<File> getNonNativeLibraries() {
		//return nonNativeLibs;
		return Collections.emptyList();
	}
	
	public File getNativesDir() {
		return nativesDir;
	}
}

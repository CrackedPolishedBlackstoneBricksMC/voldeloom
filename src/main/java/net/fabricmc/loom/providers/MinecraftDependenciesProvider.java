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

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.ZipUtil;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Minecraft has a number of dependencies and native libraries. They are specified in a custom version manifest format, not with a maven POM.
 * This is in charge of reading the version manifest and adding them as real Gradle project dependencies.
 * It also downloads native libraries and extracts their contents.
 */
public class MinecraftDependenciesProvider extends DependencyProvider {
	@Inject
	public MinecraftDependenciesProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc) {
		super(project, extension);
		this.mc = mc;
	}
	
	private final MinecraftProvider mc;
	
	private final Collection<Path> nonNativeLibs = new HashSet<>();
	private Path nativesDir;
	
	public void decorateProject() throws Exception {
		//inputs
		MinecraftVersionInfo versionInfo = mc.getVersionManifest();
		
		//outputs
		nativesDir = WellKnownLocations.getUserCache(project).resolve("natives").resolve(mc.getVersion());
		Path nativesJarStore = nativesDir.resolve("jars");
		cleanIfRefreshDependencies();
		
		Files.createDirectories(nativesJarStore);
		
		//task
		for(MinecraftVersionInfo.Library library : versionInfo.libraries) {
			if(!library.allowed()) continue;
			
			if(library.isNative()) {
				Path libJarFile = library.getPath(nativesJarStore);
				
				//download the natives jar
				new DownloadSession(library.getURL(extension), project)
					.dest(libJarFile)
					.etag(true)
					.gzip(false)
					.skipIfExists()
					.skipIfSha1Equals(library.getSha1())
					.download();
				
				//unpack it
				Path unpackFlag = libJarFile.resolveSibling(libJarFile.getFileName().toString() + ".unpack-flag");
				if(!Files.exists(unpackFlag)) {
					//unpack the natives jar
					ZipUtil.unpack(libJarFile, nativesDir, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
							if(dir.endsWith("META-INF")) return FileVisitResult.SKIP_SUBTREE;
							else return FileVisitResult.CONTINUE;
						}
					});
					
					//create the unpack-flag file, so i don't have to do this again next time
					//(partially because the files are in-use by running copies of the Minecraft client, so they can't be overwritten while one is open,
					//and partially because this gets hit every time you launch the game, so i shouldn't do work i can skip :) )
					Files.write(unpackFlag, "Presence of this file tells Voldeloom that the corresponding native library JAR was already extracted to the filesystem.\n".getBytes(StandardCharsets.UTF_8));
				}
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
		
		installed = true;
	}

	//TODO: Voldeloom had a bug where it didn't actually write anything to this collection lol
	// Returning an empty collection here to maintain the buggy behavior. Later I will analyze the impact
	public Collection<Path> getNonNativeLibraries() {
		//return nonNativeLibs;
		return Collections.emptyList();
	}
	
	public Path getNativesDir() {
		return nativesDir;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		ArrayList<Path> funny = new ArrayList<>(nonNativeLibs);
		funny.add(nativesDir);
		return funny;
	}
}

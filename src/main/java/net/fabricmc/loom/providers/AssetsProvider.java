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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

/**
 * Downloads Minecraft's global asset index, the asset index for the selected version, and downloads all assets inside that index.
 * Results go into the Gradle user cache.
 * 
 * This class resolves assets using the "legacy" file layout only (real filenames, not hashes with the `objects` folder).
 */
public class AssetsProvider extends DependencyProvider {
	public AssetsProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path assetIndexFile;
	private Path thisVersionAssetsDir;
	
	public void decorateProject(MinecraftProvider mc) throws Exception {
		MinecraftVersionInfo versionInfo = mc.getVersionManifest();
		MinecraftVersionInfo.AssetIndex assetIndexInfo = versionInfo.assetIndex;
		
		Path globalAssetsCache = WellKnownLocations.getUserCache(project).resolve("assets");
		
		//outputs
		assetIndexFile = globalAssetsCache.resolve("indexes").resolve(assetIndexInfo.getFabricId(mc.getVersion()) + ".json");
		thisVersionAssetsDir = globalAssetsCache.resolve("legacy").resolve(mc.getVersion());
		
		//tasks
		
		boolean offline = project.getGradle().getStartParameter().isOffline();
		if (Files.notExists(assetIndexFile) || !Checksum.compareSha1(assetIndexFile, assetIndexInfo.sha1)) {
			project.getLogger().lifecycle(":downloading asset index");

			if (offline) {
				if (Files.exists(assetIndexFile)) {
					//We know it's outdated but can't do anything about it, oh well
					project.getLogger().warn("Asset index outdated");
				} else {
					//We don't know what assets we need, just that we don't have any
					throw new GradleException("Asset index not found at " + assetIndexFile.toAbsolutePath());
				}
			} else {
				Files.createDirectories(globalAssetsCache);
				new DownloadSession(assetIndexInfo.url, project)
					.dest(assetIndexFile)
					.etag(true)
					.gzip(true)
					.download();
			}
		}
		
		//Btw, using this `legacy` folder just to get out of regular Loom's way
		
		if(Files.notExists(thisVersionAssetsDir)) {
			project.getLogger().lifecycle(":downloading assets...");
			
			JsonObject assetJson;
			try(BufferedReader in = Files.newBufferedReader(assetIndexFile)) {
				assetJson = new Gson().fromJson(in, JsonObject.class);
			}
			JsonObject objectsJson = assetJson.getAsJsonObject("objects");
			
			//just logging for fun
			int assetCount = objectsJson.size();
			project.getLogger().lifecycle("|-> Found " + assetCount + " assets to download.");
			int downloadedCount = 0, nextLogAssetCount = 0, logCount = 0;
			
			for(String filename : objectsJson.keySet()) {
				Path destFile = thisVersionAssetsDir.resolve(filename);
				if(Files.notExists(destFile)) {
					Files.createDirectories(destFile.getParent());
					
					String sha1 = objectsJson.get(filename).getAsJsonObject().get("hash").getAsString();
					String shsha1 = sha1.substring(0, 2) + '/' + sha1;
					new DownloadSession(extension.resourcesBaseUrl + shsha1, project)
						.quiet()
						.dest(destFile)
						.gzip(true)
						.etag(false) //we're hopefully not gonna be redownloading these
						.skipIfExists()
						.download();
					
					//just logging for fun
					downloadedCount++;
					if(downloadedCount >= nextLogAssetCount) {
						project.getLogger().lifecycle("|-> " + logCount * 10 + "%...");
						logCount++;
						nextLogAssetCount = logCount * assetCount / 10;
					}
				}
			}
			project.getLogger().lifecycle("|-> Done!");
		}
		
		installed = true;
	}
	
	public Path getAssetIndexFile() {
		return assetIndexFile;
	}
	
	public Path getAssetsDir() {
		return thisVersionAssetsDir;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Arrays.asList(assetIndexFile, thisVersionAssetsDir);
	}
}
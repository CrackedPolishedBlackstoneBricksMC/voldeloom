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

package net.fabricmc.loom.newprovider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Downloads Minecraft's global asset index, the asset index for the selected version, and downloads all assets inside that index.
 * Results go into the Gradle user cache.
 * <p>
 * This class resolves assets using the "legacy" file layout only (real filenames, not hashes with the `objects` folder).
 */
public class AssetDownloader extends NewProvider<AssetDownloader> {
	public AssetDownloader(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private ConfigElementWrapper mc;
	private MinecraftVersionInfo versionManifest;
	private String resourcesBaseUrl;
	
	public AssetDownloader mc(ConfigElementWrapper mc) {
		this.mc = mc;
		return this;
	}
	
	public AssetDownloader versionManifest(MinecraftVersionInfo versionManifest) {
		this.versionManifest = versionManifest;
		return this;
	}
	
	public AssetDownloader resourcesBaseUrl(String resourcesBaseUrl) {
		this.resourcesBaseUrl = resourcesBaseUrl;
		return this;
	}
	
	private Path thisVersionAssetsDir, assetIndexFile, assetsCache;
	
	public Path getAssetsDir() {
		return thisVersionAssetsDir;
	}
	
	public AssetDownloader computePaths() throws Exception {
		assetsCache = getCacheDir().resolve("assets");
		
		assetIndexFile = assetsCache.resolve("indexes").resolve(versionManifest.assetIndex.getFabricId(mc.getVersion()) + ".json");
		thisVersionAssetsDir = assetsCache.resolve("legacy").resolve(mc.getVersion());
		//Btw, using this `legacy` folder just to get out of regular Loom's way.
		
		log.info("] asset index: {}", assetIndexFile);
		
		//Let's not delete the assets themselves on refreshDependencies.
		//They're rarely the problem, and take a long time to download.
		cleanOnRefreshDependencies(assetIndexFile/*, thisVersionAssetsDir*/);
		
		return this;
	}
	
	public AssetDownloader downloadAssets() throws Exception {
		Files.createDirectories(assetsCache);
		
		newDownloadSession(versionManifest.assetIndex.url)
			.dest(assetIndexFile)
			.etag(true)
			.gzip(true)
			.skipIfSha1Equals(versionManifest.assetIndex.sha1) //TODO: kinda subsumed by skipIfExists lol
			.skipIfExists()
			.download();
		
		if(Files.notExists(thisVersionAssetsDir)) {
			log.lifecycle("|-> Downloading assets...");
			JsonObject assetJson;
			try(BufferedReader in = Files.newBufferedReader(assetIndexFile)) {
				assetJson = new Gson().fromJson(in, JsonObject.class);
			}
			JsonObject objectsJson = assetJson.getAsJsonObject("objects");
			
			//just logging for fun
			int assetCount = objectsJson.size();
			log.lifecycle("|-> Found {} assets to download.", assetCount);
			int downloadedCount = 0, nextLogAssetCount = 0, logCount = 0;
			
			for(String filename : objectsJson.keySet()) {
				Path destFile = thisVersionAssetsDir.resolve(filename);
				if(Files.notExists(destFile)) {
					Files.createDirectories(destFile.getParent());
					
					String sha1 = objectsJson.get(filename).getAsJsonObject().get("hash").getAsString();
					String shsha1 = sha1.substring(0, 2) + '/' + sha1;
					newDownloadSession(resourcesBaseUrl + shsha1)
						.quiet()
						.dest(destFile)
						.gzip(true)
						.etag(false) //we're hopefully not gonna be redownloading these
						.skipIfExists()
						.download();
					
					//just logging for fun
					downloadedCount++;
					if(downloadedCount >= nextLogAssetCount) {
						log.lifecycle("\\-> " + logCount * 10 + "%...");
						logCount++;
						nextLogAssetCount = logCount * assetCount / 10;
					}
				}
			}
			log.info("|-> Done!");
		}
		
		return this;
	}
}

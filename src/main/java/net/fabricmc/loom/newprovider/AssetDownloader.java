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
import net.fabricmc.loom.util.VersionManifest;
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
	
	//inputs
	private VersionManifest versionManifest;
	private String resourcesBaseUrl;
	
	//outputs
	private Path assetIndexJson;
	private Path assetsDir;
	private boolean legacyLayout;
	
	//privates
	private boolean freshAssetIndex;
	private JsonObject assets;
	
	public AssetDownloader versionManifest(VersionManifest versionManifest) {
		this.versionManifest = versionManifest;
		return this;
	}
	
	public AssetDownloader resourcesBaseUrl(String resourcesBaseUrl) {
		this.resourcesBaseUrl = resourcesBaseUrl;
		return this;
	}
	
	public Path getAssetsDir() {
		return assetsDir;
	}
	
	public Path getAssetIndex() {
		return assetIndexJson;
	}
	
	public AssetDownloader prepare() throws Exception {
		Path assetsCache = getCacheDir().resolve("assets");
		
		assetIndexJson = assetsCache.resolve("indexes").resolve(versionManifest.assetIndexReference.id + ".json");
		
		//Let's not delete the assets themselves on refreshDependencies.
		//They're rarely the problem, and take a long time to download.
		//That's why i check freshAssetIndex before deleting it on refreshDependencies.
		freshAssetIndex = Files.notExists(assetIndexJson);
		cleanOnRefreshDependencies(assetIndexJson);
		
		//download asset index
		newDownloadSession(versionManifest.assetIndexReference.url)
			.dest(assetIndexJson)
			.etag(true)
			.gzip(true)
			.skipIfSha1Equals(versionManifest.assetIndexReference.sha1) //TODO: kinda subsumed by skipIfExists lol
			//.skipIfExists()
			.download();
		
		//parse it as json
		try(BufferedReader in = Files.newBufferedReader(assetIndexJson)) {
			assets = new Gson().fromJson(in, JsonObject.class);
		}
		
		//find what layout it's in
		legacyLayout =
			//Every version other than 1.6:
			(assets.has("map_to_resources") && assets.getAsJsonPrimitive("map_to_resources").getAsBoolean()) ||
			//1.6, for some reason:
			(assets.has("virtual") && assets.getAsJsonPrimitive("virtual").getAsBoolean());
		
		//decide what directory to put the output artifacts in
		if(legacyLayout) assetsDir = assetsCache.resolve("legacy").resolve(versionManifest.assetIndexReference.id);
		else assetsDir = assetsCache.resolve("objects");
		
		return this;
	}
	
	public AssetDownloader downloadAssets() throws Exception {
		if(freshAssetIndex) {
			log.lifecycle("|-> Downloading assets to {}...", assetsDir);
			JsonObject objects = assets.getAsJsonObject("objects");
			
			//<logging>
			int assetCount = objects.size();
			log.lifecycle("|-> Found {} assets to download.", assetCount);
			int downloadedCount = 0, nextLogAssetCount = 0, logCount = 0;
			//</logging>
			
			for(String filename : objects.keySet()) {
				String sha1 = objects.get(filename).getAsJsonObject().get("hash").getAsString();
				String sh = sha1.substring(0, 2);
				String shsha1 = sh + '/' + sha1;
				
				Path destFile = legacyLayout ?
					assetsDir.resolve(filename) :
					assetsDir.resolve(sh).resolve(sha1);
				
				if(Files.notExists(destFile)) {
					newDownloadSession(resourcesBaseUrl + shsha1)
						.quiet()
						.dest(destFile)
						.gzip(true)
						.etag(false) //we're hopefully not gonna be redownloading these
						.skipIfExists()
						.download();
				}
				
				//<logging>
				downloadedCount++;
				if(downloadedCount >= nextLogAssetCount) {
					log.lifecycle("\\-> " + logCount * 10 + "%...");
					logCount++;
					nextLogAssetCount = logCount * assetCount / 10;
				}
				//</logging>
			}
			log.info("|-> Done!");
		}
		
		return this;
	}
}

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
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileReader;
import java.net.URL;

public class AssetsProvider extends DependencyProvider {
	public AssetsProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc) {
		super(project, extension);
		this.mc = mc;
	}
	
	private final MinecraftProvider mc;
	
	private File assetIndexFile;
	private File thisVersionAssetsDir;
	
	@Override
	public void decorateProject() throws Exception {
		MinecraftVersionInfo versionInfo = mc.getVersionManifest();
		MinecraftVersionInfo.AssetIndex assetIndexInfo = versionInfo.assetIndex;
		
		File globalAssetsCache = new File(WellKnownLocations.getUserCache(project), "assets");
		
		//outputs
		assetIndexFile = new File(globalAssetsCache, "indexes" + File.separator + assetIndexInfo.getFabricId(mc.getVersion()) + ".json");
		thisVersionAssetsDir = new File(new File(globalAssetsCache, "legacy"), mc.getVersion());
		
		//tasks
		
		boolean offline = project.getGradle().getStartParameter().isOffline();
		if (!assetIndexFile.exists() || !Checksum.equals(assetIndexFile, assetIndexInfo.sha1)) {
			project.getLogger().lifecycle(":downloading asset index");

			if (offline) {
				if (assetIndexFile.exists()) {
					//We know it's outdated but can't do anything about it, oh well
					project.getLogger().warn("Asset index outdated");
				} else {
					//We don't know what assets we need, just that we don't have any
					throw new GradleException("Asset index not found at " + assetIndexFile.getAbsolutePath());
				}
			} else {
				globalAssetsCache.mkdirs();
				DownloadUtil.downloadIfChanged(new URL(assetIndexInfo.url), assetIndexFile, project.getLogger());
			}
		}
		
		//TODO: I removed all code relating to the modern assets system (that uses the objects/ folder)
		//Btw, using this `legacy` folder just to get out of its way
		
		if(!thisVersionAssetsDir.exists()) {
			project.getLogger().lifecycle(":downloading assets...");
			
			JsonObject assetJson;
			try(FileReader in = new FileReader(assetIndexFile)) {
				assetJson = new Gson().fromJson(in, JsonObject.class);
			}
			JsonObject objectsJson = assetJson.getAsJsonObject("objects");
			
			//just logging for fun
			int assetCount = objectsJson.size();
			project.getLogger().lifecycle("|-> Found " + assetCount + " assets to download.");
			int downloadedCount = 0, nextLogAssetCount = 0, logCount = 0;
			
			for(String filename : objectsJson.keySet()) {
				File file = new File(thisVersionAssetsDir, filename);
				if(!file.exists()) {
					file.getParentFile().mkdirs();
					
					String sha1 = objectsJson.get(filename).getAsJsonObject().get("hash").getAsString();
					String shsha1 = sha1.substring(0, 2) + '/' + sha1;
					DownloadUtil.downloadIfChanged(new URL(Constants.RESOURCES_BASE + shsha1), file, project.getLogger(), true);
					
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
	}
	
	public File getAssetIndexFile() {
		return assetIndexFile;
	}
	
	public File getAssetsDir() {
		return thisVersionAssetsDir;
	}
}

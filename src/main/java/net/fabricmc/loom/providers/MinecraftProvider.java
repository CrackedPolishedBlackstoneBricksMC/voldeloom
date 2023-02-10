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
import net.fabricmc.loom.Constants;
import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses the custom non-Maven version manifest used by Minecraft. Downloads the split minecraft client and server jars.
 */
public class MinecraftProvider extends DependencyProvider {
	@Inject
	public MinecraftProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private String minecraftVersion;
	
	private Path clientJar;
	private Path serverJar;
	private MinecraftVersionInfo thisVersionManifest;
	
	@Override
	public boolean projectmapSelf() {
		return extension.customManifestUrl != null;
	}
	
	@Override
	protected void performSetup() throws Exception {
		minecraftVersion = getSingleDependency(Constants.MINECRAFT).getDependency().getVersion();
		
		project.getLogger().lifecycle("] Minecraft {}", minecraftVersion);
		
		Path versionManifestIndexJson = getCacheDir().resolve("version_manifest.json");
		Path thisVersionManifestJson = getCacheDir().resolve("minecraft-" + minecraftVersion + "-info.json");
		
		clientJar = getCacheDir().resolve("minecraft-" + minecraftVersion + "-client.jar");
		serverJar = getCacheDir().resolve("minecraft-" + minecraftVersion + "-server.jar");
		
		cleanOnRefreshDependencies(andEtags(Arrays.asList(clientJar, serverJar, thisVersionManifestJson, versionManifestIndexJson)));
		
		//We're gonna keep going, actually - `thisVersionManifest` is our goal, and it's nice to get this done by the end of setup()
		//because other setup functions can make use of it.
		//All these downloaders no-op if the file exists and we're offline btw, and the happy path makes 0 http connections
		
		project.getLogger().info("|-> Downloading manifest index...");
		new DownloadSession("https://launchermeta.mojang.com/mc/game/version_manifest.json", project)
			.dest(versionManifestIndexJson)
			.etag(true)
			.gzip(true)
			.skipIfNewerThan(Period.ofDays(14))
			.download();
		
		project.getLogger().info("|-> Parsing manifest index...");
		ManifestVersion versionManifestIndex;
		try(BufferedReader reader1 = Files.newBufferedReader(versionManifestIndexJson)) {
			versionManifestIndex = new Gson().fromJson(reader1, ManifestVersion.class);
		}
		
		ManifestVersion.VersionData selectedVersion = null;
		if(extension.customManifestUrl != null) {
			project.getLogger().lifecycle("!! Using custom Minecraft per-version manifest at URL: {}", extension.customManifestUrl);
			selectedVersion = new ManifestVersion.VersionData();
			selectedVersion.id = minecraftVersion;
			selectedVersion.url = extension.customManifestUrl;
		} else {
			project.getLogger().info("|-> Browsing manifest index, looking for per-version manifest for {}...", minecraftVersion);
			for(ManifestVersion.VersionData indexedVersion : versionManifestIndex.versions) { //what's a little O(N) lookup between friends
				if(indexedVersion.id.equalsIgnoreCase(minecraftVersion)) {
					selectedVersion = indexedVersion;
					break;
				}
			}
			
			if(selectedVersion == null || selectedVersion.url == null) {
				throw new IllegalStateException("Could not find a per-version manifest corresponding to Minecraft version '" + minecraftVersion + "' in version_manifest.json ('" + versionManifestIndexJson +"').");
			}
		}
		
		project.getLogger().info("|-> Found URL for Minecraft {} per-version manifest, downloading...", minecraftVersion);
		new DownloadSession(selectedVersion.url, project)
			.dest(thisVersionManifestJson)
			.gzip(true)
			.etag(true)
			.skipIfExists()
			.download();
		
		//Parse the per-version manifest.
		//-- Ultimately, this is why we do all the previous downloading in setup().
		//This json file contains a lot of information that is useful to have early-on.
		project.getLogger().info("|-> Parsing per-version manifest...");
		try(BufferedReader reader = Files.newBufferedReader(thisVersionManifestJson)) {
			thisVersionManifest = new Gson().fromJson(reader, MinecraftVersionInfo.class);
		}
	}
	
	public void performInstall() throws Exception {
		project.getLogger().info("|-> Downloading Minecraft {} client jar...", minecraftVersion);
		new DownloadSession(thisVersionManifest.downloads.get("client").url, project)
			.dest(clientJar)
			.etag(true)
			.gzip(false)
			.skipIfExists()
			.skipIfSha1Equals(thisVersionManifest.downloads.get("client").sha1) //TODO: kinda subsumed by skipIfExists lol
			.download();
		
		project.getLogger().info("|-> Downloading Minecraft {} server jar...", minecraftVersion);
		new DownloadSession(thisVersionManifest.downloads.get("server").url, project)
			.dest(serverJar)
			.etag(true)
			.gzip(false)
			.skipIfExists()
			.skipIfSha1Equals(thisVersionManifest.downloads.get("server").sha1)
			.download();
	}
	
	public Path getClientJar() {
		return clientJar;
	}
	
	public Path getServerJar() {
		return serverJar;
	}

	public String getVersion() {
		return minecraftVersion;
	}
	
	//Resolved in setup()
	public MinecraftVersionInfo getVersionManifest() {
		return thisVersionManifest;
	}
	
	//designed to be parsed with google gson
	public static class ManifestVersion {
		public List<VersionData> versions = new ArrayList<>();
	
		public static class VersionData {
			public String id, url;
		}
	}
}

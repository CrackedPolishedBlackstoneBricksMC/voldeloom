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

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.ManifestVersion;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.StaticPathWatcher;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class MinecraftProvider extends DependencyProvider {
	private String minecraftVersion;
	private String minecraftJarStuff;

	private MinecraftVersionInfo versionInfo;
	private MinecraftLibraryProvider libraryProvider;

	private File minecraftJson;
	private File minecraftClientJar;
	private File minecraftServerJar;

	Gson gson = new Gson();

	public MinecraftProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}

	@Override
	public void decorateProject() throws Exception {
		//deps
		DependencyInfo minecraftDependency = getSingleDependency(Constants.MINECRAFT);
		minecraftVersion = minecraftDependency.getDependency().getVersion();
		
		//TODO remove this dep, move "jar stuff" to ForgePatchedProvider or remove it 
		ForgeProvider forge = extension.getDependencyManager().getForgeProvider();
		minecraftJarStuff = minecraftDependency.getDependency().getVersion() + "-forge-" + forge.getForgeVersion();
		
		//outputs (+versionInfo)
		File userCache = WellKnownLocations.getUserCache(project);
		minecraftJson = new File(userCache, "minecraft-" + minecraftVersion + "-info.json");
		minecraftClientJar = new File(userCache, "minecraft-" + minecraftVersion + "-client.jar");
		minecraftServerJar = new File(userCache, "minecraft-" + minecraftVersion + "-server.jar");
		
		//execution
		boolean offline = project.getGradle().getStartParameter().isOffline();
		downloadMcJson(offline);

		try(FileReader reader = new FileReader(minecraftJson)) {
			versionInfo = gson.fromJson(reader, MinecraftVersionInfo.class);
		}

		if(offline) {
			if(minecraftClientJar.exists() && minecraftServerJar.exists()) {
				project.getLogger().debug("Found client and server jars, presuming up-to-date");
			} else if(new File(userCache, "minecraft-" + minecraftVersion + "-merged.jar").exists()) {
				//(Cheating a bit by predicting the output filename of MinecraftMergedProvider.)
				//Strictly we don't need the split jars if the merged one exists, let's try go on
				project.getLogger().warn("Missing a game jar but merged jar present, things might end badly");
			} else {
				throw new GradleException("Missing jar(s); Client: " + minecraftClientJar.exists() + ", Server: " + minecraftServerJar.exists());
			}
		} else {
			downloadJars(project.getLogger());
		}

		//TODO move up
		libraryProvider = new MinecraftLibraryProvider();
		libraryProvider.provide(this, project);
	}
	
	private void downloadMcJson(boolean offline) throws IOException {
		File manifests = new File(WellKnownLocations.getUserCache(project), "version_manifest.json");

		if (offline) {
			if (manifests.exists()) {
				//If there is the manifests already we'll presume that's good enough
				project.getLogger().debug("Found version manifests, presuming up-to-date");
			} else {
				//If we don't have the manifests then there's nothing more we can do
				throw new GradleException("Version manifests not found at " + manifests.getAbsolutePath());
			}
		} else {
			project.getLogger().debug("Downloading version manifests");
			DownloadUtil.downloadIfChanged(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), manifests, project.getLogger());
		}

		String versionManifest = Files.asCharSource(manifests, StandardCharsets.UTF_8).read();
		ManifestVersion mcManifest = new GsonBuilder().create().fromJson(versionManifest, ManifestVersion.class);

		Optional<ManifestVersion.Versions> optionalVersion = Optional.empty();
		
		if (extension.customManifest != null) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = minecraftVersion;
			customVersion.url = extension.customManifest;
			optionalVersion = Optional.of(customVersion);
			project.getLogger().lifecycle("Using custom minecraft manifest");
		}

		if (!optionalVersion.isPresent()) {
			optionalVersion = mcManifest.versions.stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();
		}

		if (optionalVersion.isPresent()) {
			if (offline) {
				if (minecraftJson.exists()) {
					//If there is the manifest already we'll presume that's good enough
					project.getLogger().debug("Found Minecraft {} manifest, presuming up-to-date", minecraftVersion);
				} else {
					//If we don't have the manifests then there's nothing more we can do
					throw new GradleException("Minecraft " + minecraftVersion + " manifest not found at " + minecraftJson.getAbsolutePath());
				}
			} else {
				if (StaticPathWatcher.INSTANCE.hasFileChanged(minecraftJson.toPath())) {
					project.getLogger().debug("Downloading Minecraft {} manifest", minecraftVersion);
					DownloadUtil.downloadIfChanged(new URL(optionalVersion.get().url), minecraftJson, project.getLogger());
				}
			}
		} else {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}
	}

	private void downloadJars(Logger logger) throws IOException {
		if (!minecraftClientJar.exists() || (!Checksum.equals(minecraftClientJar, versionInfo.downloads.get("client").sha1) && StaticPathWatcher.INSTANCE.hasFileChanged(minecraftClientJar.toPath()))) {
			logger.debug("Downloading Minecraft {} client jar", minecraftVersion);
			DownloadUtil.downloadIfChanged(new URL(versionInfo.downloads.get("client").url), minecraftClientJar, logger);
		}

		if (!minecraftServerJar.exists() || (!Checksum.equals(minecraftServerJar, versionInfo.downloads.get("server").sha1) && StaticPathWatcher.INSTANCE.hasFileChanged(minecraftServerJar.toPath()))) {
			logger.debug("Downloading Minecraft {} server jar", minecraftVersion);
			DownloadUtil.downloadIfChanged(new URL(versionInfo.downloads.get("server").url), minecraftServerJar, logger);
		}
	}
	
	public File getClientJar() {
		return minecraftClientJar;
	}
	
	public File getServerJar() {
		return minecraftServerJar;
	}

	public String getMinecraftVersion() {
		return minecraftVersion;
	}
	
	public String getJarStuff() {
		return minecraftJarStuff;
	}

	public MinecraftVersionInfo getVersionInfo() {
		return versionInfo;
	}

	public MinecraftLibraryProvider getLibraryProvider() {
		return libraryProvider;
	}
	
	//several places, including run configs, launch provider, and MinecraftNativesProvider
	//MOVED from LoomDependencyManager
	public File getNativesDirectory() {
		File natives = new File(WellKnownLocations.getUserCache(project), "natives/" + minecraftVersion);
		if (!natives.exists()) natives.mkdirs();
		return natives;
	}
}

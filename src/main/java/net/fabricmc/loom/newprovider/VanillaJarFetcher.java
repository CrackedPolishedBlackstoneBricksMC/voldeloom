package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.VersionManifest;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VanillaJarFetcher extends NewProvider<VanillaJarFetcher> {
	public VanillaJarFetcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private ConfigElementWrapper mc;
	private @Nullable String customManifestUrl;
	
	//outputs
	private Path clientJar;
	private Path serverJar;
	private VersionManifest versionManifest;
	
	public VanillaJarFetcher mc(ConfigElementWrapper mc) {
		this.mc = mc;
		return this;
	}
	
	//Must call before clientJarFilename to set projectmapped flag
	public VanillaJarFetcher customManifestUrl(@Nullable String customManifestUrl) {
		this.customManifestUrl = customManifestUrl;
		setProjectmapped(customManifestUrl != null);
		
		return this;
	}
	
	public VanillaJarFetcher clientFilename(String clientFilename) {
		this.clientJar = getCacheDir().resolve(clientFilename);
		return this;
	}
	
	public VanillaJarFetcher serverFilename(String serverFilename) {
		this.serverJar = getCacheDir().resolve(serverFilename);
		return this;
	}
	
	public Path getClientJar() {
		return clientJar;
	}
	
	public Path getServerJar() {
		return serverJar;
	}
	
	public VersionManifest getVersionManifest() {
		return versionManifest;
	}
	
	//process
	public VanillaJarFetcher fetch() throws Exception {
		Preconditions.checkNotNull(mc, "minecraft version");
		
		Path versionManifestIndexJson = getCacheDir().resolve("version_manifest.json");
		Path thisVersionManifestJson = getCacheDir().resolve("minecraft-" + mc.getFilenameSafeVersion() + "-info.json");
		
		log.lifecycle("] client jar: {}", clientJar);
		log.lifecycle("] server jar: {}", serverJar);
		log.lifecycle("] manifest index: {}", versionManifestIndexJson);
		log.lifecycle("] this version manifest: {}", thisVersionManifestJson);
		
		cleanOnRefreshDependencies(andEtags(Arrays.asList(clientJar, serverJar, thisVersionManifestJson, versionManifestIndexJson)));
		
		log.info("|-> Downloading manifest index...");
		newDownloadSession("https://launchermeta.mojang.com/mc/game/version_manifest.json")
			.dest(versionManifestIndexJson)
			.etag(true)
			.gzip(true)
			.skipIfNewerThan(Period.ofDays(14))
			.download();
		
		log.info("|-> Parsing manifest index...");
		ManifestVersion versionManifestIndex;
		try(BufferedReader reader = Files.newBufferedReader(versionManifestIndexJson)) {
			versionManifestIndex = new Gson().fromJson(reader, ManifestVersion.class);
		}
		
		ManifestVersion.VersionData selectedVersion = null;
		if(customManifestUrl != null) {
			log.lifecycle("!! Using custom Minecraft per-version manifest at URL: {}", customManifestUrl);
			selectedVersion = new ManifestVersion.VersionData();
			selectedVersion.id = mc.getVersion();
			selectedVersion.url = customManifestUrl;
		} else {
			log.info("|-> Browsing manifest index, looking for per-version manifest for {}...", mc.getVersion());
			for(ManifestVersion.VersionData indexedVersion : versionManifestIndex.versions) { //what's a little O(N) lookup between friends
				if(indexedVersion.id.equalsIgnoreCase(mc.getVersion())) {
					selectedVersion = indexedVersion;
					break;
				}
			}
			
			if(selectedVersion == null || selectedVersion.url == null) {
				throw new IllegalStateException("Could not find a per-version manifest corresponding to Minecraft version '" + mc.getVersion() + "' in version_manifest.json ('" + versionManifestIndexJson +"').");
			}
		}
		
		log.info("|-> Found URL for Minecraft {} per-version manifest, downloading...", mc.getVersion());
		newDownloadSession(selectedVersion.url)
			.dest(thisVersionManifestJson)
			.gzip(true)
			.etag(true)
			.skipIfExists()
			.download();
		
		log.info("|-> Parsing per-version manifest...");
		try(BufferedReader reader = Files.newBufferedReader(thisVersionManifestJson)) {
			versionManifest = new Gson().fromJson(reader, VersionManifest.class);
		}
		
		log.info("|-> Downloading Minecraft {} client jar...", mc.getVersion());
		newDownloadSession(versionManifest.downloads.get("client").url)
			.dest(clientJar)
			.etag(true)
			.gzip(false)
			.skipIfExists()
			.skipIfSha1Equals(versionManifest.downloads.get("client").sha1) //TODO: kinda subsumed by skipIfExists lol
			.download();
		
		log.info("|-> Downloading Minecraft {} server jar...", mc.getVersion());
		newDownloadSession(versionManifest.downloads.get("server").url)
			.dest(serverJar)
			.etag(true)
			.gzip(false)
			.skipIfExists()
			.skipIfSha1Equals(versionManifest.downloads.get("server").sha1)
			.download();
		
		return this;
	}
	
	//designed to be parsed with google gson
	public static class ManifestVersion {
		public List<ManifestVersion.VersionData> versions = new ArrayList<>();
		
		public static class VersionData {
			public String id, url;
		}
	}
}

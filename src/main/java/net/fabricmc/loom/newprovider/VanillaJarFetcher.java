package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.ManifestIndex;
import net.fabricmc.loom.util.VersionManifest;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.Period;
import java.util.Arrays;
import java.util.Locale;

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
		
		cleanOnRefreshDependencies(andEtags(Arrays.asList(clientJar, serverJar, versionManifestIndexJson, thisVersionManifestJson)));
		
		log.info("|-> Downloading manifest index...");
		newDownloadSession("https://launchermeta.mojang.com/mc/game/version_manifest.json")
			.dest(versionManifestIndexJson)
			.etag(true)
			.gzip(true)
			.skipIfNewerThan(Period.ofDays(14))
			.download();
		
		log.info("|-> Parsing manifest index...");
		ManifestIndex versionManifestIndex = ManifestIndex.read(versionManifestIndexJson);
		
		ManifestIndex.VersionData selectedVersion = null;
		if(customManifestUrl != null) {
			log.lifecycle("!! Using custom Minecraft per-version manifest at URL: {}", customManifestUrl);
			selectedVersion = new ManifestIndex.VersionData();
			selectedVersion.id = mc.getVersion();
			selectedVersion.url = customManifestUrl;
		} else {
			log.info("|-> Browsing manifest index, looking for per-version manifest for {}...", mc.getVersion());
			selectedVersion = versionManifestIndex.versions.get(mc.getVersion().toLowerCase(Locale.ROOT));
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
		versionManifest = VersionManifest.read(thisVersionManifestJson);
		
		log.info("|-> Downloading Minecraft {} client jar...", mc.getVersion());
		newDownloadSession(versionManifest.downloads.get("client").url)
			.dest(clientJar)
			.etag(true)
			.gzip(false)
			//.skipIfExists()
			.skipIfSha1Equals(versionManifest.downloads.get("client").sha1) //TODO: kinda subsumed by skipIfExists lol
			.download();
		
		log.info("|-> Downloading Minecraft {} server jar...", mc.getVersion());
		newDownloadSession(versionManifest.downloads.get("server").url)
			.dest(serverJar)
			.etag(true)
			.gzip(false)
			//.skipIfExists()
			.skipIfSha1Equals(versionManifest.downloads.get("server").sha1)
			.download();
		
		return this;
	}
}

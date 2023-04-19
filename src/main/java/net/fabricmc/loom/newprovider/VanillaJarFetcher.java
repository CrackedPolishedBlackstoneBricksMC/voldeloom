package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.ManifestIndex;
import net.fabricmc.loom.util.VersionManifest;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.Period;
import java.util.Locale;

public class VanillaJarFetcher extends NewProvider<VanillaJarFetcher> {
	public VanillaJarFetcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private ConfigElementWrapper mc;
	private @Nullable String customManifestUrl;
	private String clientFilename, serverFilename;
	
	public VanillaJarFetcher mc(ConfigElementWrapper mc) {
		this.mc = mc;
		props.put("mcversion", mc.getVersion());
		return this;
	}
	
	public VanillaJarFetcher customManifestUrl(@Nullable String customManifestUrl) {
		this.customManifestUrl = customManifestUrl;
		props.put("customManifestUrl", customManifestUrl);
		return this;
	}
	
	public VanillaJarFetcher clientFilename(String clientFilename) {
		this.clientFilename = clientFilename;
		return this;
	}
	
	public VanillaJarFetcher serverFilename(String serverFilename) {
		this.serverFilename = serverFilename;
		return this;
	}
	
	//outputs
	private Path clientJar;
	private Path serverJar;
	private VersionManifest versionManifest;
	
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
		Check.notNull(mc, "minecraft version");
		
		Path versionManifestIndexJson = getOrCreate(props.substFilename(getCacheDir().resolve("version_manifest_{HASH}.json")), dest3 -> {
			log.info("|-> Downloading manifest index to {}...", dest3);
			newDownloadSession("https://launchermeta.mojang.com/mc/game/version_manifest.json")
				.dest(dest3)
				.etag(true)
				.gzip(true)
				.skipIfNewerThan(Period.ofDays(14))
				.download();
		});
		log.lifecycle("] manifest index: {}", versionManifestIndexJson);
		
		log.info("|-> Parsing manifest index...");
		ManifestIndex versionManifestIndex = ManifestIndex.read(versionManifestIndexJson);
		ManifestIndex.VersionData selectedVersion;
		if(customManifestUrl != null) {
			log.lifecycle("!! Using custom Minecraft per-version manifest at URL: {}", customManifestUrl);
			selectedVersion = new ManifestIndex.VersionData();
			selectedVersion.id = mc.getVersion();
			selectedVersion.url = customManifestUrl;
		} else {
			log.info("|-> Browsing manifest index, looking for per-version manifest for {}...", mc.getVersion());
			selectedVersion = versionManifestIndex.versions.get(mc.getVersion().toLowerCase(Locale.ROOT));
			if(selectedVersion == null || selectedVersion.url == null) {
				throw new IllegalStateException("Could not find a per-version manifest corresponding to Minecraft version '" + mc.getVersion() + "' in version_manifest.json ('" + versionManifestIndexJson + "').");
			}
		}
		
		Path thisVersionManifestJson = getOrCreate(props.substFilename(getCacheDir().resolve("minecraft-" + mc.getFilenameSafeVersion() + "-info-{HASH}.json")), dest2 -> {
			log.info("|-> Found URL for Minecraft {} per-version manifest, downloading to {}...", mc.getVersion(), dest2);
			newDownloadSession(selectedVersion.url)
				.dest(dest2)
				.gzip(true)
				.etag(true)
				.download();
		});
		log.lifecycle("] this version manifest: {}", thisVersionManifestJson);
		
		log.info("|-> Parsing per-version manifest...");
		versionManifest = VersionManifest.read(thisVersionManifestJson);
		
		clientJar = getOrCreate(props.substFilename(getCacheDir().resolve(clientFilename)), dest1 -> {
			log.info("|-> Downloading Minecraft {} client jar to {}...", mc.getVersion(), dest1);
			newDownloadSession(versionManifest.downloads.get("client").url)
				.dest(dest1)
				.etag(true)
				.gzip(false)
				.download();
		});
		log.lifecycle("] client jar: {}", clientJar);
		
		serverJar = getOrCreate(props.substFilename(getCacheDir().resolve(serverFilename)), dest -> {
			log.info("|-> Downloading Minecraft {} server jar to {}...", mc.getVersion(), dest);
			newDownloadSession(versionManifest.downloads.get("server").url)
				.dest(dest)
				.etag(true)
				.gzip(false)
				.download();
		});
		log.lifecycle("] server jar: {}", serverJar);
		
		return this;
	}
}

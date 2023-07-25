package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.OperatingSystem;
import net.fabricmc.loom.util.VersionManifest;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class VanillaDependencyFetcher extends NewProvider<VanillaDependencyFetcher> {
	public VanillaDependencyFetcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private VersionManifest manifest;
	private String librariesBaseUrl;
	private String nativesDirName;
	
	public VanillaDependencyFetcher manifest(VersionManifest manifest) {
		this.manifest = manifest;
		props.put("manifest-id", manifest.id); //Probably redundant
		return this;
	}
	
	public VanillaDependencyFetcher librariesBaseUrl(String librariesBaseUrl) {
		this.librariesBaseUrl = librariesBaseUrl;
		props.put("librariesBaseUrl", librariesBaseUrl); //Changing the URL should redownload libraries
		return this;
	}
	
	public VanillaDependencyFetcher nativesDirname(String nativesDirname) {
		this.nativesDirName = nativesDirname;
		return this;
	}
	
	//outputs
	private Path nativesDir;
	private final Collection<String> mavenDependencies = new ArrayList<>();
	
	public Path getNativesDir() {
		return nativesDir;
	}
	
	public Collection<String> getMavenDependencies() {
		return mavenDependencies;
	}
	
	//TODO: Upstream Voldeloom had a bug where it didn't actually write anything to this collection lol
	// Returning an empty collection here to maintain the buggy behavior. Later I will analyze the impact
	//  HEY IT's me from the future. I'm not seeing any issue, and there's no code to maintain this collection anyway
	// (it was the realized files inside the mavenDependencies collection)
	public Collection<Path> getNonNativeLibraries_Todo() {
		//return nonNativeLibs;
		return Collections.emptyList();
	}
	
	//process
	public VanillaDependencyFetcher fetch() throws Exception {
		Check.notNull(nativesDirName, "natives directory name");
		Check.notNull(manifest, "minecraft version manifest");
		Check.notNull(librariesBaseUrl, "libraries base URL");
		
		nativesDir = getOrCreate(getCacheDir().resolve("natives").resolve(props.subst(nativesDirName)), dest -> {
			log.info("|-> Downloading native libraries into {}", dest);
			Files.createDirectories(dest);
			
			for(VersionManifest.Library library : manifest.libraries) {
				if(!library.allowed() || !library.hasNatives()) continue;
				
				VersionManifest.LibraryArtifact nativeArtifact = library.nativeArtifactFor(OperatingSystem.getOS(), OperatingSystem.getArch());
				
				//download the natives jar
				Path libJar = newDownloadSession(nativeArtifact.url)
					.dest(nativeArtifact.resolveFlat(dest.resolve("jars")))
					.etag(false) //no need to save etag, this directory is fresh.
					.gzip(true)
					.download();
				
				//extract them all onto each other in nativesDir - n.b., the file visitor here is used as a "filter" argument
				//TODO: actually parse the mojang version manifest instead of hardcoding a meta-inf exclusion?
				ZipUtil.unpack(libJar, dest, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
						if(dir.endsWith("META-INF")) return FileVisitResult.SKIP_SUBTREE;
						else return FileVisitResult.CONTINUE;
					}
				});
			}
		});
		log.lifecycle("] native libraries directory: {}", nativesDir);
		
		for(VersionManifest.Library library : manifest.libraries) {
			if(!library.allowed() || library.hasNatives()) continue;
			//It appears downloading the library manually is not necessary since the json
			//gives maven coordinates which Gradle can resolve the usual way off of mojang's maven
			log.info("|-> Found Minecraft maven-style dependency {}", library.getMavenCoordinate());
			mavenDependencies.add(library.getMavenCoordinate());
		}
		
		return this;
	}
	
	public VanillaDependencyFetcher installDependenciesToProject(String config, DependencyHandler deps) {
		for(String mavenDep : mavenDependencies) {
			deps.add(config, mavenDep);
		}
		
		return this;
	}
}

package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.nio.charset.StandardCharsets;
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
	private ConfigElementWrapper mc;
	private MinecraftVersionInfo manifest;
	private String librariesBaseUrl;
	
	public VanillaDependencyFetcher mc(ConfigElementWrapper mc) {
		this.mc = mc;
		return this;
	}
	
	public VanillaDependencyFetcher manifest(MinecraftVersionInfo manifest) {
		this.manifest = manifest;
		return this;
	}
	
	public VanillaDependencyFetcher librariesBaseUrl(String librariesBaseUrl) {
		this.librariesBaseUrl = librariesBaseUrl;
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
	public Collection<Path> getNonNativeLibraries_Todo() {
		//return nonNativeLibs;
		return Collections.emptyList();
	}
	
	//process
	public VanillaDependencyFetcher fetch() throws Exception {
		nativesDir = getCacheDir().resolve("natives").resolve(mc.getVersion());
		Path nativesJarStore = nativesDir.resolve("jars");
		
		cleanOnRefreshDependencies(nativesDir);
		
		Files.createDirectories(nativesJarStore);
		
		for(MinecraftVersionInfo.Library library : manifest.libraries) {
			if(!library.allowed()) continue;
			
			if(library.isNative()) {
				log.info("Found minecraft native dependency {}", library.getArtifactName());
				
				Path libJarFile = library.getPath(nativesJarStore);
				
				//download the natives jar
				newDownloadSession(librariesBaseUrl + library.getURLSuffix())
					.dest(libJarFile)
					.etag(true)
					.gzip(false)
					.skipIfExists()
					.skipIfSha1Equals(library.getSha1())
					.download();
				
				//unpack it
				Path unpackFlag = libJarFile.resolveSibling(libJarFile.getFileName().toString() + ".unpack-flag");
				if(!Files.exists(unpackFlag)) {
					//unpack the natives jar
					ZipUtil.unpack(libJarFile, nativesDir, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
							if(dir.endsWith("META-INF")) return FileVisitResult.SKIP_SUBTREE;
							else return FileVisitResult.CONTINUE;
						}
					});
					
					//create the unpack-flag file, so i don't have to do this again next time
					//(partially because the files are in-use by running copies of the Minecraft client, so they can't be overwritten while one is open,
					//and partially because this gets hit every time you launch the game, so i shouldn't do work i can skip :) )
					Files.write(unpackFlag, "Presence of this file tells Voldeloom that the corresponding native library JAR was already extracted to the filesystem.\n".getBytes(StandardCharsets.UTF_8));
				}
			} else {
				String depToAdd = library.getArtifactName();
				
				//TODO: Launchwrapper is not used with the `direct` launch method, which is intended to be the voldeloom default.
				// If a launchwrapper-based launch method is used, note that lw requires ASM 4 to be on the classpath.
				// (I might be able to get away with using the same version Forge requests, but I'm currently allowing
				//  Forge to load its own libraries instead of trying to shove them on the classpath myself.)
				// I'm currently not using lw because the version requested for 1.4 does not support --assetIndex
				// with the stock tweaker, which means bothering with lw does not provide much of a value-add.
				// It might also be possible to update LW to version 1.12 (which has targetCompatibility 6), but that
				// requires ASM 5 to run.
//				if(library.getArtifactName().equals("net.minecraft:launchwrapper:1.5")) {
//					continue;
//				} else {
//					depToAdd = library.getArtifactName();
//				}
				
				//It appears downloading the library manually is not necessary, since the minecraft info .json
				//gives maven coordinates which Gradle can resolve the usual way off of mojang's maven
				
				log.info("|-> Found Minecraft maven-style dependency {}", depToAdd);
				mavenDependencies.add(depToAdd);
			}
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

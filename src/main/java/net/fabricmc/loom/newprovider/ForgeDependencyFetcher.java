package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.VersionManifest;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Forge auto-downloads dependencies at runtime, but the server is long dead, and I'd like to know about the
 * dependencies when creating the workspace. This sniffs Forge's dependencies out of the Forge jar with a little
 * static-analysis.
 */
public class ForgeDependencyFetcher extends NewProvider<ForgeDependencyFetcher> {
	public ForgeDependencyFetcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private Path forgeJar;
	private String fmlLibrariesBaseUrl;
	private boolean bouncycastleCheat;
	
	public ForgeDependencyFetcher forgeJar(Path forgeJar) {
		this.forgeJar = forgeJar;
		return this;
	}
	
	public ForgeDependencyFetcher fmlLibrariesBaseUrl(String fmlLibrariesBaseUrl) {
		this.fmlLibrariesBaseUrl = fmlLibrariesBaseUrl;
		return this;
	}
	
	public ForgeDependencyFetcher bouncycastleCheat(boolean bouncycastleCheat) {
		this.bouncycastleCheat = bouncycastleCheat;
		return this;
	}
	
	//outputs
	private Path libDownloaderDir;
	
	private final Collection<String> sniffedLibDownloaderJarNames = new ArrayList<>();
	private final Collection<Path> resolvedLibDownloaderJars = new ArrayList<>();
	private final Collection<String> sniffedMavenDepNames = new ArrayList<>();
	
	public ForgeDependencyFetcher libDownloaderDir(String extractedLibrariesDirname) {
		this.libDownloaderDir = getCacheDir().resolve("forgeLibs").resolve(extractedLibrariesDirname);
		return this;
	}
	
	//procedure
	public ForgeDependencyFetcher sniff() throws Exception {
		Check.notNull(forgeJar, "forge jar");
		
		class LibrarySniffingClassVisitor extends ClassVisitor {
			public LibrarySniffingClassVisitor() {
				super(Opcodes.ASM4, null);
			}
			
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				//static initializer
				if(name.equals("<clinit>")) return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, name, descriptor, signature, exceptions)) {
					@Override
					public void visitLdcInsn(Object value) {
						//This method ldcs library filenames ("argo-2.25.jar") and also their SHA1 hashes as strings.
						//I differentiate between them by just looking for the .jar suffix, but I guess another way could be doing more thorough static analysis,
						//and seeing which array the string constants end up being written to.
						if(value instanceof String && ((String) value).endsWith(".jar")) {
							log.info("|-> Found Forge library: {}", value);
							sniffedLibDownloaderJarNames.add((String) value);
						}
					}
				};
				
				else return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}
		
		try(FileSystem forgeFs = ZipUtil.openFs(forgeJar)) {
			//read from magical hardcoded path inside the forge jar; this is where the auto-downloaded library paths are stored
			//TODO: applies from forge 1.3 through forge 1.5, dropped in 1.6
			//TODO: at least 1.5 includes additional "deobfuscation data" zip dep, but also contains a sys property to change download mirror
			Path coreFmlLibsPath = forgeFs.getPath("/cpw/mods/fml/relauncher/CoreFMLLibraries.class");
			if(Files.exists(coreFmlLibsPath)) {
				log.info("|-> Parsing cpw.mods.fml.relauncher.CoreFMLLibraries...");
				try(InputStream in = Files.newInputStream(coreFmlLibsPath)) {
					new ClassReader(in).accept(new LibrarySniffingClassVisitor(), ClassReader.SKIP_FRAMES); //just don't need frames
				}
			} else {
				log.info("|-> No cpw.mods.fml.relauncher.CoreFMLLibraries class in this Forge jar.");
			}
			
			Path versionJsonPath = forgeFs.getPath("/version.json");
			if(Files.exists(versionJsonPath)) {
				log.info("|-> A version.json exists in this Forge jar, I guess we're in the Launchwrapper era. Parsing it for libraries.");
				VersionManifest versionManifest = VersionManifest.read(versionJsonPath); //yup, the vanilla format
				
				for(VersionManifest.Library lib : versionManifest.libraries) {
					//todo: all the natives handling from vanilla's library sniffer too?
					if(lib.allowed() && !lib.isCustomForge()) {
						sniffedMavenDepNames.add(lib.name);
					}
				}
			} else {
				log.info("|-> No version.json exists in this jar.");
			}
		}
		
		if(bouncycastleCheat) {
			sniffedLibDownloaderJarNames.add("bcprov-jdk15on-147.jar");
			log.info("|-> Cheating and pretending bcprov-jdk15on-147.jar is a Forge library...");
		}
		
		log.info("] found {} lib-downloader libraries and {} Maven libraries", sniffedLibDownloaderJarNames.size(), sniffedMavenDepNames.size());
		
		return this;
	}
	
	public ForgeDependencyFetcher fetch() throws Exception {
		Check.notNull(libDownloaderDir, "extracted libraries dir");
		Check.notNull(fmlLibrariesBaseUrl, "FML libraries URL");
		
		cleanOnRefreshDependencies(libDownloaderDir);
		
		if(!sniffedLibDownloaderJarNames.isEmpty()) {
			Files.createDirectories(libDownloaderDir);
			
			for(String lib : sniffedLibDownloaderJarNames) {
				Path dest = libDownloaderDir.resolve(lib);
				resolvedLibDownloaderJars.add(dest);
				
				newDownloadSession(fmlLibrariesBaseUrl + lib)
					.dest(dest)
					.etag(true)
					.gzip(false)
					.skipIfExists()
					.download();
			}
		}
		
		return this;
	}
	
	public ForgeDependencyFetcher installDependenciesToProject(String config, DependencyHandler deps) {
		for(Path resolvedLibrary : resolvedLibDownloaderJars) {
			deps.add(config, files(resolvedLibrary));
		}
		
		for(String mavenDep : sniffedMavenDepNames) {
			deps.add(config, mavenDep);
		}
		
		return this;
	}
}

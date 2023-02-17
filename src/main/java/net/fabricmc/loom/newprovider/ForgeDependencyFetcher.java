package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
	
	//outputs
	private Path extractedLibrariesDir;
	private final Collection<String> sniffedLibraries = new ArrayList<>();
	private final Collection<Path> resolvedLibraries = new ArrayList<>();
	
	public ForgeDependencyFetcher forgeJar(Path forgeJar) {
		this.forgeJar = forgeJar;
		return this;
	}
	
	public ForgeDependencyFetcher fmlLibrariesBaseUrl(String fmlLibrariesBaseUrl) {
		this.fmlLibrariesBaseUrl = fmlLibrariesBaseUrl;
		return this;
	}
	
	public ForgeDependencyFetcher extractedLibrariesDirname(String extractedLibrariesDirname) {
		this.extractedLibrariesDir = getCacheDir().resolve("forgeLibs").resolve(extractedLibrariesDirname);
		return this;
	}
	
	//procedure
	public ForgeDependencyFetcher sniff() throws Exception {
		Preconditions.checkNotNull(forgeJar, "forge jar");
		
		class LibrarySniffingClassVisitor extends ClassVisitor {
			public LibrarySniffingClassVisitor(ClassVisitor classVisitor, Collection<String> sniffedLibraries) {
				super(Opcodes.ASM4, classVisitor);
				this.sniffedLibraries = sniffedLibraries;
			}
			
			private final Collection<String> sniffedLibraries;
			
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
							sniffedLibraries.add((String) value);
						}
					}
				};
				
				else return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}
		
		try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forgeJar.toUri()), Collections.emptyMap())) {
			//read from magical hardcoded path inside the forge jar; this is where the auto-downloaded library paths are stored
			//TODO: applies from forge 1.3 through forge 1.5, dropped in 1.6
			//TODO: at least 1.5 includes additional "deobfuscation data" zip dep, but also contains a sys property to change download mirror
			Path coreFmlLibsPath = forgeFs.getPath("/cpw/mods/fml/relauncher/CoreFMLLibraries.class");
			if(Files.exists(coreFmlLibsPath)) {
				log.info("|-> Parsing cpw.mods.fml.relauncher.CoreFMLLibraries...");
				try(InputStream in = Files.newInputStream(coreFmlLibsPath)) {
					new ClassReader(in).accept(new LibrarySniffingClassVisitor(null, sniffedLibraries), ClassReader.SKIP_FRAMES); //just don't need frames
				}
			} else {
				log.info("|-> No cpw.mods.fml.relauncher.CoreFMLLibraries class in this jar.");
			}
		}
		
		return this;
	}
	
	public ForgeDependencyFetcher fetch() throws Exception {
		Preconditions.checkNotNull(extractedLibrariesDir, "extracted libraries dir");
		Preconditions.checkNotNull(fmlLibrariesBaseUrl, "FML libraries URL");
		
		cleanOnRefreshDependencies(extractedLibrariesDir);
		Files.createDirectories(extractedLibrariesDir);
		
		for(String lib : sniffedLibraries) {
			Path dest = extractedLibrariesDir.resolve(lib);
			resolvedLibraries.add(dest);
			
			newDownloadSession(fmlLibrariesBaseUrl + lib)
				.dest(dest)
				.etag(true)
				.gzip(false)
				.skipIfExists()
				.download();
		}
		
		return this;
	}
	
	public ForgeDependencyFetcher installDependenciesToProject(String config, DependencyHandler deps) {
		for(Path resolvedLibrary : resolvedLibraries) {
			deps.add(config, files(resolvedLibrary));
		}
		
		return this;
	}
}

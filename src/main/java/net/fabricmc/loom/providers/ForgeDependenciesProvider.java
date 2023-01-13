package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.DownloadSession;
import org.gradle.api.Project;
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
import java.util.List;

/**
 * Forge/FML depends on a handful of additional libraries, which it wants to download at runtime then shove onto the classpath.
 * (At the time, the FML coremod system included functionality for specifying libraries to automatically download.)
 * 
 * Two problems with this:
 * - I'd like to know about the dependencies, so I can attach them in the IDE.
 * -> They aren't in the maven POM.
 * - The servers it wants to download from are long-dead. The download will fail.
 * -> I need to download the libraries first and put them where Forge expects to find them, so it won't try.
 * 
 * This provider statically analyzes the class that Forge stores its library list in, downloads them from a mirror service,
 * then adds them as true project dependencies. The library folder is also accessible with getForgeLibsFolder().
 * 
 * The "putting them where Forge expects to find them" step is handled by ShimForgeLibrariesTask.
 */
public class ForgeDependenciesProvider extends DependencyProvider {
	public ForgeDependenciesProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path forgeLibsFolder;
	
	public void decorateProject(ForgeProvider forge) throws Exception {
		forgeLibsFolder = WellKnownLocations.getUserCache(project).resolve("forgeLibs").resolve(forge.getVersion());
		Files.createDirectories(forgeLibsFolder);
		
		List<String> sniffedLibraries = new ArrayList<>();
		
		try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getJar().toUri()), Collections.emptyMap())) {
			//read from magical hardcoded path inside the forge jar; this is where the auto-downloaded library paths are stored
			//TODO: applies from forge 1.3 through forge 1.5, dropped in 1.6
			//TODO: at least 1.5 includes additional "deobfuscation data" zip dep, but also contains a sys property to change download mirror
			Path coreFmlLibsPath = forgeFs.getPath("/cpw/mods/fml/relauncher/CoreFMLLibraries.class");
			if(Files.exists(coreFmlLibsPath)) {
				project.getLogger().info("|-> Parsing cpw.mods.fml.relauncher.CoreFMLLibraries...");
				try(InputStream in = Files.newInputStream(coreFmlLibsPath)) {
					new ClassReader(in).accept(new LibrarySniffingClassVisitor(null, sniffedLibraries), ClassReader.SKIP_FRAMES); //just don't need frames
				}
			} else {
				project.getLogger().info("|-> No cpw.mods.fml.relauncher.CoreFMLLibraries class in this jar.");
			}
		}
		
		//download each library
		for(String lib : sniffedLibraries) {
			Path dest = forgeLibsFolder.resolve(lib);
			
			new DownloadSession(extension.fmlLibrariesBaseUrl + lib, project)
				.dest(dest)
				.etag(true)
				.gzip(false)
				.skipIfExists()
				.download();
			
			//dep on each one individually instead of using project.files(forgeLibsFolder)
			//this is mainly to prevent the .etag files from being included, but also simplifies the file dependency
			//cause it can be hard to get the actual file listing out of a `files` dependency sometimes, in my experience
			project.getDependencies().add(Constants.FORGE_DEPENDENCIES, project.files(dest));
		}
		
		installed = true;
	}
	
	private static class LibrarySniffingClassVisitor extends ClassVisitor {
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
						sniffedLibraries.add((String) value);
					}
				}
			};
			
			else return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}
	
	public Path getForgeLibsFolder() {
		return forgeLibsFolder;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.singleton(forgeLibsFolder);
	}
}

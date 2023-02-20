package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

/**
 * Pastes one jar on top of another, and remembers to delete META-INF.
 * <p>
 * Also performs the, uh, critically important task of gluing the Minecraft version number onto the Forge version number.
 * This version tag is used in a few places. This should be removed.
 */
public class Jarmodder extends NewProvider<Jarmodder> {
	public Jarmodder(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private Path base, overlay;
	
	//outputs
	private Path jarmodded;
	
	public Jarmodder base(Path base) {
		this.base = base;
		return this;
	}
	
	public Jarmodder overlay(Path overlay) {
		this.overlay = overlay;
		return this;
	}
	
	public Jarmodder jarmoddedFilename(String jarmoddedFilename) {
		this.jarmodded = getCacheDir().resolve(jarmoddedFilename);
		return this;
	}
	
	public Path getJarmoddedJar() {
		return jarmodded;
	}
	
	public Jarmodder patch() throws Exception {
		Preconditions.checkNotNull(base, "jarmod base");
		Preconditions.checkNotNull(overlay, "jarmod overlay");
		
		cleanOnRefreshDependencies(jarmodded);
		
		log.lifecycle("] jarmodded: {}", jarmodded);
		
		if(Files.notExists(jarmodded)) {
			log.lifecycle("|-> Jarmodded jar does not exist, performing jarmod...");
			Files.createDirectories(jarmodded.getParent());
			
			try(FileSystem baseFs    = FileSystems.newFileSystem(URI.create("jar:" + base.toUri()),    Collections.emptyMap());
			    FileSystem overlayFs = FileSystems.newFileSystem(URI.create("jar:" + overlay.toUri()), Collections.emptyMap());
			    FileSystem patchedFs = FileSystems.newFileSystem(URI.create("jar:" + jarmodded.toUri()), Collections.singletonMap("create", "true"))) {
				log.lifecycle("|-> Copying base into patched jar...");
				Files.walkFileTree(baseFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path sourceDir, BasicFileAttributes attrs) throws IOException {
						if(sourceDir.endsWith("META-INF")) {
							return FileVisitResult.SKIP_SUBTREE;
						} else {
							Path destDir = patchedFs.getPath(sourceDir.toString());
							Files.createDirectories(destDir);
							return FileVisitResult.CONTINUE;
						}
					}
					
					@Override
					public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
						Path destPath = patchedFs.getPath(sourcePath.toString());
						Files.copy(sourcePath, destPath);
						return FileVisitResult.CONTINUE;
					}
				});
				
				log.lifecycle("|-> Copying patch over top...");
				Files.walkFileTree(overlayFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path sourceDir, BasicFileAttributes attrs) throws IOException {
						if(sourceDir.endsWith("META-INF")) {
							return FileVisitResult.SKIP_SUBTREE;
						} else {
							Path destDir = patchedFs.getPath(sourceDir.toString());
							Files.createDirectories(destDir);
							return FileVisitResult.CONTINUE;
						}
					}
					
					@Override
					public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
						//TODO: move this OUT, not into the general jarmods system
						// If this file is missing, Forge will assume it's in a dev environment and not do runtime binpatching
						if(sourcePath.toString().endsWith("binpatches.pack.lzma")) return FileVisitResult.CONTINUE;
						
						Path destPath = patchedFs.getPath(sourcePath.toString());
						Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			log.lifecycle("|-> Deleting META-INF... (just kidding, i didn't copy it in the first place)");
			log.lifecycle("|-> Jarmod success!");
		}
		
		return this;
	}
}

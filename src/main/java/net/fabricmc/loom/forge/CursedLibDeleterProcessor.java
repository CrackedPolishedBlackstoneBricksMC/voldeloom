package net.fabricmc.loom.forge;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.processors.JarProcessor;

public class CursedLibDeleterProcessor implements JarProcessor {

	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	
	@Override
	public void setup(Project project, LoomGradleExtension extension) {}

	@Override
	public void process(File file) {
		try(FileSystem mcFs = FileSystems.newFileSystem(URI.create("jar:" + file.toURI()), FS_ENV)) {
			Files.walkFileTree(mcFs.getPath("org"), new Deleter());
			Files.walkFileTree(mcFs.getPath("argo"), new Deleter());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isInvalid(File file) {
		return false;
	}

	private static class Deleter extends SimpleFileVisitor<Path> {

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.delete(file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			Files.delete(dir);
			return FileVisitResult.CONTINUE;
		}
		
	}
}

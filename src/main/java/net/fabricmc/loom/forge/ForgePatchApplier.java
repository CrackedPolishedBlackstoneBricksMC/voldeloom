package net.fabricmc.loom.forge;

import net.fabricmc.loom.LoomGradleExtension;

import java.io.File;
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
import java.util.Map;

public class ForgePatchApplier {

	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");

	public static void process(File mc, LoomGradleExtension extension) {
		ForgeProvider forgeProvider = extension.getDependencyManager().getForgeProvider();
		
		File forge = forgeProvider.getForge();
		try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toURI()), FS_ENV)) {
			try(FileSystem mcFs = FileSystems.newFileSystem(URI.create("jar:" + mc.toURI()), FS_ENV)) {
				Files.walk(forgeFs.getPath("/")).forEach(path -> {
					try {
						if(!Files.isDirectory(path)) {
							Files.copy(path, mcFs.getPath(path.toString()), StandardCopyOption.REPLACE_EXISTING);
						} else if(!Files.exists(mcFs.getPath(path.toString()))) {
							Files.createDirectory(mcFs.getPath(path.toString()));
						}
					} catch (IOException e) {
						throw new RuntimeException("Exception patching minecraft: ", e);
					}
				});
				Files.walkFileTree(mcFs.getPath("META-INF"), new DeletingFileVisitor());
			}
		} catch (IOException e) {
			throw new RuntimeException("Exception patching minecraft: ", e);
		}
	}
	
	private static class DeletingFileVisitor extends SimpleFileVisitor<Path> {
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

package net.fabricmc.loom.util;

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

public class NaiveRenamer {
	private Path input, output;
	
	public void doIt() throws Exception {
		try(FileSystem inFs = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()), Collections.emptyMap());
		    FileSystem outFs = FileSystems.newFileSystem(URI.create("jar:" + output.toUri()), Collections.singletonMap("create", "true"))) {
			Files.walkFileTree(inFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path inDir, BasicFileAttributes attrs) throws IOException {
					Files.createDirectories(outFs.getPath(inDir.toString()));
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path inPath, BasicFileAttributes attrs) throws IOException {
					Path outPath = outFs.getPath(inPath.toString());
					
					if(outPath.endsWith(".class")) {
						
					} else if(outPath.endsWith(".java")) {
						
					} else Files.copy(inPath, outPath);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
}

package net.fabricmc.loom.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Various bits of random crap for dealing with zip files.
 * 
 * Loom used to use zeroturnaround ZipUtils, but only used like, three methods.
 */
public class ZipUtil {
	public static byte[] readFully(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] shuttle = new byte[4096];
		int read;
		while((read = in.read(shuttle)) != -1) out.write(shuttle, 0, read);
		return out.toByteArray();
	}
	
	/**
	 * Extracts a ZIP file into the directory specified by {@code destRoot}. You may filter the extraction.
	 * 
	 * @param inZip The Zip file to extract.
	 * @param destRoot The root directory that the zip will be extracted into.
	 * @param filter If this returns anything other than {@code FileVisitResult.CONTINUE}, the file will not be extracted.
	 */
	public static void unpack(Path inZip, Path destRoot, SimpleFileVisitor<Path> filter) {
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + inZip.toUri()), Collections.emptyMap())) {
			Files.walkFileTree(zipFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path zipPath, BasicFileAttributes attrs) throws IOException {
					FileVisitResult filterResult = filter.preVisitDirectory(zipPath, attrs);
					if(filterResult != FileVisitResult.CONTINUE) return filterResult;
					
					Files.createDirectories(resolveAcrossFilesystems(destRoot, zipPath));
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path zipPath, BasicFileAttributes attrs) throws IOException {
					FileVisitResult filterResult = filter.visitFile(zipPath, attrs);
					if(filterResult != FileVisitResult.CONTINUE) return filterResult;
					
					Files.copy(zipPath, resolveAcrossFilesystems(destRoot, zipPath), StandardCopyOption.REPLACE_EXISTING);
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return filter.postVisitDirectory(dir, exc);
				}
			});
		} catch (Exception e) {
			throw new RuntimeException("Exception while unpacking file " + inZip + " to " + destRoot, e);
		}
	}
	
	/**
	 * Starts as {@code resolveRoot}, and resolves each segment of {@code in} against it, even if {@code in} is on a separate filesystem.
	 * 
	 * @throws IllegalArgumentException if {@code in} contains the {@code ../} parent-directory segment.
	 */
	public static Path resolveAcrossFilesystems(Path resolveRoot, Path in) {
		Path out = resolveRoot, last = resolveRoot;
		for(Path element : in) {
			out = out.resolve(element.toString());
			
			if(out.getNameCount() != last.getNameCount() + 1) throw new IllegalArgumentException("don't path traversal me or my son ever again");
			last = out;
		}
		return out;
	}
}

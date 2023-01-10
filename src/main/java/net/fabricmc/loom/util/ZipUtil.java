package net.fabricmc.loom.util;

import com.google.common.io.ByteStreams;

import java.io.File;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Various bits of random crap for dealing with zip files.
 * 
 * Loom used to use zeroturnaround ZipUtils, but only used like, three methods.
 */
public class ZipUtil {
	public static boolean containsEntry(File file, String name) {
		try(ZipFile zf = new ZipFile(file)) {
			return zf.getEntry(name) != null;
		} catch (Exception e) {
			throw new RuntimeException("Exception while checking entry " + name, e);
		}
	}
	
	public static byte[] unpackEntry(File file, String name) {
		try(ZipFile zf = new ZipFile(file)) {
			ZipEntry entry = zf.getEntry(name);
			try(InputStream in = zf.getInputStream(entry)) {
				return ByteStreams.toByteArray(in);
			}
		} catch (Exception e) {
			throw new RuntimeException("Exception while unpacking entry " + name, e);
		}
	}
	
	public static void unpack(Path inZip, Path destRoot) {
		unpack(inZip, destRoot, new SimpleFileVisitor<Path>() {}); //always returns CONTINUE
	}
	
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

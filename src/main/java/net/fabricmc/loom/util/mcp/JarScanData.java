package net.fabricmc.loom.util.mcp;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Walks a jar and reads all the field types from it.
 */
public class JarScanData {
	public final Map<String, String> fieldDescs = new HashMap<>();
	
	public JarScanData scan(Path mc) throws IOException {
		try(FileSystem mcFs = FileSystems.newFileSystem(URI.create("jar:" + mc.toUri()), Collections.emptyMap())) {
			ClassVisitor loader = new InfoLoadingClassVisitor();
			
			Files.walkFileTree(mcFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if(file.toString().endsWith(".class")) {
						try(InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
							new ClassReader(in).accept(loader, 0);
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
			
			return this;
		}
	}
	
	private /* non-static */ class InfoLoadingClassVisitor extends ClassVisitor {
		private String visiting;
		
		public InfoLoadingClassVisitor() {
			super(Opcodes.ASM7);
		}
		
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			visiting = name;
		}
		
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			fieldDescs.put(visiting + "/" + name, descriptor);
			return null;
		}
	}
}

package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.Check;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Walks a jar and reads all the field types from it.
 */
public class JarScanData {
	public final Map<String, Set<String>> innerClasses = new HashMap<>();
	
	public void scan(Path jar) throws IOException {
		try(FileSystem jarFs = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Collections.emptyMap())) {
			ClassVisitor loader = new InfoLoadingClassVisitor();
			
			Files.walkFileTree(jarFs.getPath("/"), new SimpleFileVisitor<Path>() {
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
		
		//classes keep a little table of the inner classes they contain, asm calls this for every entry in the table
		//jvm has special visibility rules for inner classes, and they are checked at runtime ! we gotta make sure to
		//note down all the inner class relationships so the remapper can place the inner classes in a legal location
		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			//there are three cases that can come up
			//1. outerName != null
			// -> the class in 'name' is an inner class of 'outerName', as expected
			//2. outerName == innerName == null, name == visiting
			// -> i have no idea what this one means. generally there is also a different
			//    kind of inner class declaration though if you keep scanning the jar.
			//    one example of this is mr$1 in 1.7.10
			//3. outerName == innerName == null, name != visiting
			// -> this means the class in `name` is an inner class of `visiting`.
			//    hypothesis: `name` is an anonymous class defined inside a method of `visiting`?
			//    one example of this is name mr$1, visiting mr, in 1.7.10
			
			String actualOuterName;
			
			if(outerName != null) {
				actualOuterName = outerName;
			} else {
				if(Objects.equals(name, visiting)) return; //case 2 above; this is spurious
				Check.notNull(visiting, "visiting == outerName == null, name " + name + " innerName " + innerName);
				actualOuterName = visiting;
			}
			
			innerClasses.computeIfAbsent(actualOuterName, __ -> new HashSet<>()).add(name);
		}
	}
}

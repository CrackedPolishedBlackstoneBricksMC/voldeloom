package net.fabricmc.loom.util.mcp;

import net.fabricmc.stitch.util.Pair;
import net.fabricmc.tinyremapper.IMappingProvider;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * A parser for MCP-format `.srg` files. Augments data discovered in the SRGs with inner-class and field method from a scanned JAR.
 * 
 * @author TwilightFlower
 */
public class SrgMappingProvider implements IMappingProvider {
	private final HashMap<String, String> classes = new HashMap<>();
	private final Collection<Pair<Member, String>> methods = new ArrayList<>();
	private final Collection<Pair<Member, String>> fields = new ArrayList<>();
	private final JarScanData scanData;

	public SrgMappingProvider(Path srg, JarScanData data) throws IOException {
		this.scanData = data;
		
		try(Scanner srgScanner = new Scanner(new BufferedInputStream(Files.newInputStream(srg)))) {
			while(srgScanner.hasNextLine()) {
				String[] line = srgScanner.nextLine().split(" ");
				switch(line[0]) {
				case "CL:":
					classes.put(line[1], line[2]);
					break;
				case "FD:":
					String desc;
					if((desc = scanData.fieldDescs.get(line[1])) == null) {
						System.out.println("Could not load descriptor for mapped field, skipping: " + line[1]);
						continue;
					}
					String unmappedClass = line[1].substring(0, line[1].lastIndexOf('/'));
					String unmappedName = line[1].substring(line[1].lastIndexOf('/')+1);
					String mappedName = line[2].substring(line[2].lastIndexOf('/')+1);
					fields.add(Pair.of(new Member(unmappedClass, unmappedName, desc), mappedName));
					break;
				case "MD:":
					unmappedClass = line[1].substring(0, line[1].lastIndexOf('/'));
					unmappedName = line[1].substring(line[1].lastIndexOf('/')+1);
					mappedName = line[3].substring(line[3].lastIndexOf('/')+1);
					methods.add(Pair.of(new Member(unmappedClass, unmappedName, line[2]), mappedName));
					break;
				default:
					break;
				}
			}
		}
	}
	
	@Override
	public void load(MappingAcceptor out) {
		for(String inner : scanData.innerClasses) {
			String[] split = inner.split("\\$", 2);
			String parentMapped;
			if((parentMapped = classes.get(split[0])) != null) {
				out.acceptClass(inner, parentMapped + "$" + split[1]);
				//System.out.println(parentMapped + "$" + split[1]);
			}
		}
		classes.forEach(out::acceptClass);
		methods.forEach(p -> out.acceptMethod(p.getLeft(), p.getRight()));
		fields.forEach(p -> out.acceptField(p.getLeft(), p.getRight()));
	}
	
	public static JarScanData scan(Path mc) throws IOException {
		try(FileSystem mcFs = FileSystems.newFileSystem(URI.create("jar:" + mc.toUri()), Collections.emptyMap())) {
			JarScanData scanData = new JarScanData();
			ClassVisitor loader = scanData.new InfoLoadingClassVisitor();
			
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
			
			return scanData;
		}
	}
	
	public static class JarScanData {		
		public final Map<String, String> fieldDescs = new HashMap<>();
		public final Collection<String> innerClasses = new ArrayList<>();
		
		public class InfoLoadingClassVisitor extends ClassVisitor {
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
			
			public void visitInnerClass(String name, String outerName, String innerName, int access) {
				innerClasses.add(name);
			}
		}
	}
}

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SrgMappingProvider implements IMappingProvider {

	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");

	private final Map<String, String> fieldDescs;
	private final Collection<String> innerClasses;
	
	private final HashMap<String, String> classes = new HashMap<>();
	private final Collection<Pair<Member, String>> methods = new ArrayList<>();
	private final Collection<Pair<Member, String>> fields = new ArrayList<>();

	public SrgMappingProvider(Path srg, Map<String, String> fieldDescs, Collection<String> innerClasses) throws IOException {
		this.fieldDescs = fieldDescs;
		this.innerClasses = innerClasses;
		try(Scanner srgScanner = new Scanner(new BufferedInputStream(Files.newInputStream(srg)))) {
			while(srgScanner.hasNextLine()) {
				String[] line = srgScanner.nextLine().split(" ");
				switch(line[0]) {
				case "CL:":
					classes.put(line[1], line[2]);
					break;
				case "FD:":
					String desc;
					if((desc = fieldDescs.get(line[1])) == null) {
						System.out.println("Could not load descriptor for mapped field, skipping: " + line[1]);
						break;
					}
					String unmappedClass = line[1].substring(0, line[1].lastIndexOf('/'));
					String unmappedName = line[1].substring(line[1].lastIndexOf('/')+1);
					String mappedName = line[2].substring(line[2].lastIndexOf('/')+1);
					fields.add(Pair.of(new Member(unmappedClass, unmappedName, fieldDescs.get(line[1])), mappedName));
					break;
				case "MD:":
					unmappedClass = line[1].substring(0, line[1].lastIndexOf('/'));
					unmappedName = line[1].substring(line[1].lastIndexOf('/')+1);
					mappedName = line[3].substring(line[3].lastIndexOf('/') + 1, line[3].length());
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
		for(String inner : innerClasses) {
			String[] split = inner.split("\\$", 2);
			String parentMapped;
			if((parentMapped = classes.get(split[0])) != null) {
				out.acceptClass(inner, parentMapped + "$" + split[1]);
				//System.out.println(parentMapped + "$" + split[1]);
			}
		}
		classes.entrySet().forEach(e -> out.acceptClass(e.getKey(), e.getValue()));
		methods.forEach(p -> out.acceptMethod(p.getLeft(), p.getRight()));
		fields.forEach(p -> out.acceptField(p.getLeft(), p.getRight()));
	}
	
	public static Pair<Map<String, String>, Collection<String>> calcInfo(Path mc) throws IOException {
		try (FileSystem mcFs = FileSystems.newFileSystem(URI.create("jar:" + mc.toUri()), FS_ENV)) {
			Map<String, String> fieldDescs = new HashMap<>();
			Collection<String> innerClasses = new ArrayList<>();
			ClassVisitor loader = new InfoLoadingClassVisitor(fieldDescs, innerClasses);
			Files.walk(mcFs.getPath("/"))
			.filter(p -> p.toString().endsWith(".class"))
			.forEach(p -> process(p, loader));
			return Pair.of(fieldDescs, innerClasses);
		}
	}

	private static void process(Path p, ClassVisitor cv) {
		try(InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
			ClassReader reader = new ClassReader(in);
			reader.accept(cv, 0);
		} catch (IOException e) {
			throw new RuntimeException("Error grabbing field descs: ", e);
		}
	}

	private static class InfoLoadingClassVisitor extends ClassVisitor {

		private String visiting;
		private final Map<String, String> map;
		private final Collection<String> inners;
		
		public InfoLoadingClassVisitor(Map<String, String> mapToPut, Collection<String> innerClasses) {
			super(Opcodes.ASM7);
			this.map = mapToPut;
			this.inners = innerClasses;
		}

		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			visiting = name;
		}
		
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			map.put(visiting + "/" + name, descriptor);
			return null;
		}
		
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			inners.add(name);
		}
	}
}

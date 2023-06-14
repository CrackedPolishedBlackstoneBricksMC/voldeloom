package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RemapperMcp extends NewProvider<RemapperMcp> {
	public RemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private Path input;
	private Srg srg;
	private String mappedDirectory, mappedFilename;
	private Set<String> deletedPrefixes;
	private final Set<Path> remapClasspath = new LinkedHashSet<>();
	
	public RemapperMcp inputJar(Path inputJar) {
		this.input = inputJar;
		return this;
	}
	
	public RemapperMcp srg(Srg srg) {
		this.srg = srg;
		return this;
	}
	
	public RemapperMcp outputSrgJar(String mappingsDepString, String outputName) {
		this.mappedDirectory = mappingsDepString;
		this.mappedFilename = outputName;
		return this;
	}
	
	public RemapperMcp deletedPrefixes(Set<String> deletedPrefixes) {
		this.deletedPrefixes = deletedPrefixes;
		return this;
	}
	
	public RemapperMcp addToRemapClasspath(Collection<Path> nonNativeLibs) {
		this.remapClasspath.addAll(nonNativeLibs);
		return this;
	}
	
	//outputs
	private Path mappedJar;
	
	public Path getOutputSrgJar() {
		return mappedJar;
	}
	
	public RemapperMcp remap() throws Exception {
		mappedJar = getOrCreate(getCacheDir().resolve("mapped").resolve(mappedDirectory).resolve(props.subst(mappedFilename)), dest ->
			doIt(input, dest, srg, log, deletedPrefixes, remapClasspath));
		
		return this;
	}
	
	public static void doIt(Path input, Path mappedJar, Srg srg, Logger log, @Nullable Set<String> deletedPrefixes, @Nullable Set<Path> remapClasspath) throws Exception {
		log.lifecycle("\\-> Constructing TinyRemapper");
		TinyRemapper remapper = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.ignoreFieldDesc(true) //MCP doesn't have them
			.skipLocalVariableMapping(true)
			.withMappings(srg.toMappingProvider())
			.extraPostApplyVisitor((trclass, next) -> new Asm4CompatClassVisitor(next)) //TODO maybe move this lol
			.build();
		
		log.lifecycle("] input jar: {}", input);
		log.lifecycle("] mapped jar: {}", mappedJar);
		
		//ugh
		if(remapClasspath != null) {
			Iterable<CharSequence> hooey = remapClasspath.stream().map(Path::toString).collect(Collectors.toList());
			log.lifecycle("] remap classpath: {}", String.join(", ", hooey));
		}
		
		log.lifecycle("\\-> Performing remap");
		
		OutputConsumerPath.Builder buildybuild = new OutputConsumerPath.Builder(mappedJar);
		if(deletedPrefixes != null && !deletedPrefixes.isEmpty()) buildybuild.filter(s -> !deletedPrefixes.contains(s.split("/", 2)[0]));
		try(OutputConsumerPath oc = buildybuild.build()) {
			oc.addNonClassFiles(input);
			if(remapClasspath != null) remapper.readClassPath(remapClasspath.toArray(new Path[0]));
			remapper.readInputs(input);
			remapper.apply(oc);
		} finally {
			remapper.finish();
		}
		
		log.lifecycle("\\-> Remap success! :)");
	}
	
	/**
	 * Basically tiny-remapper is putting things into the class file that aren't compatible with ASM api level 4, which
	 * many versions of Forge use to parse mod classes. Ex., for some reason after remapping, a parameter-name table
	 * shows up out of nowhere.
	 * <p>
	 * There are more things that aren't compatible with asm api 4 but i don't think tiny-remapper will add them,
	 * and they aren't as easily silently-droppable as this stuff (like what am i supposed to do if i find an
	 * invokedynamic at this stage lmao)
	 * <p>
	 * <a href="https://www.youtube.com/watch?v=n2IZbbuFxWg">https://www.youtube.com/watch?v=n2IZbbuFxWg</a>
	 */
	private static class Asm4CompatClassVisitor extends ClassVisitor {
		public Asm4CompatClassVisitor(ClassVisitor classVisitor) {
			super(Opcodes.ASM4, classVisitor);
		}
		
		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			return null; //No! I don't want that.
		}
		
		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return new FieldVisitor(Opcodes.ASM4, super.visitField(access, name, descriptor, signature, value)) {
				@Override
				public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
					return null; //No! I don't want that.
				}
			};
		}
		
		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, name, descriptor, signature, exceptions)) {
				@Override
				public void visitParameter(String name, int access) {
					//No! I don't want that.
				}
				
				@Override
				public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
					return null; //No! I don't want that.
				}
				
				@Override
				public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
					return null; //No! I don't want that.
				}
				
				@Override
				public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
					return null; //No! I don't want that.
				}
				
				@Override
				public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
					return null; //No! I don't want that.
				}
			};
		}
	}
}

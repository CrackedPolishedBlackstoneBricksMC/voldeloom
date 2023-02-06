package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.stitch.merge.JarMerger;
import org.gradle.api.Project;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.inject.Inject;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Merges the Minecraft client jar and server jar into a sinlge -merged jar. When a member is available in only one jar,
 * a {@code @SideOnly} annotation is attached to the corresponding member in the merged jar.
 * <p>
 * The merging tool used is FabricMC's JarMerger. This always leaves Fabric's {@code @Environment} annotations on the single-sided artifacts.
 * It's hardcoded, so a postprocessing step is used to change those into Forge's {@code @SideOnly} annotations.
 * <p>
 * The merged (and postprocessed) jar is available with {@code getMergedJar()}.
 */
public class MergedProvider extends DependencyProvider {
	@Inject
	public MergedProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc) {
		super(project, extension);
		this.mc = mc;
	}
	
	private final MinecraftProvider mc;
	
	private Path mergedUnfixed;
	private Path merged;
	
	public void decorateProject() throws Exception {
		//inputs
		Path client = mc.getClientJar();
		Path server = mc.getServerJar();
		String version = mc.getVersion();
		
		//outputs
		Path userCache = WellKnownLocations.getUserCache(project);
		merged = userCache.resolve("minecraft-" + version + "-merged.jar");
		mergedUnfixed = userCache.resolve("minecraft-" + version + "-merged-unfixed.jar");
		cleanIfRefreshDependencies();
		
		//execution
		project.getLogger().lifecycle("] merged-unfixed jar is at: " + mergedUnfixed);
		if(Files.notExists(mergedUnfixed)) {
			project.getLogger().lifecycle("|-> Does not exist, performing merge...");
			
			try(JarMerger jm = new JarMerger(client.toFile(), server.toFile(), mergedUnfixed.toFile())) {
				jm.enableSyntheticParamsOffset();
				jm.merge();
			}
			
			project.getLogger().lifecycle("|-> Merge success! :)");
		}
		
		project.getLogger().lifecycle("] merged-fixed jar is at: " + merged);
		if(Files.notExists(merged)) {
			project.getLogger().lifecycle("|-> Does not exist, performing annotation remap...");
			
			try(FileSystem unfixedFs = FileSystems.newFileSystem(URI.create("jar:" + mergedUnfixed.toUri()), Collections.emptyMap());
			    FileSystem fixedFs = FileSystems.newFileSystem(URI.create("jar:" + merged.toUri()), Collections.singletonMap("create", "true")))
			{
				Files.walkFileTree(unfixedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path unfixedDir, BasicFileAttributes attrs) throws IOException {
						Path fixedDir = fixedFs.getPath(unfixedDir.toString());
						Files.createDirectories(fixedDir);
						return FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult visitFile(Path unfixedFile, BasicFileAttributes attrs) throws IOException {
						Path fixedFile = fixedFs.getPath(unfixedFile.toString());
						if(unfixedFile.toString().endsWith(".class")) {
							try(InputStream unfixedReader = new BufferedInputStream(Files.newInputStream(unfixedFile))) {
								ClassReader unfixedClassReader = new ClassReader(unfixedReader);
								ClassWriter fixedClassWriter = new ClassWriter(0);
								unfixedClassReader.accept(new EnvironmentToSideOnlyVisitor(fixedClassWriter), 0);
								Files.write(fixedFile, fixedClassWriter.toByteArray());
							}
						} else {
							Files.copy(unfixedFile, fixedFile);
						}
						
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			project.getLogger().lifecycle("|-> Annotation remap success! :)");
		}
		
		installed = true;
	}
	
	public Path getMergedJar() {
		return merged;
	}
	
	//I deleted this class but pasted it back from upstream Voldeloom, so, this is all stuff figured out by TwilightFlower. Thanks!
	private static class EnvironmentToSideOnlyVisitor extends ClassVisitor implements Opcodes {
		private static final String FABRIC_ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
		private static final String FORGE_SIDEONLY = "Lcpw/mods/fml/relauncher/SideOnly;";
		
		private static final String FABRIC_ENVTYPE = "Lnet/fabricmc/api/EnvType;";
		private static final String FORGE_SIDE = "Lcpw/mods/fml/relauncher/Side;";
		
		public EnvironmentToSideOnlyVisitor(ClassVisitor classVisitor) {
			super(ASM4, classVisitor);
		}
		
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if(descriptor.equals(FABRIC_ENVIRONMENT)) {
				return new EnvFixAnnotationVisitor(visitAnnotation(FORGE_SIDEONLY, visible));
			} else {
				return super.visitAnnotation(descriptor, visible);
			}
		}
		
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new EnvFixMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions));
		}
		
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return new EnvFixFieldVisitor(super.visitField(access, name, descriptor, signature, value));
		}
		
		private static class EnvFixAnnotationVisitor extends AnnotationVisitor {
			public EnvFixAnnotationVisitor(AnnotationVisitor annotationVisitor) {
				super(ASM4, annotationVisitor);
			}
			
			public void visitEnum(String name, String descriptor, String value) {
				// both forge and fabric use SERVER and CLIENT so no value change necessary
				if(descriptor.equals(FABRIC_ENVTYPE)) {
					super.visitEnum(name, FORGE_SIDE, value); 
				} else {
					super.visitEnum(name, descriptor, value);
				}
			}
		}
		
		private static class EnvFixMethodVisitor extends MethodVisitor {
			public EnvFixMethodVisitor(MethodVisitor methodVisitor) {
				super(Opcodes.ASM7, methodVisitor);
			}
			
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				if(descriptor.equals(FABRIC_ENVIRONMENT)) {
					return new EnvFixAnnotationVisitor(visitAnnotation(FORGE_SIDEONLY, visible));
				} else {
					return super.visitAnnotation(descriptor, visible);
				}
			}
		}
		
		private static class EnvFixFieldVisitor extends FieldVisitor {
			public EnvFixFieldVisitor(FieldVisitor fieldVisitor) {
				super(Opcodes.ASM7, fieldVisitor);
			}
			
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				if(descriptor.equals(FABRIC_ENVIRONMENT)) {
					return new EnvFixAnnotationVisitor(visitAnnotation(FORGE_SIDEONLY, visible));
				} else {
					return super.visitAnnotation(descriptor, visible);
				}
			}
		}
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Arrays.asList(merged, mergedUnfixed);
	}
}

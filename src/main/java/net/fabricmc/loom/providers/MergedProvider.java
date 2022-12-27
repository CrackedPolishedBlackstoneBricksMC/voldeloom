package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.WellKnownLocations;
import net.fabricmc.stitch.merge.JarMerger;
import org.gradle.api.Project;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
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

public class MergedProvider extends DependencyProvider {
	public MergedProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc) {
		super(project, extension);
		this.mc = mc;
	}
	
	private final MinecraftProvider mc;
	
	private File mergedUnfixed;
	private File merged;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		File client = mc.getClientJar();
		File server = mc.getServerJar();
		String version = mc.getVersion();
		
		//outputs
		File userCache = WellKnownLocations.getUserCache(project);
		merged = new File(userCache, "minecraft-" + version + "-merged.jar");
		mergedUnfixed = new File(userCache, "minecraft-" + version + "-merged-unfixed.jar");
		
		//execution
		project.getLogger().lifecycle("] merged-unfixed jar is at: " + mergedUnfixed);
		if(!mergedUnfixed.exists()) {
			project.getLogger().lifecycle("|-> Does not exist, performing merge...");
			
			try(JarMerger jm = new JarMerger(client, server, mergedUnfixed)) {
				jm.enableSyntheticParamsOffset();
				jm.merge();
			}
			
			project.getLogger().lifecycle("|-> Merge success! :)");
		}
		
		project.getLogger().lifecycle("] merged-fixed jar is at: " + merged);
		if(!merged.exists()) {
			project.getLogger().lifecycle("|-> Does not exist, performing annotation remap...");
			
			try(FileSystem unfixedFs = FileSystems.newFileSystem(URI.create("jar:" + mergedUnfixed.toURI()), Collections.emptyMap());
			    FileSystem fixedFs = FileSystems.newFileSystem(URI.create("jar:" + merged.toURI()), Collections.singletonMap("create", "true")))
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
							ClassReader unfixedReader = new ClassReader(Files.newInputStream(unfixedFile));
							ClassWriter fixedWriter = new ClassWriter(0);
							unfixedReader.accept(new EnvironmentToSideOnlyVisitor(fixedWriter), 0);
							Files.write(fixedFile, fixedWriter.toByteArray());
						} else {
							Files.copy(unfixedFile, fixedFile);
						}
						
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			project.getLogger().lifecycle("|-> Annotation remap success! :)");
		}
	}
	
	public File getMergedJar() {
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
					return new EnvFixAnnotationVisitor(visitAnnotation(FORGE_SIDE, visible));
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
					return new EnvFixAnnotationVisitor(visitAnnotation(FORGE_SIDE, visible));
				} else {
					return super.visitAnnotation(descriptor, visible);
				}
			}
		}
	}
}

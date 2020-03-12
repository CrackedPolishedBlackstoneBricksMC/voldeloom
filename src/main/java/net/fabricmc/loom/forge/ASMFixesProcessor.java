package net.fabricmc.loom.forge;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;

import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.forge.asm.ATClassVisitor;
import net.fabricmc.loom.forge.asm.EnvironmentAnnotationFixClassVisitor;
import net.fabricmc.loom.forge.provider.ForgeProvider;
import net.fabricmc.loom.processors.JarProcessor;

public class ASMFixesProcessor implements JarProcessor {

	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	
	private LoomGradleExtension extension;
	
	@Override
	public void setup(Project project, LoomGradleExtension extension) {
		this.extension = extension;
	}

	@Override
	public void process(File file) {
		ForgeProvider forge = extension.getDependencyManager().getProvider(ForgeProvider.class);
		try {
			forge.mapForge();
		} catch (IOException e1) {
			throw new RuntimeException("Problem remapping ATs", e1);
		}
		ForgeATConfig atConfig = forge.getATs();
		try(FileSystem mcFs = FileSystems.newFileSystem(URI.create("jar:" + file.toURI()), FS_ENV)) {
			Files.walk(mcFs.getPath("/"))
				.filter(p -> p.toString().endsWith(".class"))
				.forEach(p -> transformClass(p, atConfig));
		} catch (IOException e) {
			throw new RuntimeException("Exception applying ATs", e);
		}
	}

	private void transformClass(Path p, ForgeATConfig atConfig) {
		try(InputStream input = new BufferedInputStream(Files.newInputStream(p))) {
			ClassReader classReader = new ClassReader(input);
			ClassWriter classWriter = new ClassWriter(0);
			classReader.accept(new EnvironmentAnnotationFixClassVisitor(new ATClassVisitor(classWriter, atConfig)), 0);
			input.close();
			byte[] clazz = classWriter.toByteArray();
			Files.copy(new ByteArrayInputStream(clazz), p, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("Error applying ATs", e);
		}
	}
	
	@Override
	public boolean isInvalid(File file) {
		return false;
	}

}

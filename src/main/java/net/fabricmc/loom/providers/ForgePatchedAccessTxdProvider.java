package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.ForgeAccessTransformerSet;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

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

public class ForgePatchedAccessTxdProvider extends DependencyProvider {
	public ForgePatchedAccessTxdProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc, ForgeProvider forge, ForgePatchedProvider forgePatched) {
		super(project, extension);
		this.mc = mc;
		this.forge = forge;
		this.forgePatched = forgePatched;
	}
	
	private final MinecraftProvider mc;
	private final ForgeProvider forge;
	private final ForgePatchedProvider forgePatched;
	
	private File accessTransformedMc;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		String jarStuff = mc.getJarStuff();
		ForgeAccessTransformerSet unmappedAts = forge.getUnmappedAccessTransformers();
		File unAccessTransformedMc = forgePatched.getPatchedJar();
		
		//outputs
		File userCache = WellKnownLocations.getUserCache(project);
		accessTransformedMc = new File(userCache, "minecraft-" + jarStuff + "-atd.jar");
		
		//task
		project.getLogger().lifecycle("] access-transformed jar is at: " + accessTransformedMc);
		if(!accessTransformedMc.exists()) {
			project.getLogger().lifecycle("|-> Does not exist, performing access transform...");
			
			try(
				FileSystem unAccessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + unAccessTransformedMc.toURI()), Collections.emptyMap());
				FileSystem accessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + accessTransformedMc.toURI()), Collections.singletonMap("create", "true")))
			{
				Files.walkFileTree(unAccessTransformedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path srcPath, BasicFileAttributes attrs) throws IOException {
						Path dstPath = accessTransformedFs.getPath(srcPath.toString());
						Files.createDirectories(dstPath.getParent());
						
						if(srcPath.toString().endsWith(".class")) {
							ClassReader srcReader = new ClassReader(Files.newInputStream(srcPath));
							ClassWriter dstWriter = new ClassWriter(0);
							srcReader.accept(unmappedAts.new AccessTransformingClassVisitor(dstWriter), 0);
							Files.write(dstPath, dstWriter.toByteArray());
						} else {
							Files.copy(srcPath, dstPath);
						}
						
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			project.getLogger().lifecycle("|-> Access transformation success! :)");
		}
	}
	
	public File getTransformedJar() {
		return accessTransformedMc;
	}
}

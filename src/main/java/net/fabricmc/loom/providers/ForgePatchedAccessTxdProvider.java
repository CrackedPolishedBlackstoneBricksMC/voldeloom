package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.mcp.ForgeAccessTransformerSet;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

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
import java.util.List;

/**
 * Applies Forge's access transformers on top of the patched Minecraft + Forge jar.
 * The access-transformed jar is accessible with {@code getTransformedJar()}.
 * <p>
 * Outside of development, this is normally done by Forge as it classloads Minecraft.
 */
public class ForgePatchedAccessTxdProvider extends DependencyProvider {
	public ForgePatchedAccessTxdProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path accessTransformedMc;
	
	public void decorateProject(ForgeProvider forge, ForgePatchedProvider forgePatched) throws Exception {
		//inputs
		Path forgeJar = forge.getJar();
		Path unAccessTransformedMc = forgePatched.getPatchedJar();
		String patchedVersionTag = forgePatched.getPatchedVersionTag();
		
		//outputs
		Path userCache = WellKnownLocations.getUserCache(project);
		accessTransformedMc = userCache.resolve("minecraft-" + patchedVersionTag + "-atd.jar");
		cleanIfRefreshDependencies();
		
		//task
		project.getLogger().lifecycle("] access-transformed jar is at: {}", accessTransformedMc);
		if(Files.notExists(accessTransformedMc)) {
			project.getLogger().lifecycle("|-> Does not exist, parsing Forge's access transformers...");
			
			//Read forge ats
			ForgeAccessTransformerSet ats = new ForgeAccessTransformerSet();
			try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forgeJar.toUri()), Collections.emptyMap())) {
				//TODO: where do these names come from, can they be read from the jar?
				// 1.2.5 does not have these files
				for(String atFileName : Arrays.asList("forge_at.cfg", "fml_at.cfg")) {
					Path atFilePath = forgeFs.getPath(atFileName);
					if(Files.exists(atFilePath)) {
						project.getLogger().info("|-> Loading {}...", atFileName);
						try(InputStream atIn = new BufferedInputStream(Files.newInputStream(atFilePath))) {
							ats.load(atIn);
						}
					} else {
						project.getLogger().info("|-> No {} in the Forge jar.", atFileName);
					}
				}
			}
			
			project.getLogger().lifecycle("|-> Found {} access transformers. Performing transform...", ats.getCount());
			
			try(
				FileSystem unAccessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + unAccessTransformedMc.toUri()), Collections.emptyMap());
				FileSystem accessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + accessTransformedMc.toUri()), Collections.singletonMap("create", "true")))
			{
				Files.walkFileTree(unAccessTransformedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path srcPath, BasicFileAttributes attrs) throws IOException {
						Path dstPath = accessTransformedFs.getPath(srcPath.toString());
						Files.createDirectories(dstPath.getParent());
						
						if(srcPath.toString().endsWith(".class")) {
							try(InputStream srcReader = new BufferedInputStream((Files.newInputStream(srcPath)))) {
								ClassReader srcClassReader = new ClassReader(srcReader);
								ClassWriter dstClassWriter = new ClassWriter(0);
								srcClassReader.accept(ats.new AccessTransformingClassVisitor(dstClassWriter), 0);
								Files.write(dstPath, dstClassWriter.toByteArray());
							}
						} else {
							Files.copy(srcPath, dstPath);
						}
						
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			project.getLogger().lifecycle("|-> Access transformation success! :)");
			
			List<String> unusedAtsReport = ats.reportUnusedTransformers();
			if(unusedAtsReport.isEmpty()) {
				project.getLogger().warn("|-> Found {} unused access transformers.", unusedAtsReport.size());
				unusedAtsReport.forEach(project.getLogger()::warn);
			}
		}
		
		installed = true;
	}
	
	public Path getTransformedJar() {
		return accessTransformedMc;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.singleton(accessTransformedMc);
	}
}

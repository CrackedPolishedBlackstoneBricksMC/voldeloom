package net.fabricmc.loom.forge;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MinecraftMergedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.providers.DependencyProvider;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

public class MinecraftForgePatchedProvider extends DependencyProvider {
	public MinecraftForgePatchedProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private File patched;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		MinecraftProvider minecraftProvider = extension.getDependencyManager().getMinecraftProvider();
		String jarStuff = minecraftProvider.getJarStuff();
		
		MinecraftMergedProvider mergedProvider = extension.getDependencyManager().getMinecraftMergedProvider();
		File merged = mergedProvider.getMergedJar();
		
		ForgeProvider forgeProvider = extension.getDependencyManager().getForgeProvider();
		File forge = forgeProvider.getForge();
		
		//outputs
		File userCache = WellKnownLocations.getUserCache(project);
		patched = new File(userCache, "minecraft-" + jarStuff + "-merged.jar");
		
		//execution
		if(!patched.exists()) {
			project.getLogger().lifecycle("|-> Patching merged Minecraft with Forge, result goes at " + patched);
			
			try(
				FileSystem mergedFs = FileSystems.newFileSystem(URI.create("jar:" + merged.toURI()), Collections.emptyMap());
				FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toURI()), Collections.emptyMap());
				FileSystem patchedFs = FileSystems.newFileSystem(URI.create("jar:" + patched.toURI()), Collections.singletonMap("create", "true")))
			{
				//Copy the contents of the mc-merged jar into the patched jar
				Files.walkFileTree(mergedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
						return dir.endsWith("META-INF") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
						Path destPath = patchedFs.getPath(sourcePath.toString());
						Files.createDirectories(destPath.getParent());
						Files.copy(sourcePath, destPath);
						return FileVisitResult.CONTINUE;
					}
				});
				
				//Copy the contents of the Forge jar on top
				Files.walkFileTree(forgeFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
						return dir.endsWith("META-INF") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
						Path destPath = patchedFs.getPath(sourcePath.toString());
						Files.createDirectories(destPath.getParent());
						Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
						return FileVisitResult.CONTINUE;
					}
				});
				
				//No need to delete META-INF cause i didn't copy it in the first place ;)
			}
			
			project.getLogger().lifecycle("|-> Patch success! :)");
		}
	}
	
	public File getPatchedJar() {
		return patched;
	}
}

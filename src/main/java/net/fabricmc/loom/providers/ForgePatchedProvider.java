package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
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

public class ForgePatchedProvider extends DependencyProvider {
	public ForgePatchedProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc, MergedProvider merged, ForgeProvider forge) {
		super(project, extension);
		this.mc = mc;
		this.merged = merged;
		this.forge = forge;
	}
	
	private final MinecraftProvider mc;
	private final MergedProvider merged;
	private final ForgeProvider forge;
	
	private File patched;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		String jarStuff = mc.getJarStuff();
		File mergedJar = merged.getMergedJar();
		File forgeJar = forge.getJar();
		
		//outputs
		File userCache = WellKnownLocations.getUserCache(project);
		patched = new File(userCache, "minecraft-" + jarStuff + "-merged.jar");
		
		//execution
		project.getLogger().lifecycle("] patched jar is at: " + patched);
		if(!patched.exists()) {
			project.getLogger().lifecycle("|-> Does not exist, performing patch...");
			
			try(
				FileSystem mergedFs = FileSystems.newFileSystem(URI.create("jar:" + mergedJar.toURI()), Collections.emptyMap());
				FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forgeJar.toURI()), Collections.emptyMap());
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
						if(dir.endsWith("META-INF")) return FileVisitResult.SKIP_SUBTREE;
						else return FileVisitResult.CONTINUE;
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
			
			project.getLogger().lifecycle("|-> Patch success!");
		}
	}
	
	public File getPatchedJar() {
		return patched;
	}
}

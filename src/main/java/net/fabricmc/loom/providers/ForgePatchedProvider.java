package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.task.CleaningTask;
import org.gradle.api.Project;

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
import java.util.Collection;
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
	
	private Path patched;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		String jarStuff = mc.getJarStuff();
		Path mergedJar = merged.getMergedJar();
		Path forgeJar = forge.getJar();
		
		//outputs
		Path userCache = WellKnownLocations.getUserCache(project);
		patched = userCache.resolve("minecraft-" + jarStuff + "-merged.jar");
		
		//execution
		project.getLogger().lifecycle("] patched jar is at: " + patched);
		if(Files.notExists(patched)) {
			project.getLogger().lifecycle("|-> Does not exist, performing patch...");
			
			try(
				FileSystem mergedFs = FileSystems.newFileSystem(URI.create("jar:" + mergedJar.toUri()), Collections.emptyMap());
				FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forgeJar.toUri()), Collections.emptyMap());
				FileSystem patchedFs = FileSystems.newFileSystem(URI.create("jar:" + patched.toUri()), Collections.singletonMap("create", "true")))
			{
				//Copy the contents of the mc-merged jar into the patched jar
				Files.walkFileTree(mergedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path sourceDir, BasicFileAttributes attrs) throws IOException {
						if(sourceDir.endsWith("META-INF")) {
							return FileVisitResult.SKIP_SUBTREE;
						} else {
							Path destDir = patchedFs.getPath(sourceDir.toString());
							Files.createDirectories(destDir);
							return FileVisitResult.CONTINUE;
						}
					}
					
					@Override
					public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
						Path destPath = patchedFs.getPath(sourcePath.toString());
						Files.copy(sourcePath, destPath);
						return FileVisitResult.CONTINUE;
					}
				});
				
				//Copy the contents of the Forge jar on top
				Files.walkFileTree(forgeFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path sourceDir, BasicFileAttributes attrs) throws IOException {
						if(sourceDir.endsWith("META-INF")) {
							return FileVisitResult.SKIP_SUBTREE;
						} else {
							Path destDir = patchedFs.getPath(sourceDir.toString());
							Files.createDirectories(destDir);
							return FileVisitResult.CONTINUE;
						}
					}
					
					@Override
					public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
						Path destPath = patchedFs.getPath(sourcePath.toString());
						Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
						return FileVisitResult.CONTINUE;
					}
				});
				
				//No need to delete META-INF cause i didn't copy it in the first place ;)
			}
			
			project.getLogger().lifecycle("|-> Patch success!");
		}
	}
	
	public Path getPatchedJar() {
		return patched;
	}
	
	public static class ForgePatchedCleaningTask extends CleaningTask {
		@Override
		public Collection<Path> locationsToDelete() {
			ForgePatchedProvider prov = getLoomGradleExtension().getDependencyManager().getForgePatchedProvider();
			return Collections.singleton(prov.getPatchedJar());
		}
	}
}

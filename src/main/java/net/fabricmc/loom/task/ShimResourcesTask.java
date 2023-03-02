package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RunConfig;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Gradle task that copies Minecraft's assets into place.
 */
//TODO: extend AbstractCopyTask once i figure that out, instead of DefaultTask
public class ShimResourcesTask extends DefaultTask implements LoomTaskExt {
	public ShimResourcesTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Copies Minecraft's assets into the folder where the client expects to find them, since it tries to download them from a long-dead S3 bucket at game startup, and this version of the game does not support an --assetIndex parameter.");
		getOutputs().upToDateWhen(__ -> getResourceTargetDirectories().stream().allMatch(Files::exists));
	}
	
	@OutputDirectories
	public Collection<Path> getResourceTargetDirectories() {
		return getLoomGradleExtension().runConfigs.stream()
			.filter(cfg -> "client".equals(cfg.getEnvironment()))
			.map(RunConfig::resolveRunDir)
			.map(getLoomGradleExtension().forgeCapabilities.minecraftRealPath.get())
			.map(p -> p.resolve("resources"))
			.collect(Collectors.toList());
	}
	
	@TaskAction
	public void doIt() throws Exception {
		LoomGradleExtension ext = getLoomGradleExtension();
		
		Path resourceSourceDirectory = ext.getProviderGraph()
			.assets
			.downloadAssets() //<-- actually download them now
			.getAssetsDir();
		
		//TODO: unplug the task entirely, instead of early exiting
		if(ext.forgeCapabilities.supportsAssetsDir.get()) {
			getLogger().lifecycle("Kinda skipping ShimResourcesTask copying because the game supports --assetsDir");
			return;
		}
		
		for(Path resourceTargetDirectory : getResourceTargetDirectories()) {
			Files.walkFileTree(resourceSourceDirectory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path sourceDirPath, BasicFileAttributes attrs) throws IOException {
					Path targetDirPath = resourceTargetDirectory.resolve(resourceSourceDirectory.relativize(sourceDirPath));
					Files.createDirectories(targetDirPath);
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path sourceFilePath, BasicFileAttributes attrs) throws IOException {
					if(sourceFilePath.toString().endsWith(".etag")) return FileVisitResult.CONTINUE;
					
					Path targetFilePath = resourceTargetDirectory.resolve(resourceSourceDirectory.relativize(sourceFilePath));
					Files.copy(sourceFilePath, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
}

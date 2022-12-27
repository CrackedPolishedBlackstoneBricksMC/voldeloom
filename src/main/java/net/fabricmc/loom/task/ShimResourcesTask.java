package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.AssetsProvider;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

//TODO: extend AbstractCopyTask once i figure that out, instead of DefaultTask
public class ShimResourcesTask extends DefaultTask implements LoomTaskExt {
	public ShimResourcesTask() {
		setGroup("fabric");
		getOutputs().upToDateWhen(__ -> Files.exists(getResourceTargetDirectory()));
	}
	
	@OutputDirectory
	public Path getResourceTargetDirectory() {
		LoomGradleExtension ext = getLoomGradleExtension();
		File runDir = new File(getProject().getRootDir(), ext.runDir); //see AbstractRunTask, TODO factor this out
		return runDir.toPath().resolve("resources");
	}
	
	@TaskAction
	public void doIt() throws Exception {
		LoomGradleExtension ext = getLoomGradleExtension();
		AssetsProvider assets = ext.getDependencyManager().getAssetsProvider();
		
		Path resourceSourceDirectory = assets.getAssetsDir().toPath();
		Path resourceTargetDirectory = getResourceTargetDirectory();
		
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
				Files.copy(sourceFilePath, targetFilePath);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}

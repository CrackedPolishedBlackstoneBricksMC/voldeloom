package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gradle task that copies Forge's runtime-downloaded libraries into place.
 */
public class ShimForgeLibrariesTask extends DefaultTask implements LoomTaskExt {
	public ShimForgeLibrariesTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Copies Forge's runtime-downloaded dependencies into the folder where Forge expects to find them, because Forge tries to download dependencies at game startup, and the server it downloads them from is long-dead.");
		
		getOutputs().upToDateWhen(__ -> {
			for(Path libDir : getLibraryDirectories()) {
				for(File lib : getLibs()) {
					if(Files.notExists(libDir.resolve(lib.getName()))) return false;
				}
			}
			return true;
		});
	}
	
	@OutputDirectories
	public Collection<Path> getLibraryDirectories() {
		return getLoomGradleExtension().runConfigs.stream().map(cfg -> cfg.resolveRunDir().resolve("lib")).collect(Collectors.toList());
	}
	
	private Set<File> getLibs() {
		return getProject().getConfigurations().getByName(Constants.FORGE_DEPENDENCIES).getFiles();
	}
	
	@TaskAction
	public void shimLibraries() throws IOException {
		for(Path forgeLibsDir : getLibraryDirectories()) {
			Files.createDirectories(forgeLibsDir);
			
			for(File lib : getLibs()) {
				Path libPath = lib.toPath();
				Files.copy(libPath, forgeLibsDir.resolve(libPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}

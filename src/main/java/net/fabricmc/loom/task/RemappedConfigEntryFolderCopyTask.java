package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.RunConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Gradle task that copies and pastes coremods into the Forge `coremods` folder, because Forge doesn't support reading them off the classpath.
 */
public class RemappedConfigEntryFolderCopyTask extends DefaultTask implements LoomTaskExt {
	public RemappedConfigEntryFolderCopyTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Copies and pastes coremods into the Forge `coremods` folder, because this version of Minecraft Forge only loads coremods out of a very specific folder, `.minecraft/coremods`, and not from the classpath.");
		getOutputs().upToDateWhen(__ -> false); //TODO
	}
	
	@Internal
	public Collection<Path> getRunDirectories() {
		return getLoomGradleExtension().runConfigs.stream().map(RunConfig::resolveRunDir).collect(Collectors.toList());
	}
	
	@TaskAction
	public void doIt() throws IOException {
		for(RemappedConfigurationEntry entry : getLoomGradleExtension().remappedConfigurationEntries) {
			String copyToFolder = entry.getCopyToFolder();
			if(copyToFolder == null || copyToFolder.isEmpty()) continue;
			
			for(File file : entry.getOutputConfig().getFiles()) {
				Path path = file.toPath();
				for(Path runDir : getRunDirectories()) {
					Path copyDest = runDir.resolve(copyToFolder);
					
					Files.createDirectories(copyDest);
					Files.copy(path, copyDest.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}
}

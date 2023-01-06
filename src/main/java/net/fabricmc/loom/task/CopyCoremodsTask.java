package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.util.RemappedConfigurationEntry;
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

public class CopyCoremodsTask extends DefaultTask implements LoomTaskExt {
	public CopyCoremodsTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Copies and pastes coremods into the Forge `coremods` folder, because this version of Minecraft Forge only loads coremods out of a very specific folder, `.minecraft/coremods`, and not from the classpath.");
		getOutputs().upToDateWhen(__ -> false); //TODO
	}
	
	@Internal
	public Collection<Path> getCoremodDirectories() {
		return getLoomGradleExtension().runConfigs.stream().map(cfg -> cfg.resolveRunDir().resolve("coremods")).collect(Collectors.toList());
	}
	
	@TaskAction
	public void doIt() throws IOException {
		for(RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			if(!entry.isCoremod()) continue;
			
			for(File file : entry.getOrCreateRemappedConfiguration(getProject().getConfigurations()).getFiles()) {
				Path path = file.toPath();
				for(Path coremodDir : getCoremodDirectories()) {
					Files.createDirectories(coremodDir);
					Files.copy(path, coremodDir.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}
}

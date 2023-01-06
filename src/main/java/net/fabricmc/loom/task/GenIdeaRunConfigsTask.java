package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.util.RunConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GenIdeaRunConfigsTask extends DefaultTask implements LoomTaskExt {
	@TaskAction
	public void doIt() throws Exception {
		Project project = getProject();
		LoomGradleExtension extension = getLoomGradleExtension();
		
		if(project != project.getRootProject()) {
			project.getLogger().lifecycle("Project " + project + " != root project " + project.getRootProject());
			return;
		}
		
		Path ideaDir = project.file(".idea").toPath();
		Path runConfigsDir = ideaDir.resolve("runConfigurations");
		Files.createDirectories(runConfigsDir);
		
		for(RunConfig cfg : extension.runConfigs) {
			if(!cfg.isIdeConfigGenerated()) continue;
			
			RunConfig cooked = cfg.cook(extension);
			
			Path cfgFile = runConfigsDir.resolve(cooked.getBaseName() + ".xml");
			if(Files.notExists(cfgFile)) {
				Files.write(cfgFile, cooked.configureTemplate("idea_run_config_template.xml").getBytes(StandardCharsets.UTF_8));
			}
			Files.createDirectories(cooked.resolveRunDir());
		}
	}
}

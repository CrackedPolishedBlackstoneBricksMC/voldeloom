package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public abstract class ConfigurationDebugTask extends DefaultTask implements LoomTaskExt {
	public ConfigurationDebugTask() {
		setGroup(Constants.TASK_GROUP_TOOLS);
		setDescription("Prints some information about the Configurations used in the project and their relationships between each other.");
		getOutputs().upToDateWhen(__ -> false);
	}
	
	@TaskAction
	public void doIt() {
		Logger l = getLogger();
		
		l.lifecycle(" -- CONFIGURATION INHERITANCE GRAPH (paste into a graphviz viewer like https://edotor.net/) --");
		l.lifecycle("digraph Configs {");
		l.lifecycle("  scale=2; //makes neato look less crap");
		l.lifecycle("  rankdir=LR; //makes dot look less crap");
		l.lifecycle("  node[shape=\"rect\"]; //tightens up dot a bit");
		l.lifecycle("  splines=false; //reduces dot spaghetti");
		l.lifecycle("  ");
		
		for(Configuration configuration : getProject().getConfigurations()) {
			l.lifecycle("  " + configuration.getName());
			for(Configuration superConfig : configuration.getExtendsFrom()) {
				l.lifecycle("  " + superConfig.getName() + " -> " + configuration.getName());
			}
			
			for(Dependency dep : configuration.getDependencies()) {
				if(dep instanceof SelfResolvingDependency) {
					for(File file : ((SelfResolvingDependency) dep).resolve()) {
						String bla = file.getAbsolutePath().replace('\\', '/');
						l.lifecycle("  \"" + bla + "\" [color=\"#FF0044\"]");
						l.lifecycle("  \"" + bla + "\" -> " + configuration.getName());
					}
				} else {
					String bla = dep.getGroup() + ":" + dep.getName() + ":" + dep.getVersion();
					l.lifecycle("  \"" + bla + "\" [color=\"#FF0000\"]");
					l.lifecycle("  \"" + bla + "\" -> " + configuration.getName());
				}
			}
		}
		
		//draw arrows to represent RemappedDependenciesProvider's operation
		for(RemappedConfigurationEntry hm : getLoomGradleExtension().remappedConfigurationEntries) {
			l.lifecycle("  " + hm.inputConfig.getName() + " -> " + hm.outputConfig.getName() + " [color=\"#CC8800\",style=dashed]");
		}
		
		l.lifecycle("}");
		
		l.lifecycle(" -- CONFIGURATION CONTENTS --");
		for(Configuration configuration : getProject().getConfigurations()) {
			if(!configuration.getDependencies().isEmpty()) {
				l.lifecycle("Configuration " + configuration.getName() + " contains " + configuration.getDependencies().size() + " dependencies:");
				for(Dependency dep : configuration.getDependencies()) {
					l.lifecycle(" - " + dep.getGroup() + ":" + dep.getName() + ":" + dep.getVersion());
					if(dep instanceof SelfResolvingDependency) {
						for(File file : ((SelfResolvingDependency) dep).resolve()) {
							l.lifecycle("  \\-> " + file.getAbsolutePath());
						}
					}
				}
			}
		}
	}
}

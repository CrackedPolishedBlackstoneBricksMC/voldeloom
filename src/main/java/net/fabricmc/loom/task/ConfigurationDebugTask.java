package net.fabricmc.loom.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public abstract class ConfigurationDebugTask extends DefaultTask {
	public ConfigurationDebugTask() {
		setGroup("fabric-debug");
		getOutputs().upToDateWhen(__ -> false);
	}
	
	@TaskAction
	public void doIt() {
		Logger l = getLogger();
		
		l.lifecycle(" -- CONFIGURATION INHERITANCE GRAPH (paste into a graphviz viewer like https://edotor.net/) --");
		l.lifecycle("digraph Configs {");
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
		
//		for(RemappedConfigurationEntry hm : Constants.MOD_COMPILE_ENTRIES) {
//			l.lifecycle("  " + hm.getSourceConfiguration() + " -> " + hm.getTargetConfiguration() + " [color=\"#ffcc00\"]");
//			l.lifecycle("  " + hm.getSourceConfiguration() + " -> " + hm.getRemappedConfiguration() + " [color=\"#00ffcc\"]");
//		}
		
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

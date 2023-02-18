package net.fabricmc.loom.util;

import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Internal;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;

/**
 * Syntax sugar.
 * 
 * TODO: probably remove this. Formerly was used as a containment mechanism to find out where various areas of the code were using DependencyProviders.
 */
public interface LoomTaskExt extends Task {
	@Internal
	default LoomGradleExtension getLoomGradleExtension() {
		return getProject().getExtensions().getByType(LoomGradleExtension.class);
	}
	
	//merged from ForkingJavaExecTask in loom 1
	default ExecResult forkedJavaexec(Action<? super JavaExecSpec> action) {
		//compute the classpath of the current buildscript
		ConfigurationContainer configurations = getProject().getBuildscript().getConfigurations();
		DependencyHandler handler = getProject().getDependencies();
		FileCollection classpath = configurations.getByName("classpath").plus(configurations.detachedConfiguration(handler.localGroovy()));
		
		return getProject().javaexec(spec -> {
			spec.classpath(classpath);
			action.execute(spec);
		});
	}
}

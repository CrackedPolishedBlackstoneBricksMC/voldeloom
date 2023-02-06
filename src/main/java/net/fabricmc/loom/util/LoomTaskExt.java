package net.fabricmc.loom.util;

import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Task;
import org.gradle.api.tasks.Internal;

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
}

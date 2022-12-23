package net.fabricmc.loom.util;

import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Task;
import org.gradle.api.tasks.Internal;

/**
 * (VOLDELOOM-DISASTER)
 * 
 * LoomGradleExtension is very stateful and order-dependent (it can't run before it's set up in the project's afterEvaluate block, etc).
 * You're always allowed to use it in task execution, though, since that happens after afterEvaluate.
 * 
 * To prevent the proliferation of areas of the code grabbing at it willy-nilly, this is an interface that can only be applied to tasks, adds a little helper.
 * Basically this means tasks don't clutter up the Find Usages window when I'm searching around the code for where this utility gets used.
 */
public interface LoomTaskExt extends Task {
	@Internal
	default LoomGradleExtension getLoomGradleExtension() {
		return getProject().getExtensions().getByType(LoomGradleExtension.class);
	}
}

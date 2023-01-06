package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Destroys;
import org.gradle.api.tasks.TaskAction;

import java.nio.file.Path;
import java.util.Collection;

public abstract class CleaningTask extends DefaultTask implements LoomTaskExt {
	public CleaningTask() {
		setGroup("fabric-clean");
	}
	
	@Destroys
	public abstract Collection<Path> locationsToDelete();
	
	@TaskAction
	public void delete() {
		LoomGradlePlugin.delete(getProject(), (Object[]) locationsToDelete().toArray(new Path[0]));
	}
}

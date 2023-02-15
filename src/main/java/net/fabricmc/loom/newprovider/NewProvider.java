package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.WellKnownLocations;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public abstract class NewProvider<SELF extends NewProvider<SELF>> {
	public NewProvider(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.extension = extension;
		
		this.log = project.getLogger();
	}
	
	protected final Project project; //TODO: make it private maybe
	private final LoomGradleExtension extension;
	
	protected final Logger log;
	
	public boolean projectmapped = false;
	
	public SELF superProjectmapped(boolean projectmapped) {
		this.projectmapped |= projectmapped;
		return self();
	}
	
	@SuppressWarnings("unchecked")
	protected SELF self() {
		return (SELF) this;
	}
	
	protected final Path getCacheDir() {
		if(projectmapped) return WellKnownLocations.getProjectCache(project);
		else return WellKnownLocations.getUserCache(project);
	}
	
	
	/**
	 * Delete these paths if the refresh-dependency mode is enabled.
	 * @param paths varargs list of paths
	 */
	public final void cleanOnRefreshDependencies(Path... paths) {
		cleanOnRefreshDependencies(Arrays.asList(paths));
	}
	
	/**
	 * Delete these paths if the refresh-dependency mode is enabled.
	 * @param paths collection of paths
	 */
	public final void cleanOnRefreshDependencies(Collection<Path> paths) {
		if(extension.refreshDependencies) {
			project.getLogger().lifecycle("|-> Deleting outputs of " + getClass().getSimpleName() + " because of refreshDependencies mode");
			LoomGradlePlugin.delete(project, paths);
		}
	}
	
	protected Collection<Path> andEtags(Collection<Path> in) {
		ArrayList<Path> out = new ArrayList<>(in);
		for(Path i : in) {
			out.add(i.resolveSibling(i.getFileName().toString() + ".etag"));
		}
		return out;
	}
}

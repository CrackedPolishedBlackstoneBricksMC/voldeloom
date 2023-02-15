package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.DownloadSession;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
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
	
	protected final Project project;
	private final LoomGradleExtension extension;
	
	protected final Logger log;
	
	public boolean projectmapped = false;
	
	public SELF superProjectmapped(boolean projectmapped) {
		this.projectmapped |= projectmapped;
		return self();
	}
	
	/**
	 * Some support for the curiously recurring template pattern
	 * @return you!
	 */
	@SuppressWarnings("unchecked")
	protected final SELF self() {
		return (SELF) this;
	}
	
	protected final Path getCacheDir() {
		if(projectmapped) return WellKnownLocations.getProjectCache(project);
		else return WellKnownLocations.getUserCache(project);
	}
	
	/**
	 * Delete these paths if the refresh-dependency mode is enabled.
	 * @param paths varargs list of paths to delete
	 */
	protected final void cleanOnRefreshDependencies(Path... paths) {
		cleanOnRefreshDependencies(Arrays.asList(paths));
	}
	
	/**
	 * Delete these paths if the refresh-dependency mode is enabled.
	 * @param paths collection of paths to delete
	 */
	protected final void cleanOnRefreshDependencies(Collection<Path> paths) {
		if(extension.refreshDependencies) {
			log.lifecycle("!! Deleting outputs of " + getClass().getSimpleName() + " because of refreshDependencies mode");
			LoomGradlePlugin.delete(project, paths);
		}
	}
	
	protected final Collection<Path> andEtags(Collection<Path> in) {
		ArrayList<Path> out = new ArrayList<>(in);
		for(Path i : in) {
			out.add(i.resolveSibling(i.getFileName().toString() + ".etag"));
		}
		return out;
	}
	
	//Trying to keep the provider stuff pretty separate from most Gradle wizardry, but I do need to poke a few holes:
	protected final Configuration getConfigurationByName(String name) {
		return project.getConfigurations().getByName(name);
	}
	
	protected final DownloadSession newDownloadSession(String url) {
		return new DownloadSession(url, project);
	}
	
	protected final Path getRemappedModCache() {
		return WellKnownLocations.getRemappedModCache(project);
	}
	
	protected final FileCollection files(Object... paths) {
		return project.files(paths);
	}
}

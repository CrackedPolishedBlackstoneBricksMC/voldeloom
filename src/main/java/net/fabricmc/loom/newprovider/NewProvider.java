package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.ProviderGraph;
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

/**
 * Basically "provider" is a catchall term for "something that has to run in afterEvaluate, because doing it
 * before the buildscript is evaluated is too early to allow the user to it, but doing it during task execution
 * is too late to configure the dependencies of the project".
 * <p>
 * Generally they're "providers" in the sense that they create some jar file, then "provide" it to the
 * project by adding it as a dependency to some configuration. Generalize this to "providing" an in-memory
 * set of mappings, or a boolean saying whether this version of forge has binpatches or not, and that's basically it.
 * <p>
 * it's "New" provider, because this project used to have an old provider system, and it sucked real ass, so
 * in like 5 hours of Hyper focus i erased all of it and replaced it with this much better one :3
 * 
 * @param <SELF> curiously recurring template pattern
 * @see ProviderGraph for where the providers are connected together :3
 */
public abstract class NewProvider<SELF extends NewProvider<SELF>> {
	public NewProvider(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.extension = extension;
		
		this.log = project.getLogger();
	}
	
	private final Project project;
	private final LoomGradleExtension extension;
	
	protected final Logger log;
	
	private boolean projectmapped = false;
	
	//since "projectmapped" is just a bool that can be set either way at any time,
	//this flag gets set to `true` as a safety interlock - after reading the projectmapped
	//flag (and possibly making a decision based off of it) you're not allowed to change it again
	private boolean leveragedProjectmappiness = false;
	
	/**
	 * Whether this provider has been configured in such a way that should disqualify
	 * it from being put in the global Gradle cache, a state known as "being projectmapped".
	 * <p>
	 * I think this term stems from Loom specifically handling cusotm *mappings*, but
	 * I'm sticking with the term even in a more general sense of things other than mappings.
	 */
	public boolean isProjectmapped() {
		leveragedProjectmappiness = true;
		return projectmapped;
	}
	
	@SuppressWarnings("unchecked")
	public SELF setProjectmapped(boolean projectmapped) {
		if(leveragedProjectmappiness) {
			throw new IllegalStateException("Cannot set projectmappiness after already having leveraged it, this might put things in an inconsistent state.");
		}
		
		this.projectmapped |= projectmapped;
		
		return (SELF) this;
	}
	
	/**
	 * Set to {@code true} if a dependent provider is projectmapped. Uses the "curiously recurring
	 * template pattern", so you can interleave this method into the provider's other builder methods.
	 */
	@SuppressWarnings("unchecked")
	public SELF superProjectmapped(boolean projectmapped) {
		this.projectmapped |= projectmapped;
		
		return (SELF) this;
	}
	
	/**
	 * Returns a directory that files can be stashed in. It's project-local if the provider
	 * is projectmapped, and the global per-user gradle cache if not.
	 */
	public final Path getCacheDir() {
		return WellKnownLocations.getCache(project, isProjectmapped());
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

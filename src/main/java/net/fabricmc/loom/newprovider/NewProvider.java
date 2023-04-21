package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.ProviderGraph;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.Props;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
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
	public final Props props = new Props();
	
	//simply for convenience, can be called with a heterogenous array of NewProviders and Props
	@SuppressWarnings("unchecked")
	public SELF superProps(Object... others) {
		for(Object other : others) {
			if(other instanceof NewProvider<?>) props.putAll(((NewProvider<?>) other).props);
			else if(other instanceof Props) props.putAll((Props) other);
			else throw new IllegalArgumentException("Unknown object " + other.getClass() + " passed to superProps");
		}
		return (SELF) this;
	}
	
	/**
	 * If refresh-dependencies mode is enabled, deletes the file or directory at {@code path}.<br>
	 * Then, if {@code path} does not exist, the {@code fileCreator} is invoked with the path as an argument.
	 * This procedure is expected to create a nonempty file or a directory at that location.
	 * Returns {@code path}.
	 */
	protected final Path getOrCreate(Path path, ThrowyConsumer<Path> fileCreator) throws Exception {
		if(extension.refreshDependencies) {
			project.getLogger().warn("Ignoring " + path + " because refresh-dependencies mode is set");
			LoomGradlePlugin.delete(project, path);
		}
		
		if(Files.notExists(path)) {
			project.getLogger().info("Creating file at " + path);
			
			fileCreator.accept(path);
			//check that the file creator actually did create the file, and it's not empty
			if(Files.notExists(path)) throw new IllegalStateException("Runnable " + fileCreator + " should have created a file at " + path);
			if(!Files.isDirectory(path) && Files.size(path) == 0) throw new IllegalStateException("Runnable " + fileCreator + " created a zero-byte file at " + path);
			
			//TODO: write props to a file in user-readable form, just for debugging?
		} else {
			project.getLogger().info("Cache hit at " + path);
		}
		
		return path;
	}
	
	//TODO: reimpl projectmappiness? Shouldn't be too hard, read off the Props
	public final Path getCacheDir() {
		return WellKnownLocations.getUserCache(project);
	}
	
	/**
	 * Delete these paths if the refresh-dependency mode is enabled.
	 * @param paths varargs list of paths to delete
	 * @deprecated Use the getOrCreate method instead if possible  
	 */
	@Deprecated
	protected final void cleanOnRefreshDependencies(Path... paths) {
		cleanOnRefreshDependencies(Arrays.asList(paths));
	}
	
	/**
	 * Delete these paths if the refresh-dependency mode is enabled.
	 * @param paths collection of paths to delete
	 * @deprecated Use the getOrCreate method instead 
	 */
	@Deprecated
	protected final void cleanOnRefreshDependencies(Collection<Path> paths) {
		if(extension.refreshDependencies) {
			log.lifecycle("!! Deleting outputs of " + getClass().getSimpleName() + " because of refreshDependencies mode");
			LoomGradlePlugin.delete(project, paths);
		}
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
	
	///
	
	public interface ThrowyConsumer<T> {
		void accept(T thing) throws Exception;
	}
}

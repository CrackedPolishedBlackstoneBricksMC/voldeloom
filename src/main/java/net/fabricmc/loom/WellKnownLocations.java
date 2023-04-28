package net.fabricmc.loom;

import org.gradle.api.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Various file paths used throughout the plugin. (These used to be on LoomGradleExtension.)
 */
public class WellKnownLocations {
	private static Path mkdirs(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return path;
	}
	
	/**
	 * The user-global cache for Voldeloom items. Used for items that have completely standard dependencies, like
	 * vanilla Minecraft, regular Forge, no custom mappings or access transformers, etc.
	 * @return A path that should only contain artifacts with no special dependencies on the current project.
	 */
	public static Path getUserCache(Project project) {
		return mkdirs(project.getGradle().getGradleUserHomeDir().toPath().resolve("caches").resolve("voldeloom"));
	}
	
	/**
	 * A project-specific cache. Used for items that have some dependency on the current project, like customized
	 * mappings, custom Minecraft version, or user-specified access transformers.
	 * @return A path that may contain artifacts that may contain special dependencies on the current project.
	 */
	public static Path getProjectCache(Project project) {
		return mkdirs(project.file(".gradle").toPath().resolve("voldeloom-cache"));
	}
	
	//always use the project-local cache; layered mappings are created in the buildscript, so they're effectively always "projectmapped"
	//TODO: well, hmm, it makes sense to share them if the hashing system is robust.. especially 1.7.10 where mappings are always layered
	public static Path getLayeredMappingsCache(Project project) {
		//return mkdirs(getProjectCache(project).resolve("layered-mappings"));
		return mkdirs(getUserCache(project).resolve("layered-mappings"));
	}
	
	public static Path getRootProjectCache(Project project) {
		return getProjectCache(project.getRootProject());
	}
	
	//TODO: Should this use getProjectCache instead of getRootProjectCache?
	//Added as a flatDir maven repo in LoomGradlePlugin.
	public static Path getRemappedModCache(Project project) {
		return mkdirs(getRootProjectCache(project).resolve("remapped-mods"));
	}
}

package net.fabricmc.loom;

import org.gradle.api.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Various file paths used throughout the plugin. (These used to be on LoomGradleExtension.)
 */
public class WellKnownLocations {
	/**
	 * The user-global cache for Voldeloom items. Used for items that have completely standard dependencies, like
	 * vanilla Minecraft, regular Forge, no custom mappings or access transformers, etc.
	 * @return A path that should only contain artifacts with no special dependencies on the current project.
	 */
	public static Path getUserCache(Project project) {
		Path userCache = project.getGradle().getGradleUserHomeDir().toPath().resolve("caches").resolve("voldeloom");
		
		try {
			Files.createDirectories(userCache);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return userCache;
	}
	
	/**
	 * A project-specific cache. Used for items that have some dependency on the current project, like customized
	 * mappings, custom Minecraft version, or user-specified access transformers.
	 * @return A path that may contain artifacts that may contain special dependencies on the current project.
	 */
	public static Path getProjectCache(Project project) {
		Path projectCache = project.file(".gradle").toPath().resolve("voldeloom-cache");
		
		try {
			Files.createDirectories(projectCache);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return projectCache;
	}
	
	public static Path getRootProjectCache(Project project) {
		return getProjectCache(project.getRootProject());
	}
	
	//TODO: Should this use getProjectCache instead of getRootProjectCache
	public static Path getRemappedModCache(Project project) {
		Path remappedModCache = getRootProjectCache(project).resolve("remapped-mods");
		
		try {
			Files.createDirectories(remappedModCache);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return remappedModCache;
	}
}

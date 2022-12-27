package net.fabricmc.loom.util;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Various file paths that are used throughout the plugin.
 * These used to be on LoomGradleExtension, but I've been moving extraneous things out of that class,
 * to better understand where its various pieces are used.
 */
@SuppressWarnings("ResultOfMethodCallIgnored") //mkdirs TODO migrate to Path
public class WellKnownLocations {
	public static Path getUserCache(Project project) {
		Path userCache = project.getGradle().getGradleUserHomeDir().toPath().resolve("caches").resolve("fabric-loom");
		
		try {
			Files.createDirectories(userCache);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return userCache;
	}
	
	public static File getRootProjectPersistentCache(Project project) {
		File rootProjectCache = new File(project.getRootProject().file(".gradle"), "loom-cache");
		if (!rootProjectCache.exists()) rootProjectCache.mkdirs();
		return rootProjectCache;
	}
	
	public static File getRemappedModCache(Project project) {
		File remappedModCache = new File(getRootProjectPersistentCache(project), "remapped_mods");
		if (!remappedModCache.exists()) remappedModCache.mkdirs();
		return remappedModCache;
	}
}

package net.fabricmc.loom.util;

import org.gradle.api.Project;

import java.io.File;

/**
 * Various file paths that are used throughout the plugin.
 * These used to be on LoomGradleExtension, but I've been moving extraneous things out of that class,
 * to better understand where its various pieces are used.
 */
@SuppressWarnings("ResultOfMethodCallIgnored") //mkdirs
public class WellKnownLocations {
	public static File getUserCache(Project project) {
		File userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");
		if (!userCache.exists()) userCache.mkdirs();
		return userCache;
	}
	
	public static File getRootProjectBuildCache(Project project) {
		File rootProjectBuildCache = new File(project.getRootProject().getBuildDir(), "loom-cache");
		if (!rootProjectBuildCache.exists()) rootProjectBuildCache.mkdirs();
		return rootProjectBuildCache;
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

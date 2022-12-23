package net.fabricmc.loom.util;

import org.gradle.api.Project;

import java.io.File;

//(VOLDELOOM-DISASTER) Moved from LoomGradleExtensions
@SuppressWarnings("ResultOfMethodCallIgnored") //mkdirs
public class WellKnownLocations {
	public static File getUserCache(Project project) {
		File userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");
		if (!userCache.exists()) userCache.mkdirs();
		return userCache;
	}
	
	public static File getRootProjectPersistentCache(Project project) {
		File rootProjectCache = new File(project.getRootProject().file(".gradle"), "loom-cache");
		if (!rootProjectCache.exists()) rootProjectCache.mkdirs();
		return rootProjectCache;
	}
	
	//Unused
//	public static File getProjectPersistentCache(Project project) {
//		File projectCache = new File(project.file(".gradle"), "loom-cache");
//		if (!projectCache.exists()) projectCache.mkdirs();
//		return projectCache;
//	}
	
	public static File getRootProjectBuildCache(Project project) {
		File rootProjectBuildCache = new File(project.getRootProject().getBuildDir(), "loom-cache");
		if (!rootProjectBuildCache.exists()) rootProjectBuildCache.mkdirs();
		return rootProjectBuildCache;
	}
	
	public static File getProjectBuildCache(Project project) {
		File projectBuildCache = new File(project.getBuildDir(), "loom-cache");
		if (!projectBuildCache.exists()) projectBuildCache.mkdirs();
		return projectBuildCache;
	}
	
	public static File getRemappedModCache(Project project) {
		File remappedModCache = new File(getRootProjectPersistentCache(project), "remapped_mods");
		if (!remappedModCache.exists()) remappedModCache.mkdirs();
		return remappedModCache;
	}
	
	public static File getNativesJarStore(Project project) {
		File natives = new File(getUserCache(project), "natives/jars");
		if (!natives.exists()) natives.mkdirs();
		return natives;
	}
	
	public static File getDevLauncherConfig(Project project) {
		return new File(getRootProjectPersistentCache(project), "launch.cfg");
	}
}

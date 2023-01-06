package net.fabricmc.loom;

import org.gradle.api.Project;

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
	
	public static Path getRootProjectPersistentCache(Project project) {
		Path rootProjectCache = project.getRootProject().file(".gradle").toPath().resolve("loom-cache");
		
		try {
			Files.createDirectories(rootProjectCache);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return rootProjectCache;
	}
	
	public static Path getRemappedModCache(Project project) {
		Path remappedModCache = getRootProjectPersistentCache(project).resolve("remapped-mods");
		
		try {
			Files.createDirectories(remappedModCache);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return remappedModCache;
	}
}

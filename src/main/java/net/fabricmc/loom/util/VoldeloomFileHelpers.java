package net.fabricmc.loom.util;

import org.gradle.api.Project;

public class VoldeloomFileHelpers {
	/**
	 * Deletes a file or directory using Gradle's machinery. Announces each file to be deleted.
	 * Gradle's machinery accepts a million different types of object here, but the most important ones are `File` and `Path`.
	 * Deleting a directory will recursively delete all files inside it, too.
	 */
	public static void delete(Project project, Object... things) {
		project.delete(deleteSpec -> {
			deleteSpec.setFollowSymlinks(false);
			
			for(Object thing : things) {
				project.getLogger().info("Deleting " + thing);
				deleteSpec.delete(thing);
			}
		});
	}
}

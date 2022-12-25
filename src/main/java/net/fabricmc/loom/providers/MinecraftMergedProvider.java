package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.WellKnownLocations;
import net.fabricmc.stitch.merge.JarMerger;
import org.gradle.api.Project;

import java.io.File;

public class MinecraftMergedProvider extends DependencyProvider {
	public MinecraftMergedProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private File merged;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		MinecraftProvider mcProvider = extension.getDependencyManager().getMinecraftProvider();
		File client = mcProvider.getClientJar();
		File server = mcProvider.getServerJar();
		String version = mcProvider.getMinecraftVersion();
		
		//outputs
		File userCache = WellKnownLocations.getUserCache(project);
		merged = new File(userCache, "minecraft-" + version + "-merged.jar");
		
		//execution
		if(!merged.exists()) {
			project.getLogger().lifecycle("|-> Merging client and server jars to " + merged);
			
			try(JarMerger jm = new JarMerger(client, server, merged)) {
				jm.enableSyntheticParamsOffset();
				jm.merge();
			}
			
			project.getLogger().lifecycle("|-> Merge success! :)");
		}
	}
	
	public File getMergedJar() {
		return merged;
	}
}

package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.WellKnownLocations;
import net.fabricmc.stitch.merge.JarMerger;
import org.gradle.api.Project;

import java.io.File;

public class MergedProvider extends DependencyProvider {
	public MergedProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc) {
		super(project, extension);
		this.mc = mc;
	}
	
	private final MinecraftProvider mc;
	
	private File merged;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		File client = mc.getClientJar();
		File server = mc.getServerJar();
		String version = mc.getVersion();
		
		//outputs
		File userCache = WellKnownLocations.getUserCache(project);
		merged = new File(userCache, "minecraft-" + version + "-merged.jar");
		
		//execution
		project.getLogger().lifecycle("] merged jar is at: " + merged);
		if(!merged.exists()) {
			project.getLogger().lifecycle("|-> Does not exist, performing merge...");
			
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

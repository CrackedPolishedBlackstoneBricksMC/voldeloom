package net.fabricmc.loom.task.runs;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.RunConfig;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.newprovider.ConfigElementWrapper;
import net.fabricmc.loom.newprovider.VanillaDependencyFetcher;
import net.fabricmc.loom.newprovider.VanillaJarFetcher;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Gradle task that generates a launch script for DevLaunchInjector.
 * <p>
 * TODO: This whole thing was basically copy-pasted from original Loom and doesn't work.
 *  At this point I don't even use DevLaunchInjector to launch the game anyway.
 *  I'm... not really sure what the benefit is? Its only purpose seems to be specifying the main class &amp; system properties,
 *   but i can do that with a run config
 */
public class GenDevLaunchInjectorConfigsTask extends DefaultTask implements LoomTaskExt {
	public GenDevLaunchInjectorConfigsTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Generates launch script for DevLaunchInjector. TODO broken");
	}
	
	@OutputFile
	public Path getDliConfigPath() {
		return WellKnownLocations.getRootProjectCache(getProject()).resolve("dli-launch.cfg");
	}
	
	@TaskAction
	public void doIt() throws IOException {
		ConfigElementWrapper mc = getLoomGradleExtension().getProviderGraph().mc;
		VanillaJarFetcher vanillaJars = getLoomGradleExtension().getProviderGraph().get(VanillaJarFetcher.class);
		VanillaDependencyFetcher libs = getLoomGradleExtension().getProviderGraph().get(VanillaDependencyFetcher.class);
		
		LaunchConfig launchConfig = new LaunchConfig();
		
		for(RunConfig cfg : getLoomGradleExtension().runConfigs) {
			//TODO: It doesn't actually use any settings in the run config, this is just pasted from old loom
			cfg = cfg.cook(getLoomGradleExtension());
			
			launchConfig
				.property(cfg.getBaseName(), "fabric.development", "true")
				
				.property(cfg.getBaseName(), "java.library.path", libs.getNativesDir().toAbsolutePath().toString())
				.property(cfg.getBaseName(), "org.lwjgl.librarypath", libs.getNativesDir().toAbsolutePath().toString())
				
				.argument(cfg.getBaseName(), "--assetIndex")
				.argument(cfg.getBaseName(), vanillaJars.getVersionManifest().assetIndex.getFabricId(mc.getVersion()))
				.argument(cfg.getBaseName(), "--assetsDir")
				.argument(cfg.getBaseName(), WellKnownLocations.getUserCache(getProject()).resolve("assets").toAbsolutePath().toString());
		}
		
		Files.write(getDliConfigPath(), launchConfig.asString().getBytes(StandardCharsets.UTF_8));
	}
	
	public static class LaunchConfig {
		private final Map<String, List<String>> values = new HashMap<>();
		
		public LaunchConfig property(String key, String value) {
			return property("common", key, value);
		}
		
		public LaunchConfig property(String side, String key, String value) {
			values.computeIfAbsent(side + "Properties", (s -> new ArrayList<>())).add(String.format("%s=%s", key, value));
			return this;
		}
		
		public LaunchConfig argument(String value) {
			return argument("common", value);
		}
		
		public LaunchConfig argument(String side, String value) {
			values.computeIfAbsent(side + "Args", (s -> new ArrayList<>())).add(value);
			return this;
		}
		
		public String asString() {
			StringJoiner stringJoiner = new StringJoiner("\n");
			
			for (Map.Entry<String, List<String>> entry : values.entrySet()) {
				stringJoiner.add(entry.getKey());
				for (String s : entry.getValue()) {
					stringJoiner.add("\t" + s);
				}
			}
			
			return stringJoiner.toString();
		}
	}
}

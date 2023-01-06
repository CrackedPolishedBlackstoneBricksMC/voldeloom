/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import org.gradle.api.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Creates the DevLaunchInjector launch script. The script's location is accessible with getConfig().
 * 
 * TODO: This is broken, and vestigial since I don't use DevLaunchInjector at the moment anyway.
 *  It should probably be made into a Gradle task as well, like the IDE run configs are.
 */
public class DevLaunchInjectorProvider extends DependencyProvider {
	public DevLaunchInjectorProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private final Path devLauncherConfigFile = WellKnownLocations.getRootProjectPersistentCache(project).resolve("launch.cfg");
	
	public void decorateProject(MinecraftProvider mc, MinecraftDependenciesProvider libs) throws IOException {
		final LaunchConfig launchConfig = new LaunchConfig()
				.property("fabric.development", "true")

				.property("client", "java.library.path", libs.getNativesDir().toAbsolutePath().toString())
				.property("client", "org.lwjgl.librarypath", libs.getNativesDir().toAbsolutePath().toString())

				.argument("client", "--assetIndex")
				.argument("client", mc.getVersionManifest().assetIndex.getFabricId(mc.getVersion()))
				.argument("client", "--assetsDir")
				.argument("client", WellKnownLocations.getUserCache(project).resolve("assets").toAbsolutePath().toString());
		
		Files.write(devLauncherConfigFile, launchConfig.asString().getBytes(StandardCharsets.UTF_8));

		//addDependency("net.fabricmc:dev-launch-injector:" + Constants.DEV_LAUNCH_INJECTOR_VERSION, "runtimeOnly");
		installed = true;
	}
	
	public Path getConfig() {
		return devLauncherConfigFile;
	}
	
	public static class LaunchConfig {
		private final Map<String, List<String>> values = new HashMap<>();

		public LaunchConfig property(String key, String value) {
			return property("common", key, value);
		}

		public LaunchConfig property(String side, String key, String value) {
			values.computeIfAbsent(side + "Properties", (s -> new ArrayList<>()))
					.add(String.format("%s=%s", key, value));
			return this;
		}

		public LaunchConfig argument(String value) {
			return argument("common", value);
		}

		public LaunchConfig argument(String side, String value) {
			values.computeIfAbsent(side + "Args", (s -> new ArrayList<>()))
					.add(value);
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
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.singleton(devLauncherConfigFile);
	}
}

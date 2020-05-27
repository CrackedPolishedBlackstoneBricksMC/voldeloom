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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;

public class LaunchProvider extends DependencyProvider {
	public LaunchProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws IOException {
		final LaunchConfig launchConfig = new LaunchConfig()
				.property("fabric.development", "true")
				.property("log4j.configurationFile", getLog4jConfigFile().getAbsolutePath())

				.property("client", "java.library.path", getExtension().getNativesDirectory().getAbsolutePath())
				.property("client", "org.lwjgl.librarypath", getExtension().getNativesDirectory().getAbsolutePath())

				.argument("client", "--assetIndex")
				.argument("client", getExtension().getMinecraftProvider().getVersionInfo().assetIndex.getFabricId(getExtension().getMinecraftProvider().getMinecraftVersion()))
				.argument("client", "--assetsDir")
				.argument("client", new File(getExtension().getUserCache(), "assets").getAbsolutePath())
				.argument("client", "--launchTarget")
				.argument("client", "fmluserdevclient")
				.argument("--fml.forgeGroup")
				.argument("net.minecraftforge")
				.argument("--fml.forgeVersion")
				.argument(getExtension().getDependencyManager().getProvider(ForgeProvider.class).getForgeVersion().split("-")[1])
				.argument("--gameDir")
				.argument(".")
				.argument("--fml.mcpVersion")
				.argument("yarn")
				.argument("--fml.mcVersion")
				.argument("1.15.2");

		//Enable ansi by default for idea and vscode
		if (new File(getProject().getRootDir(), ".vscode").exists()
				|| new File(getProject().getRootDir(), ".idea").exists()
				|| (Arrays.stream(getProject().getRootDir().listFiles()).anyMatch(file -> file.getName().endsWith(".iws")))) {
			launchConfig.property("fabric.log.disableAnsi", "false");
		}

		writeLog4jConfig();
		FileUtils.writeStringToFile(getExtension().getDevLauncherConfig(), launchConfig.asString(), StandardCharsets.UTF_8);

		addDependency("net.fabricmc:dev-launch-injector:" + Constants.DEV_LAUNCH_INJECTOR_VERSION, "runtimeOnly");
		addDependency("net.minecrell:terminalconsoleappender:" + Constants.TERMINAL_CONSOLE_APPENDER_VERSION, "runtimeOnly");
	}

	private File getLog4jConfigFile() {
		return new File(getExtension().getDevLauncherConfig().getParentFile(), "log4j.xml");
	}

	private void writeLog4jConfig() {
		try (InputStream is = LaunchProvider.class.getClassLoader().getResourceAsStream("log4j2.fabric.xml")) {
			Files.deleteIfExists(getLog4jConfigFile().toPath());
			Files.copy(is, getLog4jConfigFile().toPath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate log4j config", e);
		}
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT_NAMED;
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
}

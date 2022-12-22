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

package net.fabricmc.loom.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MinecraftProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RunConfig {
	public String configName = "";
	public String projectName = "";
	public String mainClass = "";
	public String runDir = "";
	public String vmArgs = "";
	public String programArgs = "";
	
	//TODO fold these in to run configs and stuff too, i had to hack these on because i Suck at Gradle
	public Map<String, String> systemProperties = new HashMap<>();

	public Element genRuns(Element doc) throws IOException, ParserConfigurationException, TransformerException {
		Element root = this.addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
		root = addXml(root, "configuration", ImmutableMap.of("default", "false", "name", configName, "type", "Application", "factoryName", "Application"));

		this.addXml(root, "module", ImmutableMap.of("name", projectName));
		this.addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
		this.addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDir));

		if (!Strings.isNullOrEmpty(vmArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", vmArgs));
		}

		if (!Strings.isNullOrEmpty(programArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", programArgs));
		}

		return root;
	}

	public Element addXml(Node parent, String name, Map<String, String> values) {
		Document doc = parent.getOwnerDocument();

		if (doc == null) {
			doc = (Document) parent;
		}

		Element e = doc.createElement(name);

		for (Map.Entry<String, String> entry : values.entrySet()) {
			e.setAttribute(entry.getKey(), entry.getValue());
		}

		parent.appendChild(e);
		return e;
	}

	private static void populate(Project project, LoomGradleExtension extension, RunConfig runConfig, String mode) {
		runConfig.projectName = project.getName();
		runConfig.runDir = "file://$PROJECT_DIR$/" + extension.runDir;
		runConfig.vmArgs = "";

		project.getLogger().lifecycle("LOADER LAUNCH METHOD: " + extension.getLoaderLaunchMethod());
		
		switch (extension.getLoaderLaunchMethod()) {
			//TODO(VOLDELOOM-DISASTER): The `direct` launch method was a good learning resource, but i don't think it actually works
			case "direct":
				runConfig.mainClass = mode.equals("client") ? "net.minecraft.client.Minecraft" : "net.minecraft.server.MinecraftServer";
				runConfig.programArgs = "";
				
				//Loom's LaunchProvider sets these too but launchprovider works with dev-launch-injector concepts, i don't use it atm
				runConfig.systemProperties.put("minecraft.applet.TargetDirectory", project.getRootDir().toPath().resolve("run").resolve(".minecraft").toAbsolutePath().toString());
				runConfig.systemProperties.put("java.library.path", extension.getNativesDirectory().getAbsolutePath());
				runConfig.systemProperties.put("org.lwjgl.librarypath", extension.getNativesDirectory().getAbsolutePath());
				//the fml relauncher always takes arg 0 as player name, and arg 1 as session key (or -)
				runConfig.programArgs += "Player - ";
				
				//TODO: 1.4.7 doesn't actually support assetIndex parameters! I think this was a launchwrapper addition?
				runConfig.programArgs += "--assetIndex " + extension.getMinecraftProvider().getVersionInfo().assetIndex.getFabricId(extension.getMinecraftProvider().getMinecraftVersion());
				runConfig.programArgs += " --assetsDir " + encodeEscaped(new File(extension.getUserCache(), "assets").getAbsolutePath());
				break;
			case "launchwrapper2":
				runConfig.mainClass = "net.minecraft.launchwrapper.Launch";
				runConfig.programArgs = "";
				runConfig.programArgs += "--tweakClass net.minecraft.launchwrapper.VanillaTweaker";
				//TODO VanillaTweaker doesn't seem to do anything with these either, whys that
				runConfig.programArgs += " --assetIndex " + extension.getMinecraftProvider().getVersionInfo().assetIndex.getFabricId(extension.getMinecraftProvider().getMinecraftVersion());
				runConfig.programArgs += " --assetsDir " + encodeEscaped(new File(extension.getUserCache(), "assets").getAbsolutePath());
				runConfig.programArgs += " --gameDir " + project.getRootDir().toPath().resolve("run").resolve(".minecraft").toAbsolutePath();
				runConfig.programArgs += " PlayerName -";
				runConfig.systemProperties.put("minecraft.applet.TargetDirectory", project.getRootDir().toPath().resolve("run").resolve(".minecraft").toAbsolutePath().toString());
				runConfig.systemProperties.put("java.library.path", extension.getNativesDirectory().getAbsolutePath());
				runConfig.systemProperties.put("org.lwjgl.librarypath", extension.getNativesDirectory().getAbsolutePath());
				break;
				//below is old shit from loom
			case "launchwrapper":
				runConfig.mainClass = "net.minecraft.launchwrapper.Launch";
				runConfig.programArgs = "--tweakClass " + ("client".equals(mode) ? Constants.DEFAULT_FABRIC_CLIENT_TWEAKER : Constants.DEFAULT_FABRIC_SERVER_TWEAKER);
				break;
			default:
				runConfig.mainClass = "net.fabricmc.devlaunchinjector.Main";
				runConfig.programArgs = "";
				runConfig.vmArgs = "-Dfabric.dli.config=" + encodeEscaped(extension.getDevLauncherConfig().getAbsolutePath()) + " -Dfabric.dli.env=" + mode.toLowerCase();
				break;
		}

		if (extension.getLoaderLaunchMethod().equals("launchwrapper")) {
			// if installer.json found...
			JsonObject installerJson = extension.getInstallerJson();

			if (installerJson != null) {
				List<String> sideKeys = ImmutableList.of(mode, "common");

				// copy launchwrapper tweakers
				if (installerJson.has("launchwrapper")) {
					JsonObject launchwrapperJson = installerJson.getAsJsonObject("launchwrapper");

					if (launchwrapperJson.has("tweakers")) {
						JsonObject tweakersJson = launchwrapperJson.getAsJsonObject("tweakers");
						StringBuilder builder = new StringBuilder();

						for (String s : sideKeys) {
							if (tweakersJson.has(s)) {
								for (JsonElement element : tweakersJson.getAsJsonArray(s)) {
									builder.append(" --tweakClass ").append(element.getAsString());
								}
							}
						}

						runConfig.programArgs += builder.toString();
					}
				}
			}
		}
	}

	public static RunConfig clientRunConfig(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MinecraftProvider minecraftProvider = extension.getMinecraftProvider();
		MinecraftVersionInfo minecraftVersionInfo = minecraftProvider.getVersionInfo();

		RunConfig ideaClient = new RunConfig();
		populate(project, extension, ideaClient, "client");
		ideaClient.configName = "Minecraft Client";
		ideaClient.vmArgs += getOSClientJVMArgs();
		//ideaClient.vmArgs += " -Dfabric.dli.main=" + getMainClass("client", extension);

		return ideaClient;
	}

	public static RunConfig serverRunConfig(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		RunConfig ideaServer = new RunConfig();
		populate(project, extension, ideaServer, "server");
		ideaServer.configName = "Minecraft Server";
		ideaServer.vmArgs += " -Dfabric.dli.main=" + getMainClass("server", extension);

		return ideaServer;
	}

	//This can be removed at somepoint, its not ideal but its the best solution I could thing of
	public static boolean needsUpgrade(File file) throws IOException {
		String contents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		return !(contents.contains("net.fabricmc.devlaunchinjector.Main"));
	}

	public String fromDummy(String dummy) throws IOException {
		String dummyConfig;

		try (InputStream input = SetupIntelijRunConfigs.class.getClassLoader().getResourceAsStream(dummy)) {
			dummyConfig = IOUtils.toString(input, StandardCharsets.UTF_8);
		}

		dummyConfig = dummyConfig.replace("%NAME%", configName);
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", mainClass);
		dummyConfig = dummyConfig.replace("%MODULE%", projectName);
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", programArgs.replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", vmArgs.replaceAll("\"", "&quot;"));

		return dummyConfig;
	}

	public static String getOSClientJVMArgs() {
		if (OperatingSystem.getOS().equalsIgnoreCase("osx")) {
			return " -XstartOnFirstThread";
		}

		return "";
	}

	private static String getMainClass(String side, LoomGradleExtension extension) {
		JsonObject installerJson = extension.getInstallerJson();

		if (installerJson != null && installerJson.has("mainClass")) {
			JsonElement mainClassJson = installerJson.get("mainClass");

			String mainClassName = "";

			if (mainClassJson.isJsonObject()) {
				JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

				if (mainClassesJson.has(side)) {
					mainClassName = mainClassesJson.get(side).getAsString();
				}
			} else {
				mainClassName = mainClassJson.getAsString();
			}

			return mainClassName;
		}

		// Fallback to default class names, happens when in a loader dev env
		if ("launchwrapper".equals(extension.getLoaderLaunchMethod())) {
			return "net.minecraft.launchwrapper.Launch";
		}

		return "net.fabricmc.loader.launch.knot.Knot" + side.substring(0, 1).toUpperCase(Locale.ROOT) + side.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String encodeEscaped(String s) {
		StringBuilder ret = new StringBuilder();

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (c == '@' && i > 0 && s.charAt(i - 1) == '@' || c == ' ') {
				ret.append("@@");
				ret.append(Integer.toString(c, 16));
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}
}

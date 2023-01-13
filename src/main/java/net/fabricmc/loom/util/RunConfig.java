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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * All the information required to launch a working copy of the game.
 * 
 * Voldeloom is based off of Loom 0.4; this stuff is backported from Loom 1.
 */
public class RunConfig implements Named {
	private final Project project;
	private final String baseName;
	
	private List<String> vmArgs = new ArrayList<>();
	private List<String> programArgs = new ArrayList<>();
	private String environment; //"client", "server"
	private String name; //"friendly name"
	private String mainClass;
	private String runDir;
	private boolean ideConfigGenerated;
	//TODO: loom 1 has a "source" param, but i think it's mainly for their split sourceset nonsense
	
	public RunConfig(Project project, String name) {
		this.baseName = this.name = name;
		this.project = project;
		this.ideConfigGenerated = project == project.getRootProject();
		this.runDir = "run";
	}
	
	public static RunConfig defaultClientRunConfig(Project project) {
		RunConfig haha = new RunConfig(project, "client");
		haha.client();
		return haha;
	}
	
	public static RunConfig defaultServerRunConfig(Project project) {
		RunConfig haha = new RunConfig(project, "server");
		haha.server();
		return haha;
	}
	
	public String stringifyProgramArgs() {
		return String.join(" ", programArgs);
	}
	
	public String stringifyVmArgs() {
		return String.join(" ", vmArgs);
	}
	
	public Path resolveRunDir() {
		return project.getRootDir().toPath().resolve(runDir);
	}
	
	public RunConfig copy() {
		RunConfig copy = new RunConfig(project, baseName);
		copy.vmArgs = new ArrayList<>(this.vmArgs);
		copy.programArgs = new ArrayList<>(this.programArgs);
		copy.environment = this.environment;
		copy.name = this.name;
		copy.mainClass = this.mainClass;
		copy.runDir = this.runDir;
		copy.ideConfigGenerated = this.ideConfigGenerated;
		return copy;
	}
	
	public RunConfig cook(LoomGradleExtension ext) {
		//TODO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		RunConfig copy = copy();
		if(getEnvironment().equals("client")) {
			copy.property("minecraft.applet.TargetDirectory", resolveRunDir().toAbsolutePath().toString());
			String nativeLibsDir = ext.getDependencyManager().getMinecraftDependenciesProvider().getNativesDir().toAbsolutePath().toString();
			copy.property("java.library.path", nativeLibsDir);
			copy.property("org.lwjgl.librarypath", nativeLibsDir);
		}
		
		//TODO: only has any effect on 1.5.2 (see CoreFMLLibraries), maybe 1.6
		// kinda makes ForgeDependenciesProvider redundant for the actual *downloading* process,
		// but it's still valuable because it adds them as gradle deps
		copy.property("fml.core.libraries.mirror", ext.fmlLibrariesBaseUrl + "%s"); //forge uses it as a format string
		
		return copy;
	}
	
	/// Presets ///
	
	public void client() {
		startOnFirstThread();
		setEnvironment("client");
		setMainClass("net.minecraft.client.Minecraft");
		setRunDir("run");
	}
	
	public void server() {
		programArg("nogui");
		setEnvironment("server");
		setMainClass("net.minecraft.server.MinecraftServer");
		setRunDir("run/server");
	}
	
	/// Convenience ///
	
	public void serverWithGui() {
		programArgs.removeIf("nogui"::equals);
	}
	
	public void startOnFirstThread() {
		if(OperatingSystem.getOS().equalsIgnoreCase("osx")) {
			vmArg("-XstartOnFirstThread");
		}
	}
	
	public void programArg(String arg) {
		programArgs.add(arg);
	}
	
	public void programArgs(String... args) {
		programArgs.addAll(Arrays.asList(args));
	}
	
	public void programArgs(Iterable<String> args) {
		for(String arg : args) programArg(arg);
	}
	
	public void vmArg(String arg) {
		vmArgs.add(arg);
	}
	
	public void vmArgs(String... args) {
		vmArgs.addAll(Arrays.asList(args));
	}
	
	public void vmArgs(Iterable<String> args) {
		for(String arg : args) vmArg(arg);
	}
	
	public void property(String k, String v) {
		vmArg("-D" + k + '=' + v);
	}
	
	public void property(String k) {
		vmArg("-D" + k);
	}
	
	public void properties(Map<String, String> props) {
		props.forEach(this::property);
	}
	
	/// Bean properties ///
	
	public List<String> getVmArgs() {
		return vmArgs;
	}
	
	public void setVmArgs(List<String> vmArgs) {
		this.vmArgs = vmArgs;
	}
	
	public List<String> getProgramArgs() {
		return programArgs;
	}
	
	public void setProgramArgs(List<String> programArgs) {
		this.programArgs = programArgs;
	}
	
	public String getEnvironment() {
		return environment;
	}
	
	public void setEnvironment(String environment) {
		this.environment = environment;
	}
	
	public String getBaseName() {
		return baseName;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getMainClass() {
		return mainClass;
	}
	
	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}
	
	public String getRunDir() {
		return runDir;
	}
	
	public void setRunDir(String runDir) {
		this.runDir = runDir;
	}
	
	public boolean isIdeConfigGenerated() {
		return ideConfigGenerated;
	}
	
	public void setIdeConfigGenerated(boolean ideConfigGenerated) {
		this.ideConfigGenerated = ideConfigGenerated;
	}
	
	/// Things I should probably find a better home for ///
	
	public String configureTemplate(String template) throws IOException {
		String templatedConfig;
		
		try(InputStream input = RunConfig.class.getClassLoader().getResourceAsStream(template)) {
			if(input == null) throw new IllegalArgumentException("Couldn't find template " + template + " in " + RunConfig.class.getClassLoader());
			try {
				templatedConfig = new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IllegalStateException("Problem reading template " + template + " to string in " + RunConfig.class.getClassLoader());
			}
		}
		
		templatedConfig = templatedConfig.replace("%NAME%", getName());
		templatedConfig = templatedConfig.replace("%MAIN_CLASS%", getMainClass());
		templatedConfig = templatedConfig.replace("%MODULE%", project.getName());
		templatedConfig = templatedConfig.replace("%PROGRAM_ARGS%", stringifyProgramArgs().replace("\"", "&quot;"));
		templatedConfig = templatedConfig.replace("%VM_ARGS%", stringifyVmArgs().replace("\"", "&quot;"));
		
		return templatedConfig;
	}
	
	private static String encodeDevLaunchInjectorEscaped(String s) {
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
	
	//TODO: Untested, and also gradle project import works fine, so I'm not sure why this exists
	public Element addRunConfigsToIntellijProjectFile(Element doc) {
		Element root = addChildNode(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
//		root = addChildNode(root, "configuration", ImmutableMap.of("default", "false", "name", getConfigName(), "type", "Application", "factoryName", "Application"));
//		
//		addChildNode(root, "module", ImmutableMap.of("name", getProjectName()));
//		addChildNode(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", getMainClass()));
//		addChildNode(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", getRunDir()));
//		
//		if (getVmArgs() != null && !getVmArgs().isEmpty()) addChildNode(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", stringifyVmArgs()));
//		if (getProgramArgs() != null && !getProgramArgs().isEmpty()) addChildNode(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", stringifyProgramArgs()));
		
		return root;
	}
	
	private static Element addChildNode(Node parent, String name, Map<String, String> values) {
		Document doc = parent.getOwnerDocument();
		if (doc == null) doc = (Document) parent;
		Element elem = doc.createElement(name);
		for (Map.Entry<String, String> entry : values.entrySet()) {
			elem.setAttribute(entry.getKey(), entry.getValue());
		}
		parent.appendChild(elem);
		return elem;
	}
}

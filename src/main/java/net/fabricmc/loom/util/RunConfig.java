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
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RunConfig {
	public String configName = "";
	public String projectName = "";
	public String mainClass = "";
	public String runDir = "";
	public String vmArgs = "";
	public String programArgs = "";
	
	//TODO fold these in to run configs and stuff too, i had to hack these on because i Suck at Gradle
	// Also need to be careful wrt. escaping and stuff
	public Map<String, String> systemProperties = new HashMap<>();

	public String configureTemplate(String dummy) throws IOException {
		String dummyConfig;

		try (InputStream input = RunConfig.class.getClassLoader().getResourceAsStream(dummy)) {
			dummyConfig = IOUtils.toString(input, StandardCharsets.UTF_8);
		}

		dummyConfig = dummyConfig.replace("%NAME%", configName);
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", mainClass);
		dummyConfig = dummyConfig.replace("%MODULE%", projectName);
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", programArgs.replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", vmArgs.replaceAll("\"", "&quot;"));

		return dummyConfig;
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
	
	//TODO: Untested, and also gradle project import works fine, so I'm not sure why this exists
	public Element addRunConfigsToIntellijProjectFile(Element doc) {
		Element root = addChildNode(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
		root = addChildNode(root, "configuration", ImmutableMap.of("default", "false", "name", configName, "type", "Application", "factoryName", "Application"));
		
		addChildNode(root, "module", ImmutableMap.of("name", projectName));
		addChildNode(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
		addChildNode(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDir));
		
		if (vmArgs != null && !vmArgs.isEmpty()) addChildNode(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", vmArgs));
		if (programArgs != null && !programArgs.isEmpty()) addChildNode(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", programArgs));
		
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

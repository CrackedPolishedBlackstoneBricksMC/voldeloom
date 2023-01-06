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

package net.fabricmc.loom;

import com.google.common.collect.ImmutableList;
import net.fabricmc.loom.util.RemappedConfigurationEntry;

import java.util.List;

public class Constants {
	public static final String TASK_GROUP_CLEANING = "fabric.clean";
	public static final String TASK_GROUP_IDE = "fabric.ide";
	public static final String TASK_GROUP_PLUMBING = "fabric.plumbing";
	public static final String TASK_GROUP_RUNNING = "fabric.run";
	public static final String TASK_GROUP_TOOLS = "fabric.tools";
	
	public static final String SYSTEM_ARCH = System.getProperty("os.arch").equals("64") ? "64" : "32";

	public static final String MOD_COMPILE_CLASSPATH = "modCompileClasspath";
	public static final String MOD_COMPILE_CLASSPATH_MAPPED = "modCompileClasspathMapped";
	public static final List<RemappedConfigurationEntry> MOD_COMPILE_ENTRIES = ImmutableList.of(
		//modded config, regular java config, modded remapped config, whether it gets on the mod compile classpath, maven relation, and coremoddiness
		new RemappedConfigurationEntry("modCompile"           , "compile"       , "modCompileMapped"           , true , "compile", false),
		new RemappedConfigurationEntry("modApi"               , "api"           , "modApiMapped"               , true , "compile", false),
		new RemappedConfigurationEntry("modImplementation"    , "implementation", "modImplementationMapped"    , true , "runtime", false),
		new RemappedConfigurationEntry("modRuntime"           , "runtimeOnly"   , "modRuntimeMapped"           , false, ""       , false),
		new RemappedConfigurationEntry("modCompileOnly"       , "compileOnly"   , "modCompileOnlyMapped"       , true , ""       , false),
		new RemappedConfigurationEntry("coremodCompile"       , "compile"       , "coremodCompileMapped"       , true , "compile", true),
		new RemappedConfigurationEntry("coremodApi"           , "api"           , "coremodApiMapped"           , true , "compile", true),
		new RemappedConfigurationEntry("coremodImplementation", "implementation", "coremodImplementationMapped", true , "runtime", true),
		new RemappedConfigurationEntry("coremodRuntime"       , "runtimeOnly"   , "coremodRuntimeMapped"       , false, ""       , true),
		new RemappedConfigurationEntry("coremodCompileOnly"   , "compileOnly"   , "coremodCompileOnlyMapped"   , true , ""       , true)
	);
	
	public static final String MINECRAFT = "minecraft";
	public static final String MINECRAFT_DEPENDENCIES = "minecraftLibraries";
	public static final String MINECRAFT_NAMED = "minecraftNamed";
	
	public static final String MAPPINGS = "mappings";
	public static final String MAPPINGS_FINAL = "mappingsFinal";
	
	public static final String FORGE = "forge";
	public static final String FORGE_DEPENDENCIES = "forgeLibraries";

	//public static final String DEV_LAUNCH_INJECTOR_VERSION = "0.2.0+build.6";
}

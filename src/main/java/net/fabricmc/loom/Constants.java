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

/**
 * Values that do not change across an invocation of the plugin, such as the names of Gradle configurations.
 * <p>
 * There are more Gradle configurations than what are defined in this class, btw.
 * 
 * @see LoomGradlePlugin for where more Gradle configs are defined (like {@code modRuntime})
 */
public class Constants {
	/// Task groups ///
	public static final String TASK_GROUP_CLEANING = "fabric.clean";
	public static final String TASK_GROUP_IDE = "fabric.ide";
	public static final String TASK_GROUP_PLUMBING = "fabric.plumbing";
	public static final String TASK_GROUP_RUNNING = "fabric.run";
	public static final String TASK_GROUP_TOOLS = "fabric.tools";

	/// Configuration names ///
	
	/**
	 * The Gradle configuration containing all <i>incoming</i> mod dependencies, e.g. {@code modCompileOnly},
	 * which are mods that haven't been remapped to the user's current working mappings yet.
	 * 
	 * @see net.fabricmc.loom.providers.RemappedDependenciesProvider
	 */
	public static final String EVERY_UNMAPPED_MOD = "everyUnmappedMod";
	
	/**
	 * The Gradle configuration containing the vanilla Minecraft artifact.<br>
	 * Owing to Minecraft not being on a real Maven server, only the version of the artifact is used. It doesn't resolve to anything.
	 * 
	 * @see net.fabricmc.loom.providers.MinecraftProvider
	 */
	public static final String MINECRAFT = "minecraft";
	
	/**
	 * The Gradle configuration containing the user's specified mappings input file.
	 *
	 * @see net.fabricmc.loom.providers.MappingsProvider
	 */
	public static final String MAPPINGS = "mappings";
	
	/**
	 * The Gradle configuration containing the Minecraft Forge artifact.
	 *
	 * @see net.fabricmc.loom.providers.ForgeProvider
	 */
	public static final String FORGE = "forge";
	
	/**
	 * The Gradle configuration containing any custom access-transformer files.
	 * 
	 * @see net.fabricmc.loom.providers.ForgePatchedAccessTxdProvider
	 */
	public static final String CUSTOM_ACCESS_TRANSFORMERS = "accessTransformers";
	
	/**
	 * The Gradle configuration containing all of Minecraft's own dependencies, such as LWJGL.
	 * 
	 * @see net.fabricmc.loom.providers.MinecraftDependenciesProvider
	 */
	public static final String MINECRAFT_DEPENDENCIES = "minecraftLibraries";
	
	/**
	 * The Gradle configuration containing Minecraft, patched with Forge's patches, remapped to the user's current working mappings set.
	 * 
	 * @see net.fabricmc.loom.providers.MappedProvider
	 */
	public static final String MINECRAFT_NAMED = "minecraftNamed";
	
	/**
	 * The Gradle configuration containing all of Forge's own dependencies, such as Guava.<br>
	 * These were formerly downloaded automatically when Forge started up, but the server is dead.
	 * 
	 * @see net.fabricmc.loom.providers.ForgeDependenciesProvider for what detects these dependencies
	 */
	public static final String FORGE_DEPENDENCIES = "forgeLibraries";
	
	/// Mapping scheme names ///
	
	/**
	 * The name used to refer to "off-the-shelf Minecraft".
	 */
	public static final String PROGUARDED_NAMING_SCHEME = "official";
	
	/**
	 * The name used to refer to a typical abstraction used in mapping ecosystems, where names are first moved to an intermediate naming scheme.<br>
	 * Sometimes mods are released in this naming scheme too.
	 */
	public static final String INTERMEDIATE_NAMING_SCHEME = "intermediary";
	
	/**
	 * The name used to refer to Minecraft with developer-friendly names.
	 */
	public static final String MAPPED_NAMING_SCHEME = "named";
}

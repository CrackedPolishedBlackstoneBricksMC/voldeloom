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

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.TinyRemapperSession;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Remaps the patched+accesstransformed Minecraft+Forge jar into the user's selected mappings.
 * 
 * The named jar is available with {@code getMappedJar()}, and the intermediary (srg) jar is also available.
 */
public class MappedProvider extends DependencyProvider {
	@Inject
	public MappedProvider(Project project, LoomGradleExtension extension, MinecraftDependenciesProvider libs, ForgePatchedProvider forgePatched, ForgePatchedAccessTxdProvider patchedTxd, MappingsProvider mappings) {
		super(project, extension);
		this.libs = libs;
		this.forgePatched = forgePatched;
		this.patchedTxd = patchedTxd;
		this.mappings = mappings;
		
		dependsOn(libs, forgePatched, patchedTxd, mappings);
	}
	
	private final MinecraftDependenciesProvider libs;
	private final ForgePatchedProvider forgePatched;
	private final ForgePatchedAccessTxdProvider patchedTxd;
	private final MappingsProvider mappings;
	
	//jars (to be created by performInstall)
	private Path mappedJar;
	private Path intermediaryJar;
	
	//some weirdness related to adding the gradle dependency (flatdir moment)
	private Path mappedDestDir;
	private String mappedJarNameKinda;
	
	@Override
	protected void performSetup() throws Exception {
		Path userCache = WellKnownLocations.getUserCache(project);
		
		//TODO kludgy? yeah
		String intermediaryJarNameKinda = String.format("%s-%s-%s-%s",
			forgePatched.getPatchedVersionTag(),
			Constants.INTERMEDIATE_NAMING_SCHEME,
			mappings.getMappingsName(),
			mappings.getMappingsVersion()
		);
		String intermediaryJarName = "minecraft-" + intermediaryJarNameKinda + ".jar";
		
		//We put the mapped jar in its own directory so that we can add the directory as a flatDir, where it will live all by itself.
		//no need to risk something like, a naming collision with other versions that happen to be lying around.
		mappedJarNameKinda = String.format("%s-%s-%s-%s",
			forgePatched.getPatchedVersionTag(),
			Constants.MAPPED_NAMING_SCHEME,
			mappings.getMappingsName(),
			mappings.getMappingsVersion()
		);
		String mappedJarName = "minecraft-" + mappedJarNameKinda + ".jar";
		mappedDestDir = userCache.resolve(mappedJarNameKinda);
		
		intermediaryJar = userCache.resolve(intermediaryJarName);
		mappedJar = mappedDestDir.resolve(mappedJarName);
		
		project.getLogger().lifecycle("] intermediary jar: {}", intermediaryJar);
		project.getLogger().lifecycle("] mapped jar: {}", mappedJar);
		
		cleanOnRefreshDependencies(mappedJar, mappedDestDir, intermediaryJar);
	}
	
	public void performInstall() throws Exception {
		if (Files.notExists(intermediaryJar) || Files.notExists(mappedJar)) {
			project.getLogger().lifecycle("|-> At least one mapped jar didn't exist, performing remap...");
			
			//ensure both are actually gone
			Files.deleteIfExists(mappedJar);
			Files.deleteIfExists(intermediaryJar);
			
			//These are minecraft libraries that conflict with the ones forge wants
			//They're obfuscated and mcp maps them back to reality. The forge Ant script had a task to delete them lol.
			//https://github.com/MinecraftForge/FML/blob/8e7956397dd80902f7ca69c466e833047dfa5010/build.xml#L295-L298
			Predicate<String> classFilter = s -> !s.startsWith("argo") && !s.startsWith("org");
			
			Files.createDirectories(mappedDestDir);
			
			new TinyRemapperSession()
				.setMappings(mappings.getMappings())
				.setInputJar(patchedTxd.getTransformedJar())
				.setInputNamingScheme(Constants.PROGUARDED_NAMING_SCHEME)
				.setInputClasspath(libs.getNonNativeLibraries())
				.addOutputJar(Constants.INTERMEDIATE_NAMING_SCHEME, this.intermediaryJar)
				.addOutputJar(Constants.MAPPED_NAMING_SCHEME, this.mappedJar)
				.setClassFilter(classFilter)
				.setLogger(project.getLogger()::lifecycle)
				.run();
			
			project.getLogger().lifecycle("\\-> Remap success! :)");
		}
		
		project.getLogger().info("|-> Adding flatDir repo at {} to project", mappedDestDir);
		project.getRepositories().flatDir(repository -> repository.dir(mappedDestDir));
		
		String depStr = "net.minecraft:minecraft:" + mappedJarNameKinda;
		project.getLogger().info("|-> Adding dependency {} to the '{}' configuration", depStr, Constants.MINECRAFT_NAMED);
		project.getDependencies().add(Constants.MINECRAFT_NAMED, project.getDependencies().module(depStr));
	}
	
	public Path getMappedJar() {
		return mappedJar;
	}
	
	public Path getIntermediaryJar() {
		return intermediaryJar;
	}
}

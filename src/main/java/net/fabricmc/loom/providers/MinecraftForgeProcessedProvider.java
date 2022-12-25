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
import net.fabricmc.loom.forge.ATClassVisitor;
import net.fabricmc.loom.forge.ForgeATConfig;
import net.fabricmc.loom.forge.ForgeProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

public class MinecraftForgeProcessedProvider extends DependencyProvider {
	public MinecraftForgeProcessedProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private File projectMappedJar;
	
	@Override
	public void decorateProject() throws Exception {
		//inputs
		MinecraftForgeMappedProvider mappedProvider = extension.getDependencyManager().getMinecraftForgeMappedProvider();
		File mappedJar = mappedProvider.getMappedJar();
		
		ForgeProvider forge = extension.getDependencyManager().getForgeProvider();
		
		//outputs
		File projectBuildCache = WellKnownLocations.getProjectBuildCache(project);
		File projectmappedDir = new File(projectBuildCache, "projectmapped");
		projectmappedDir.mkdirs();
		
		String processedJarNameKinda = String.format("%s-%s-%s-%s",
			extension.getDependencyManager().getMinecraftProvider().getJarStuff(),
			"processed",
			extension.getDependencyManager().getMappingsProvider().mappingsName,
			extension.getDependencyManager().getMappingsProvider().mappingsVersion
		);
		String processedJarName = "minecraft-" + processedJarNameKinda + ".jar";
		
		projectMappedJar = new File(projectmappedDir, processedJarName);
		
		//task
		//TODO split ForgeProvider into that and ForgeAccessTransformerProvider, or something, so we don't need this kludge
		forge.remapAccessTransformersNowThatAMappingsProviderIsAvailable();
		//actual task
		if(!projectMappedJar.exists()) {
			Files.copy(mappedJar.toPath(), projectMappedJar.toPath());
			
			try(FileSystem processedFs = FileSystems.newFileSystem(URI.create("jar:" + projectMappedJar.toURI()), Collections.singletonMap("create", "true"))) {
				//Delete some Forge libraries
				Files.walkFileTree(processedFs.getPath("org"), new DeletingFileVisitor());
				Files.walkFileTree(processedFs.getPath("argo"), new DeletingFileVisitor());
				
				//Feed them through some ASM soup
				Files.walk(processedFs.getPath("/"))
					.filter(path -> path.toString().endsWith(".class"))
					.forEach(path -> transformClass(path, forge.getATs()));
			}
		}
		
		//add as dependency TODO move out
		project.getRepositories().flatDir(repository -> repository.dir(projectmappedDir));
		project.getDependencies().add(Constants.MINECRAFT_NAMED, project.getDependencies().module("net.minecraft:minecraft:" + processedJarNameKinda));
	}

	public File getProcessedJar() {
		return projectMappedJar;
	}
	
	private static class DeletingFileVisitor extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.delete(file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			Files.delete(dir);
			return FileVisitResult.CONTINUE;
		}
	}
	
	private static void transformClass(Path p, ForgeATConfig atConfig) {
		try(InputStream input = new BufferedInputStream(Files.newInputStream(p))) {
			ClassReader classReader = new ClassReader(input);
			ClassWriter classWriter = new ClassWriter(0);
			//(VOLDELOOM-DISASTER) Original plugin remapped Forge's SideOnly into the fabric Environment annotations.
			//I don't know why this was done, because SideOnly annotations are already in the jar, and work fine.
			classReader.accept(new ATClassVisitor(classWriter, atConfig), 0);
			input.close();
			byte[] clazz = classWriter.toByteArray();
			Files.copy(new ByteArrayInputStream(clazz), p, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("Error applying ATs", e);
		}
	}
}

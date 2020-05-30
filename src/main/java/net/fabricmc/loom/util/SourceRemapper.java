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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.util.StitchUtil;

public class SourceRemapper {
	private final Project project;
	private final boolean toNamed;
	private final List<Runnable> remapTasks = new ArrayList<>();

	private Mercury mercury;

	public SourceRemapper(Project project, boolean toNamed) {
		this.project = project;
		this.toNamed = toNamed;
	}

	public static void remapSources(Project project, File input, File output, boolean named) throws Exception {
		SourceRemapper sourceRemapper = new SourceRemapper(project, named);
		sourceRemapper.scheduleRemapSources(input, output);
		sourceRemapper.remapAll();
	}

	public void scheduleRemapSources(File source, File destination) throws Exception {
		remapTasks.add(() -> {
			try {
				remapSourcesInner(source, destination);
			} catch (Exception e) {
				throw new RuntimeException("Failed to remap sources for " + source, e);
			}
		});
	}

	public void remapAll() {
		if (!remapTasks.isEmpty()) {
			project.getLogger().lifecycle(":remapping sources");
		}

		remapTasks.forEach(Runnable::run);
		// TODO: FIXME - WORKAROUND https://github.com/FabricMC/fabric-loom/issues/45
		System.gc();
	}

	private void remapSourcesInner(File source, File destination) throws Exception {
		project.getLogger().info(":remapping source jar");
		Mercury mercury = getMercuryInstance();

		if (source.equals(destination)) {
			if (source.isDirectory()) {
				throw new RuntimeException("Directories must differ!");
			}

			source = new File(destination.getAbsolutePath().substring(0, destination.getAbsolutePath().lastIndexOf('.')) + "-dev.jar");

			try {
				com.google.common.io.Files.move(destination, source);
			} catch (IOException e) {
				throw new RuntimeException("Could not rename " + destination.getName() + "!", e);
			}
		}

		Path srcPath = source.toPath();
		boolean isSrcTmp = false;

		if (!source.isDirectory()) {
			// create tmp directory
			isSrcTmp = true;
			srcPath = Files.createTempDirectory("fabric-loom-src");
			ZipUtil.unpack(source, srcPath.toFile());
		}

		if (!destination.isDirectory() && destination.exists()) {
			if (!destination.delete()) {
				throw new RuntimeException("Could not delete " + destination.getName() + "!");
			}
		}

		StitchUtil.FileSystemDelegate dstFs = destination.isDirectory() ? null : StitchUtil.getJarFileSystem(destination, true);
		Path dstPath = dstFs != null ? dstFs.get().getPath("/") : destination.toPath();

		try {
			mercury.rewrite(srcPath, dstPath);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap " + source.getName() + " fully!", e);
		}

		copyNonJavaFiles(srcPath, dstPath, project, source);

		if (dstFs != null) {
			dstFs.close();
		}

		if (isSrcTmp) {
			Files.walkFileTree(srcPath, new DeletingFileVisitor());
		}
	}

	private Mercury getMercuryInstance() {
		if (this.mercury != null) {
			return this.mercury;
		}

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		MappingSet mappings = extension.getOrCreateSrcMappingCache(toNamed ? 1 : 0, () -> {
			try {
				TinyTree m = mappingsProvider.getMappings();
				project.getLogger().lifecycle(":loading " + (toNamed ? "srg -> named" : "named -> srg") + " source mappings");
				return new TinyReader(m, toNamed ? "srg" : "named", toNamed ? "named" : "srg").read();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		Mercury mercury = extension.getOrCreateSrcMercuryCache(toNamed ? 1 : 0, () -> {
			Mercury m = createMercuryWithClassPath(project, toNamed);

			for (Path file : extension.getUnmappedMods()) {
				if (Files.isRegularFile(file)) {
					m.getClassPath().add(file);
				}
			}

			m.getClassPath().add(extension.getMinecraftMappedProvider().getMappedJar().toPath());
			m.getClassPath().add(extension.getMinecraftProvider().getSrgForgeJar());

			m.getProcessors().add(MercuryRemapper.create(mappings));

			return m;
		});

		this.mercury = mercury;
		return mercury;
	}

	private static void copyNonJavaFiles(Path from, Path to, Project project, File source) throws IOException {
		Files.walk(from).forEach(path -> {
			Path targetPath = to.resolve(from.relativize(path).toString());

			if (!isJavaFile(path) && !Files.exists(targetPath)) {
				try {
					Files.copy(path, targetPath);
				} catch (IOException e) {
					project.getLogger().warn("Could not copy non-java sources '" + source.getName() + "' fully!", e);
				}
			}
		});
	}

	public static Mercury createMercuryWithClassPath(Project project, boolean toNamed) {
		Mercury m = new Mercury();

		for (File file : project.getConfigurations().getByName(Constants.MINECRAFT_DEPENDENCIES).getFiles()) {
			m.getClassPath().add(file.toPath());
		}

		if (!toNamed) {
			for (File file : project.getConfigurations().getByName("compileClasspath").getFiles()) {
				m.getClassPath().add(file.toPath());
			}
		}

		return m;
	}

	private static boolean isJavaFile(Path path) {
		String name = path.getFileName().toString();
		// ".java" is not a valid java file
		return name.endsWith(".java") && name.length() != 5;
	}

	public static class TinyReader extends MappingsReader {
		private final TinyTree mappings;
		private final String from, to;

		public TinyReader(TinyTree m, String from, String to) {
			this.mappings = m;
			this.from = from;
			this.to = to;
		}

		@Override
		public MappingSet read(final MappingSet mappings) {
			for (ClassDef classDef : this.mappings.getClasses()) {
				ClassMapping classMapping = mappings.getOrCreateClassMapping(classDef.getName(from))
								.setDeobfuscatedName(classDef.getName(to));

				for (FieldDef field : classDef.getFields()) {
					classMapping.getOrCreateFieldMapping(field.getName(from), field.getDescriptor(from))
									.setDeobfuscatedName(field.getName(to));
				}

				for (MethodDef method : classDef.getMethods()) {
					classMapping.getOrCreateMethodMapping(method.getName(from), method.getDescriptor(from))
									.setDeobfuscatedName(method.getName(to));
				}
			}

			return mappings;
		}

		@Override
		public void close() { }
	}
}

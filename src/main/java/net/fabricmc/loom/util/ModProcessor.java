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

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MappedProvider;
import net.fabricmc.loom.providers.LibraryProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

//TODO: what's up with this
public class ModProcessor {
	public static void processMod(File input, File output, Project project, Configuration config, ResolvedArtifact artifact) throws IOException {
		if (output.exists()) {
			output.delete();
		}
		
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		String fromM = "intermediary";
		String toM = "named";
		
		LibraryProvider libraryProvider = extension.getDependencyManager().getLibraryProvider();
		MappedProvider mappedProvider = extension.getDependencyManager().getMappedProvider();
		MappingsProvider mappingsProvider = extension.getDependencyManager().getMappingsProvider();
		
		Path inputPath = input.getAbsoluteFile().toPath();
		Path mc = mappedProvider.getIntermediaryJar();
		Path[] mcDeps = libraryProvider.getNonNativeLibraries().toArray(new Path[0]);
		Set<Path> modCompiles = new HashSet<>();
		
		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			project.getConfigurations().getByName(entry.getSourceConfiguration()).getFiles().stream().filter((f) -> !f.equals(input)).map(p -> {
				if (p.equals(input)) {
					return inputPath;
				} else {
					return p.toPath();
				}
			}).forEach(modCompiles::add);
		}
		
		project.getLogger().lifecycle(":remapping " + input.getName() + " (TinyRemapper, " + fromM + " -> " + toM + ")");
		
		// If the sources don't exist, we want remapper to give nicer names to the missing variable names.
		// However, if the sources do exist, if remapper gives names to the parameters that prevents IDEs (at least IDEA)
		// from replacing the parameters with the actual names from the sources.
		boolean sourcesExist = ModCompileRemapper.findSources(project.getDependencies(), artifact) != null;
		
		TinyRemapper remapper = TinyRemapper.newRemapper()
						.withMappings(TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false))
						.renameInvalidLocals(!sourcesExist)
						.build();
		
		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(Paths.get(output.getAbsolutePath())).build()) {
			outputConsumer.addNonClassFiles(inputPath);
			remapper.readClassPath(modCompiles.toArray(new Path[0]));
			remapper.readClassPath(mc);
			remapper.readClassPath(mcDeps);
			remapper.readInputs(inputPath);
			remapper.apply(outputConsumer);
		} finally {
			remapper.finish();
		}
		
		if (!output.exists()) {
			throw new RuntimeException("Failed to remap JAR to " + toM + " file not found: " + output.getAbsolutePath());
		}
	}
}

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

import java.io.File;
import java.util.Locale;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.decompilers.fernflower.FabricFernFlowerDecompiler;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.task.CleanEclipseRunsTask;
import net.fabricmc.loom.task.CleanLoomBinaries;
import net.fabricmc.loom.task.CleanLoomMappings;
import net.fabricmc.loom.task.DownloadAssetsTask;
import net.fabricmc.loom.task.GenEclipseRunsTask;
import net.fabricmc.loom.task.GenIdeaProjectTask;
import net.fabricmc.loom.task.GenVsCodeProjectTask;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.task.MigrateMappingsTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.task.RunClientTask;
import net.fabricmc.loom.task.RunServerTask;

public class LoomGradlePlugin extends AbstractPlugin {
	public static File getMappedByproduct(Project project, String suffix) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();
		File mappedJar = extension.getMinecraftMappedProvider().getMappedJar();
		String path = mappedJar.getAbsolutePath();

		if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new RuntimeException("Invalid mapped JAR path: " + path);
		}

		return new File(path.substring(0, path.length() - 4) + suffix);
	}

	@Override
	public void apply(Project target) {
		super.apply(target);

		TaskContainer tasks = target.getTasks();

		tasks.register("cleanLoomBinaries", CleanLoomBinaries.class, t -> t.setDescription("Removes binary jars created by Loom."));
		tasks.register("cleanLoomMappings", CleanLoomMappings.class, t -> t.setDescription("Removes mappings downloaded by Loom."));

		tasks.register("cleanLoom").configure(task -> {
			task.setGroup("fabric");
			task.setDescription("Runs all Loom cleanup tasks.");
			task.dependsOn(tasks.getByName("cleanLoomBinaries"));
			task.dependsOn(tasks.getByName("cleanLoomMappings"));
		});

		tasks.register("migrateMappings", MigrateMappingsTask.class, t -> {
			t.setDescription("Migrates mappings to a new version.");
			t.getOutputs().upToDateWhen((o) -> false);
		});

		tasks.register("remapJar", RemapJarTask.class, t -> {
			t.setDescription("Remaps the built project jar to intermediary mappings.");
			t.setGroup("fabric");
		});

		tasks.register("downloadAssets", DownloadAssetsTask.class, t -> t.setDescription("Downloads required assets for Fabric."));

		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, t -> {
			t.setDescription("Generates an IntelliJ IDEA workspace from this project.");
			t.dependsOn("idea", "downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("genEclipseRuns", GenEclipseRunsTask.class, t -> {
			t.setDescription("Generates Eclipse run configurations for this project.");
			t.dependsOn("downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("cleanEclipseRuns", CleanEclipseRunsTask.class, t -> {
			t.setDescription("Removes Eclipse run configurations for this project.");
			t.setGroup("ide");
		});

		tasks.register("vscode", GenVsCodeProjectTask.class, t -> {
			t.setDescription("Generates VSCode launch configurations.");
			t.dependsOn("downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("remapSourcesJar", RemapSourcesJarTask.class, t -> t.setDescription("Remaps the project sources jar to intermediary names."));

		tasks.register("runClient", RunClientTask.class, t -> {
			t.setDescription("Starts a development version of the Minecraft client.");
			t.dependsOn("jar", "downloadAssets");
			t.setGroup("fabric");
		});

		tasks.register("runServer", RunServerTask.class, t -> {
			t.setDescription("Starts a development version of the Minecraft server.");
			t.dependsOn("jar");
			t.setGroup("fabric");
		});

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		extension.addDecompiler(new FabricFernFlowerDecompiler(project));

		project.afterEvaluate((p) -> {
			for (LoomDecompiler decompiler : extension.decompilers) {
				String taskName = (decompiler instanceof FabricFernFlowerDecompiler) ? "genSources" : "genSourcesWith" + decompiler.name();
				// decompiler will be passed to the constructor of GenerateSourcesTask
				tasks.register(taskName, GenerateSourcesTask.class, decompiler);
			}
		});
	}
}

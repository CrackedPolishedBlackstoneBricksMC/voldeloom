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

import net.fabricmc.loom.forge.mapping.AcceptorProvider;
import net.fabricmc.loom.forge.mapping.CsvApplierAcceptor;
import net.fabricmc.loom.forge.mapping.SrgMappingProvider;
import net.fabricmc.loom.forge.mapping.TinyWriter3Column;
import net.fabricmc.loom.processors.JarProcessorManager;
import net.fabricmc.loom.processors.MinecraftProcessedProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.VoldeloomFileHelpers;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

public class MappingsProvider extends DependencyProvider {
	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	
	public MinecraftMappedProvider mappedProvider;

	public String mappingsName;
	public String minecraftVersion;
	public String mappingsVersion;

	private Path mappingsDir;

	// The mappings we use in practice
	public File tinyMappings;
	public File tinyMappingsJar;
	public File mappingsMixinExport;

	public MappingsProvider(Project project) {
		super(project);
	}

	public void clean() {
		VoldeloomFileHelpers.delete(getProject(), mappingsDir);
	}

	public TinyTree getMappings() throws IOException {
		return MappingsCache.INSTANCE.get(tinyMappings.toPath());
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		MinecraftProvider minecraftProvider = getDependencyManager().getProvider(MinecraftProvider.class);

		getProject().getLogger().lifecycle(":setting up mappings (" + dependency.getDependency().getName() + " " + dependency.getResolvedVersion() + ")");

		String version = dependency.getResolvedVersion();
		File mappingsJar = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not find mcp mappings: " + dependency));

		this.mappingsName = StringUtils.removeSuffix(dependency.getDependency().getGroup() + "." + dependency.getDependency().getName(), "-unmerged");

		this.minecraftVersion = minecraftProvider.getJarStuff();
		this.mappingsVersion = version;

		initFiles();

		String[] depStringSplit = dependency.getDepString().split(":");
		String jarClassifier = "final";

		if (depStringSplit.length >= 4) {
			jarClassifier = jarClassifier + depStringSplit[3];
		}

		tinyMappings = mappingsDir.resolve(StringUtils.removeSuffix(mappingsJar.getName(), ".jar") + ".tiny").toFile();
		tinyMappingsJar = new File(getExtension().getUserCache(), mappingsJar.getName().replace(".jar", "-" + jarClassifier + ".jar"));
		
		if (!tinyMappings.exists()) {
			long filesize;
			try {
				filesize = Files.size(mappingsJar.toPath());
			} catch (Exception e) {
				throw new RuntimeException("Problem statting mappings zip", e);
			}
			if(filesize == 0) {
				throw new RuntimeException("The mappings zip at " + mappingsJar.toPath() + " is a 0-byte file. Please double-check the URL and redownload. " +
					"If you obtained this from the Internet Archive, note that it likes to return 0-byte files instead of 404 errors.");
			}
			
			try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + mappingsJar.toURI()), FS_ENV)) {
				Pair<Map<String, String>, Collection<String>> data = SrgMappingProvider.calcInfo(minecraftProvider.getMergedJar());
				SrgMappingProvider client = new SrgMappingProvider(mcpZipFs.getPath("conf", "client.srg"), data.getLeft(), data.getRight());
				SrgMappingProvider server = new SrgMappingProvider(mcpZipFs.getPath("conf", "server.srg"), data.getLeft(), data.getRight());
				Path notMyAwfulHack = mcpZipFs.getPath("conf", "newids.csv");
				AcceptorProvider merged = new AcceptorProvider();
				client.load(new CsvApplierAcceptor(merged, notMyAwfulHack, CsvApplierAcceptor.NEWNAME_CLIENT_IN, CsvApplierAcceptor.NEWNAME_OUT));
				server.load(new CsvApplierAcceptor(merged, notMyAwfulHack, CsvApplierAcceptor.NEWNAME_SERVER_IN, CsvApplierAcceptor.NEWNAME_OUT));
				Path notMyAwfulHack2 = mcpZipFs.getPath("conf", "packages.csv");
				AcceptorProvider packaged = new AcceptorProvider();
				merged.load(new CsvApplierAcceptor(packaged, notMyAwfulHack2, CsvApplierAcceptor.PACKAGES_IN, CsvApplierAcceptor.PACKAGES_OUT));
				
				TinyWriter3Column writer = new TinyWriter3Column("official", "intermediary", "named");
				packaged.load(writer);
				writer.acceptSecond();
				MappingAcceptor fieldMapper = new CsvApplierAcceptor(writer, mcpZipFs.getPath("conf", "fields.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
				MappingAcceptor methodMapper = new CsvApplierAcceptor(fieldMapper, mcpZipFs.getPath("conf", "methods.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
				packaged.load(methodMapper);
				mappingsDir.toFile().mkdirs();
				tinyMappings.createNewFile();
				try(OutputStream out = new BufferedOutputStream(new FileOutputStream(tinyMappings))) {
					writer.write(out);
				}
			}
		}

		if (!tinyMappingsJar.exists()) {
			ZipUtil.pack(new ZipEntrySource[] {new FileSource("mappings/mappings.tiny", tinyMappings)}, tinyMappingsJar);
		}

		addDependency(tinyMappingsJar, Constants.MAPPINGS_FINAL);

		JarProcessorManager processorManager = new JarProcessorManager(getProject());
		getExtension().setJarProcessorManager(processorManager);

		if (processorManager.active()) {
			mappedProvider = new MinecraftProcessedProvider(getProject(), processorManager);
			getProject().getLogger().lifecycle("Using project based jar storage");
		} else {
			mappedProvider = new MinecraftMappedProvider(getProject());
		}

		mappedProvider.initFiles(minecraftProvider, this);
		mappedProvider.provide(dependency, postPopulationScheduler);
	}

	public static void extractMappings(FileSystem jar, Path extractTo) throws IOException {
		Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
	}
	
	private void initFiles() {
		mappingsDir = getExtension().getUserCache().toPath().resolve("mappings");
		mappingsMixinExport = new File(getExtension().getProjectBuildCache(), "mixin-map-" + minecraftVersion + "-" + mappingsVersion + ".tiny");
	}

	@Override
	public String getTargetConfig() {
		return Constants.MAPPINGS;
	}
}

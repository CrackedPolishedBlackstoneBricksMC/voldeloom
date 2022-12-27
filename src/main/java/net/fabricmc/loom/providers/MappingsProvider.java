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
import net.fabricmc.loom.forge.mapping.AcceptorProvider;
import net.fabricmc.loom.forge.mapping.CsvApplierAcceptor;
import net.fabricmc.loom.forge.mapping.SrgMappingProvider;
import net.fabricmc.loom.forge.mapping.TinyWriter3Column;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.VoldeloomFileHelpers;
import net.fabricmc.loom.util.WellKnownLocations;
import net.fabricmc.mapping.tree.TinyMappingFactory;
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
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class MappingsProvider extends DependencyProvider {
	public MappingsProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc, ForgePatchedProvider forgePatched) {
		super(project, extension);
		this.mc = mc;
		this.forgePatched = forgePatched;
	}
	
	private final MinecraftProvider mc;
	private final ForgePatchedProvider forgePatched;
	
	public String mappingsName;
	public String minecraftVersion;
	public String mappingsVersion;

	private Path mappingsDir;

	public File tinyMappings;
	public File tinyMappingsJar;
	
	private TinyTree parsedMappings;

	

	//TODO: let's try and make this into a project-wide `clean` system that threads through each task,
	// instead of an ad-hoc thing
	public void clean() {
		VoldeloomFileHelpers.delete(project, mappingsDir);
	}

	@Override
	public void decorateProject() throws Exception {
		//deps
		DependencyInfo mappingsDependency = getSingleDependency(Constants.MAPPINGS);
		
		project.getLogger().lifecycle("|-> setting up mappings (" + mappingsDependency.getDependency().getName() + " " + mappingsDependency.getResolvedVersion() + ")");

		String version = mappingsDependency.getResolvedVersion();
		File mappingsJar = mappingsDependency.resolveSingleFile().orElseThrow(() -> new RuntimeException("Could not find mcp mappings: " + mappingsDependency));

		this.mappingsName = StringUtils.removeSuffix(mappingsDependency.getDependency().getGroup() + "." + mappingsDependency.getDependency().getName(), "-unmerged");

		this.minecraftVersion = mc.getJarStuff();
		this.mappingsVersion = version;
		
		this.mappingsDir = WellKnownLocations.getUserCache(project).toPath().resolve("mappings");

		String[] depStringSplit = mappingsDependency.getDepString().split(":");
		String jarClassifier = "final";

		if (depStringSplit.length >= 4) {
			jarClassifier = jarClassifier + depStringSplit[3];
		}

		tinyMappings = mappingsDir.resolve(StringUtils.removeSuffix(mappingsJar.getName(), ".jar") + ".tiny").toFile();
		tinyMappingsJar = new File(WellKnownLocations.getUserCache(project), mappingsJar.getName().replace(".jar", "-" + jarClassifier + ".jar"));
		
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
			
			try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + mappingsJar.toURI()), Collections.singletonMap("create", "true"))) {
				Pair<Map<String, String>, Collection<String>> data = SrgMappingProvider.calcInfo(forgePatched.getPatchedJar());
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
		
		//make them available for other tasks TODO move
		parsedMappings = TinyMappingFactory.loadWithDetection(Files.newBufferedReader(tinyMappings.toPath()));
		
		//add it as a project dependency TODO move
		project.getDependencies().add(Constants.MAPPINGS_FINAL, project.files(tinyMappingsJar));
	}
	
	public TinyTree getMappings() {
		return parsedMappings;
	}
}

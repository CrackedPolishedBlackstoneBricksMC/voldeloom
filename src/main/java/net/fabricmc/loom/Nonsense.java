package net.fabricmc.loom;

import com.google.gson.Gson;
import net.fabricmc.loom.forge.ForgeATConfig;
import net.fabricmc.loom.forge.ForgePatchApplier;
import net.fabricmc.loom.forge.mapping.AcceptorProvider;
import net.fabricmc.loom.forge.mapping.CsvApplierAcceptor;
import net.fabricmc.loom.forge.mapping.SrgMappingProvider;
import net.fabricmc.loom.forge.mapping.TinyWriter3Column;
import net.fabricmc.loom.processors.JarProcessorManager;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.WellKnownLocations;
import net.fabricmc.stitch.merge.JarMerger;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.tinyremapper.IMappingProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Nonsense {
	public Nonsense(Project project, LoomGradleExtension extension) throws Exception {
		ConfigurationContainer cfg = project.getConfigurations();
		
		Path userCache = WellKnownLocations.getUserCache(project).toPath();
		Path mappingsCache = userCache.resolve("mappings");
		
		project.getLogger().lifecycle("-> resolving Forge");
		ResolvedArtifact stockForge = resolveSingleArtifact(cfg.getByName(Constants.FORGE));
		project.getLogger().lifecycle("\\-> found Forge " + stockForge.version);
		
		project.getLogger().lifecycle("-> parsing Forge access transformers");
		ForgeATConfig forgeATConfig = new ForgeATConfig();
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + stockForge.path), Collections.emptyMap())) {
			forgeATConfig.load(Files.newInputStream(zipFs.getPath("fml_at.cfg")));
			forgeATConfig.load(Files.newInputStream(zipFs.getPath("forge_at.cfg")));
		}
		
		//we don't care about its artifact though, it will be downloaded from version manifest
		project.getLogger().lifecycle("-> resolving vanilla Minecraft");
		Dependency stockMinecraftDependency = resolveSingleDependency(cfg.getByName(Constants.MINECRAFT));
		String minecraftVersion = stockMinecraftDependency.getVersion();
		project.getLogger().lifecycle("\\-> found Minecraft " + minecraftVersion);
		
		String jarStuff = minecraftVersion + "forge" + stockForge.version;
		Path minecraftJson = userCache.resolve("minecraft-" + minecraftVersion + "-info.json");
		Path minecraftClientJar = userCache.resolve("minecraft-" + minecraftVersion + "-client.jar");
		Path minecraftServerJar = userCache.resolve("minecraft-" + minecraftVersion + "-server.jar");
		Path minecraftMergedJar = userCache.resolve("minecraft-" + minecraftVersion + "-merged.jar");
		Path minecraftPatchedMergedJar = userCache.resolve("minecraft-" + jarStuff + "-merged.jar");
		
		/// TODO: DOWNLOADING MINECRAFT, PARSING MANIFEST JSON, ETC ///
		//   here i will just assume it exists somehow magically
		
		project.getLogger().lifecycle("-> downloading Minecraft version manifest (todo)");
		project.getLogger().lifecycle("-> parsing version data");
		MinecraftVersionInfo versionInfo;
		try(BufferedReader reader = Files.newBufferedReader(minecraftJson)) {
			versionInfo = new Gson().fromJson(reader, MinecraftVersionInfo.class);
		}
		
		project.getLogger().lifecycle("-> iterating Minecraft's libraries");
		//TODO: Never written to in original Voldeloom
		Collection<File> minecraftNonNativeLibs = new HashSet<>();
		File minecraftLibs = new File(WellKnownLocations.getUserCache(project), "libraries");
		for(MinecraftVersionInfo.Library library : versionInfo.libraries) {
			if(library.allowed() && !library.isNative() && library.getFile(minecraftLibs) != null) {
				if(library.getArtifactName().contains("org.ow2.asm")) {
					//voldeloom: conflicts with forge's ASM 4 dep
					continue;
				}
				
				minecraftNonNativeLibs.add(library.getFile(minecraftLibs));
				
				//TODO: is here the best spot?
				project.getDependencies().add(Constants.MINECRAFT_DEPENDENCIES, project.getDependencies().module(library.getArtifactName()));
			}
		}
		
		project.getLogger().lifecycle("-> merging Minecraft client and server jars");
		if(Files.notExists(minecraftMergedJar)) {
			try(JarMerger merger = new JarMerger(minecraftClientJar.toFile(), minecraftServerJar.toFile(), minecraftMergedJar.toFile())) {
				merger.enableSyntheticParamsOffset();
				merger.merge();
			} catch (Exception e) {
				DownloadUtil.delete(minecraftClientJar.toFile());
				DownloadUtil.delete(minecraftServerJar.toFile());
				throw new RuntimeException("Could not merge JARs! Deleting source JARs - please re-run the command and move on.", e);
			}
		}
		
		project.getLogger().lifecycle("-> oh! almost forgot to delete META-INF");
		if(Files.notExists(minecraftPatchedMergedJar)) {
			Files.copy(minecraftMergedJar, minecraftPatchedMergedJar);
			ForgePatchApplier.process(minecraftPatchedMergedJar.toFile(), extension);
		}
		
		project.getLogger().lifecycle("-> resolving mappings artifact");
		ResolvedArtifact stockMappings = resolveSingleArtifact(cfg.getByName(Constants.MAPPINGS));
		project.getLogger().lifecycle("\\-> found mappings " + stockMappings.version); //todo need to steal more of the fabric thing
		String mappingsName = "hardcoded-mappings-name"; //TODO lol
		
		project.getLogger().lifecycle("-> crushing mappings into a fine powder");
		Path tinyMappings = mappingsCache.resolve(mappingsName + ".tiny");
		if(Files.notExists(tinyMappings)) {
			long filesize;
			try {
				filesize = Files.size(stockMappings.path);
			} catch (Exception e) {
				throw new RuntimeException("Problem statting mappings zip", e);
			}
			if(filesize == 0) {
				throw new RuntimeException("The mappings zip at " + stockMappings.path + " is a 0-byte file. Please double-check the URL and redownload. " +
					"If you obtained this from the Internet Archive, note that it likes to return 0-byte files instead of 404 errors.");
			}
			
			try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + stockMappings.path), Collections.singletonMap("create", "true"))) {
				Pair<Map<String, String>, Collection<String>> data = SrgMappingProvider.calcInfo(minecraftMergedJar.toFile());
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
				IMappingProvider.MappingAcceptor fieldMapper = new CsvApplierAcceptor(writer, mcpZipFs.getPath("conf", "fields.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
				IMappingProvider.MappingAcceptor methodMapper = new CsvApplierAcceptor(fieldMapper, mcpZipFs.getPath("conf", "methods.csv"), CsvApplierAcceptor.GENERIC_IN, CsvApplierAcceptor.GENERIC_OUT);
				packaged.load(methodMapper);
				
				try(OutputStream out = new BufferedOutputStream(Files.newOutputStream(tinyMappings))) {
					writer.write(out);
				}
			}
		}
		
		project.getLogger().lifecycle("-> packaging mappings into a jar");
		Path tinyMappingsJar = mappingsCache.resolve(mappingsName + ".tiny.jar");
		if(Files.notExists(tinyMappingsJar)) {
			ZipUtil.pack(new ZipEntrySource[] {new FileSource("mappings/mappings.tiny", tinyMappings.toFile())}, tinyMappingsJar.toFile());
		}
		
		project.getDependencies().add(Constants.MAPPINGS_FINAL, project.file(tinyMappingsJar));
		
		project.getLogger().lifecycle("-> configuring jar processors");
		JarProcessorManager processor = new JarProcessorManager(project);
		
		//blah, blah blah
		//TODO run jar processors (probably dont need a processor manager)
		//TODO the rest of the setup process
		//TODO find what needs to be made available from here
	}
	
	private static class ResolvedArtifact {
		public ResolvedArtifact(Path path, String version) {
			this.path = path;
			this.version = version;
		}
		
		public final Path path;
		public final String version;
	}
	
	private static ResolvedArtifact resolveSingleArtifact(Configuration config) {
		Set<File> files = config.resolve();
		if(files.isEmpty()) {
			throw new IllegalStateException("Expected one artifact to be found in configuration '" + config.getName() + "', found zero.");
		} else if(files.size() == 1) {
			
			DependencySet deps = config.getDependencies();
			if(deps.size() == 1) {
				return new ResolvedArtifact(files.iterator().next().toPath(), deps.iterator().next().getVersion());
			} else {
				//todo make this less shite
				throw new IllegalStateException("Expected one dependency to be found in configuration '" + config.getName() + "', found not that, idk debug this");
			}
		} else {
			StringBuilder builder = new StringBuilder("Expected one artifact to be found in configuration '");
			builder.append(config.getName());
			builder.append("', found ");
			builder.append(files.size());
			builder.append(":");
			
			for (File f : files) {
				builder.append("\n\t-").append(f.getAbsolutePath());
			}
			
			throw new IllegalStateException(builder.toString());
		}
	}
	
	private static Dependency resolveSingleDependency(Configuration config) {
		DependencySet deps = config.getDependencies();
		if(deps.size() == 1) {
			return deps.iterator().next();
		} else {
			//todo make this less shite
			throw new IllegalStateException("Expected one dependency to be found in configuration '" + config.getName() + "', found not that, idk debug this");
		}
	}
}

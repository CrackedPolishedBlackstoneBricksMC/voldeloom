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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Consumer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.cadixdev.atlas.Atlas;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgMappingFormat;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import net.fabricmc.loom.forge.InstallerUtil;
import net.fabricmc.loom.processors.JarProcessorManager;
import net.fabricmc.loom.processors.MinecraftProcessedProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DeletionFileVisitor;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.ManifestVersion;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.StaticPathWatcher;

public class MinecraftProvider extends DependencyProvider {
	private MinecraftMappedProvider mappedProvider;
	private MappingsProvider mappingsProvider;
	private String minecraftVersion;

	private MinecraftVersionInfo versionInfo;
	private MinecraftLibraryProvider libraryProvider;

	private File minecraftJson;
	private Path minecraftClientJar;
	private Path minecraftClientSrg;
	private Path minecraftForgeJar;
	private Path minecraftMergedSrg;
	private Path minecraftMergedJar;

	private ForgeProvider patchProvider;
	private McpConfigProvider mcpConfigProvider;

	Gson gson = new Gson();

	public MinecraftProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		minecraftVersion = dependency.getDependency().getVersion();
		boolean offline = getProject().getGradle().getStartParameter().isOffline();

		initFiles();

		downloadMcJson(offline);

		try (FileReader reader = new FileReader(minecraftJson)) {
			versionInfo = gson.fromJson(reader, MinecraftVersionInfo.class);
		}

		// Add Loom as an annotation processor
		addDependency(getProject().files(this.getClass().getProtectionDomain().getCodeSource().getLocation()),
				"compileOnly");

		if (offline) {
			if (Files.exists(minecraftClientJar)) {
				getProject().getLogger().debug("Found client jar, presuming up-to-date");
			} else if (Files.exists(minecraftMergedJar)) {
				// Strictly we don't need the split jars if the merged one exists, let's try go
				// on
				getProject().getLogger().warn("Missing game jar but merged jar present, things might end badly");
			} else {
				throw new GradleException("Missing jar(s); Client: " + Files.exists(minecraftClientJar));
			}
		} else {
			downloadJars(getProject().getLogger());
		}

		libraryProvider = new MinecraftLibraryProvider();
		libraryProvider.provide(this, getProject());

		// this is depended on by the Forge lib remapper, make sure it exists
		if(!Files.exists(minecraftMergedSrg)) {
			if (!Files.exists(minecraftForgeJar)) {
				getProject().getLogger().lifecycle(":installing Forge");
				Path forgeLibrariesDir = getExtension().getUserCache().toPath().resolve("forge-libs");
				InstallerUtil.clientInstall(forgeLibrariesDir, minecraftClientJar, patchProvider.getInstaller());
				Path forgeOutput = forgeLibrariesDir.resolve("net").resolve("minecraftforge").resolve("forge")
						.resolve(patchProvider.getForgeVersion())
						.resolve(String.format("forge-%s-%s.jar", patchProvider.getForgeVersion(), "client"));
				Files.copy(forgeOutput, minecraftForgeJar, StandardCopyOption.REPLACE_EXISTING);
			}
			if(!Files.exists(minecraftClientSrg)) {
				getProject().getLogger().lifecycle(":remapping Minecraft (CadixDev, official -> srg)");
				remapSrg(minecraftClientJar, minecraftClientSrg, false);
			}
		
			getProject().getLogger().lifecycle(":moving Forge-modified classes..");
			Files.copy(minecraftClientSrg, minecraftMergedSrg);
			
			try (FileSystem forgeFs = FileSystems.newFileSystem(minecraftForgeJar, null)) {
				try (FileSystem mergedFs = FileSystems.newFileSystem(minecraftMergedSrg, null)) {
					Files.walk(forgeFs.getPath("/")).forEach(p -> {
						try {
							if(Files.isRegularFile(p)) {
								Files.copy(p, mergedFs.getPath(p.toString()), StandardCopyOption.REPLACE_EXISTING);
							}
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					});
					Files.walkFileTree(mergedFs.getPath("META-INF"), new DeletionFileVisitor());
				}
			}
		}
		
		if (!Files.exists(minecraftMergedJar)) {
			getProject().getLogger().lifecycle(":remapping Minecraft (CadixDev, srg -> official)");
			remapSrg(minecraftMergedSrg, minecraftMergedJar, true);
		}

		JarProcessorManager processorManager = new JarProcessorManager(getProject());
		getExtension().setJarProcessorManager(processorManager);

		if (processorManager.active()) {
			mappedProvider = new MinecraftProcessedProvider(getProject(), processorManager);
			getProject().getLogger().lifecycle("Using project based jar storage");
		} else {
			mappedProvider = new MinecraftMappedProvider(getProject());
		}

		mappedProvider.initFiles(this, mappingsProvider);
		mappedProvider.provide(dependency, postPopulationScheduler);
	}

	private void initFiles() {
		patchProvider = getExtension().getDependencyManager().getProvider(ForgeProvider.class);
		mcpConfigProvider = getExtension().getDependencyManager().getProvider(McpConfigProvider.class);
		mappingsProvider = getExtension().getMappingsProvider();
		mappingsProvider.minecraftVersion = minecraftVersion;
		minecraftJson = new File(getExtension().getUserCache(), "minecraft-" + minecraftVersion + "-info.json");
		minecraftClientJar = getJarPathNoForge("client");
		minecraftClientSrg = getJarPathNoForge("client-srg");
		minecraftForgeJar = getJarPath("forgeonly-patched-srg");
		minecraftMergedSrg = getJarPath("merged-patched-srg");
		minecraftMergedJar = getJarPath("merged-patched");
	}

	private Path getJarPath(String type) {
		return getExtension().getUserCache().toPath().resolve(
				String.format("minecraft-%s-forge-%s-%s.jar", minecraftVersion, patchProvider.getForgeVersion(), type));
	}

	private Path getJarPathNoForge(String type) {
		return getExtension().getUserCache().toPath()
				.resolve(String.format("minecraft-%s-%s.jar", minecraftVersion, type));
	}

	private void downloadMcJson(boolean offline) throws IOException {
		File manifests = new File(getExtension().getUserCache(), "version_manifest.json");

		if (offline) {
			if (manifests.exists()) {
				// If there is the manifests already we'll presume that's good enough
				getProject().getLogger().debug("Found version manifests, presuming up-to-date");
			} else {
				// If we don't have the manifests then there's nothing more we can do
				throw new GradleException("Version manifests not found at " + manifests.getAbsolutePath());
			}
		} else {
			getProject().getLogger().debug("Downloading version manifests");
			DownloadUtil.downloadIfChanged(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"),
					manifests, getProject().getLogger());
		}

		// name conflict with nio files
		String versionManifest = com.google.common.io.Files.asCharSource(manifests, StandardCharsets.UTF_8).read();
		ManifestVersion mcManifest = new GsonBuilder().create().fromJson(versionManifest, ManifestVersion.class);

		Optional<ManifestVersion.Versions> optionalVersion = Optional.empty();

		if (getExtension().customManifest != null) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = minecraftVersion;
			customVersion.url = getExtension().customManifest;
			optionalVersion = Optional.of(customVersion);
			getProject().getLogger().lifecycle("Using custom minecraft manifest");
		}

		if (!optionalVersion.isPresent()) {
			optionalVersion = mcManifest.versions.stream()
					.filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();
		}

		if (optionalVersion.isPresent()) {
			if (offline) {
				if (minecraftJson.exists()) {
					// If there is the manifest already we'll presume that's good enough
					getProject().getLogger().debug("Found Minecraft {} manifest, presuming up-to-date",
							minecraftVersion);
				} else {
					// If we don't have the manifests then there's nothing more we can do
					throw new GradleException("Minecraft " + minecraftVersion + " manifest not found at "
							+ minecraftJson.getAbsolutePath());
				}
			} else {
				if (StaticPathWatcher.INSTANCE.hasFileChanged(minecraftJson.toPath())) {
					getProject().getLogger().debug("Downloading Minecraft {} manifest", minecraftVersion);
					DownloadUtil.downloadIfChanged(new URL(optionalVersion.get().url), minecraftJson,
							getProject().getLogger());
				}
			}
		} else {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}
	}

	private void downloadJars(Logger logger) throws IOException {
		DownloadUtil.downloadIfChanged(new URL(versionInfo.downloads.get("client").url), minecraftClientJar.toFile(),
				logger);
	}

	private Path[] getLibraryArray() {
		return getLibraryProvider().getLibraries().stream().map(File::toPath).toArray(Path[]::new);
	}

	private void remapSrg(Path input, Path output, boolean reverse) throws IOException {
		try (Atlas atlas = new Atlas()) {
			MappingSet mappings = new TSrgMappingFormat().read(mcpConfigProvider.getTsrgPath());
			if(reverse) {
				mappings = mappings.reverse();
			}
			final MappingSet javaPls = mappings;
			atlas.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(javaPls, ctx.inheritanceProvider())));
			for(Path lib : getLibraryArray()) {
				atlas.use(lib);
			}
			atlas.run(input, output);
		}
	}

	public File getMergedJar() {
		return minecraftMergedJar.toFile();
	}

	public String getMinecraftVersion() {
		return minecraftVersion;
	}

	public MinecraftVersionInfo getVersionInfo() {
		return versionInfo;
	}

	public MinecraftLibraryProvider getLibraryProvider() {
		return libraryProvider;
	}

	public MinecraftMappedProvider getMappedProvider() {
		return mappedProvider;
	}
	
	public Path getSrgForgeJar() {
		return minecraftMergedSrg;
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT;
	}
}

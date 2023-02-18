package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.VersionManifest;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the tangle of DependencyProviders.
 */
public class ProviderGraph {
	public ProviderGraph(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.log = project.getLogger();
		this.extension = extension;
	}
	
	private final Project project;
	private final Logger log;
	private final LoomGradleExtension extension;
	private final Map<Class<? extends NewProvider<?>>, NewProvider<?>> newProviders = new HashMap<>();
	
	//for better or for worse, "globals". these ones get set early,
	public ConfigElementWrapper mc;
	public ResolvedConfigElementWrapper forge;
	public MappingsWrapper mappings;
	
	//and these ones get set a little later, but still within the setup() method. so they should be ok to use from outside.
	public VersionManifest versionManifest;
	public Path mcNativesDir;
	public Collection<Path> mcNonNativeDependencies_Todo;
	
	public ProviderGraph setup() throws Exception {
		log.lifecycle("# Wrapping basic dependencies...");
		mc = new ConfigElementWrapper(project, project.getConfigurations().getByName(Constants.MINECRAFT));
		forge = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE));
		
		log.lifecycle("# Parsing mappings...");
		mappings = new MappingsWrapper(project, extension, project.getConfigurations().getByName(Constants.MAPPINGS));
		
		log.lifecycle("# Fetching vanilla jars and indexes...");
		String mcPrefix = "minecraft-" + mc.getFilenameSafeVersion();
		VanillaJarFetcher vanillaJars = new VanillaJarFetcher(project, extension)
			.mc(mc)
			.customManifestUrl(extension.customManifestUrl)
			.clientFilename(mcPrefix + "-client.jar")
			.serverFilename(mcPrefix + "-server.jar")
			.fetch();
		
		versionManifest = vanillaJars.getVersionManifest();
		
		log.lifecycle("# Fetching vanilla dependencies...");
		VanillaDependencyFetcher vanillaDeps = new VanillaDependencyFetcher(project, extension)
			.superProjectmapped(vanillaJars.isProjectmapped())
			.manifest(versionManifest)
			.librariesBaseUrl(extension.librariesBaseUrl)
			.nativesDirname(mc.getFilenameSafeVersion())
			.fetch()
			.installDependenciesToProject(Constants.MINECRAFT_DEPENDENCIES, project.getDependencies());
		
		mcNativesDir = vanillaDeps.getNativesDir();
		mcNonNativeDependencies_Todo = vanillaDeps.getNonNativeLibraries_Todo();
		
		log.lifecycle("# Fetching Forge's dependencies...");
		ForgeDependencyFetcher forgeDeps = new ForgeDependencyFetcher(project, extension)
			.forgeJar(forge.getPath())
			.fmlLibrariesBaseUrl(extension.fmlLibrariesBaseUrl)
			.extractedLibrariesDirname(forge.getFilenameSafeDepString())
			.bouncycastleCheat(extension.forgeCapabilities.computeBouncycastleCheat())
			.sniff()
			.fetch()
			.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies());
		
		log.lifecycle("# Binpatching?");
		Path binpatchedClient, binpatchedServer;
		BinpatchLoader binpatchLoader = new BinpatchLoader(project, extension)
			.forgeJar(forge.getPath())
			.load();
		
		if(binpatchLoader.hasBinpatches()) {
			log.lifecycle("## Binpatching.");
			binpatchedClient = new Binpatcher(project, extension)
				.superProjectmapped(vanillaJars.isProjectmapped() | binpatchLoader.isProjectmapped())
				.input(vanillaJars.getClientJar())
				.outputFilename(mcPrefix + "-client-binpatched.jar")
				.binpatches(binpatchLoader.getBinpatches().clientBinpatches)
				.patch()
				.getOutput();
			
			binpatchedServer = new Binpatcher(project, extension)
				.superProjectmapped(vanillaJars.isProjectmapped() | binpatchLoader.isProjectmapped())
				.input(vanillaJars.getServerJar())
				.outputFilename(mcPrefix + "-server-binpatched.jar")
				.binpatches(binpatchLoader.getBinpatches().serverBinpatches)
				.patch()
				.getOutput();
		} else {
			log.lifecycle("## Nope.");
			binpatchedClient = vanillaJars.getClientJar();
			binpatchedServer = vanillaJars.getServerJar();
		}
		
		//TODO 1.2.5 split: - cut here? (nope, i think it's earlier)
		
		log.lifecycle("# Merging client and server...");
		Merger merger = new Merger(project, extension)
			.superProjectmapped(vanillaJars.isProjectmapped() | binpatchLoader.isProjectmapped())
			.client(binpatchedClient)
			.server(binpatchedServer)
			.mergedFilename(mcPrefix + "-merged.jar")
			.merge();
		
		//TODO: Post-1.6, i think installing it like a jarmod is not strictly correct. Forge should get on the classpath some other way
		log.lifecycle("# Jarmodding...");
		
		String jarmoddedPrefix = mcPrefix + "-forge-" + forge.getFilenameSafeVersion();
		
		Jarmodder jarmod = new Jarmodder(project, extension)
			.superProjectmapped(merger.isProjectmapped())
			.base(merger.getMerged())
			.overlay(forge.getPath())
			.jarmoddedFilename(jarmoddedPrefix + "-jarmod.jar")
			.patch();
		
		log.lifecycle("# Applying access transformers...");
		AccessTransformer transformer = new AccessTransformer(project, extension)
			.superProjectmapped(jarmod.isProjectmapped())
			.loadCustomAccessTransformers();
		
		String accessTransformedPrefix = jarmoddedPrefix + "-atd";
		@Nullable String atHash = transformer.getCustomAccessTransformerHash();
		if(atHash != null) accessTransformedPrefix += "-" + atHash;
		
		transformer
			.regularForgeJar(forge.getPath())
			.forgeJarmodded(jarmod.getJarmoddedJar())
			.transformedFilename(accessTransformedPrefix + ".jar")
			.transform();
		
		log.lifecycle("# Converting mappings to tinyv2...");
		Tinifier tinifier = new Tinifier(project, extension)
			.superProjectmapped(transformer.isProjectmapped())
			.jarToScan(transformer.getTransformedJar())
			.mappings(mappings)
			.useSrgsAsFallback(extension.forgeCapabilities.useSrgsAsFallback())
			.tinify();
		
		log.lifecycle("# Remapping Minecraft...");
		Remapper remapper = new Remapper(project, extension)
			.superProjectmapped(tinifier.isProjectmapped() | jarmod.isProjectmapped() | transformer.isProjectmapped())
			.nonNativeLibs(vanillaDeps.getNonNativeLibraries_Todo())
			.intermediaryJarName(mappings.getFilenameSafeDepString(), accessTransformedPrefix + "-" + Constants.INTERMEDIATE_NAMING_SCHEME + ".jar")
			.mappedJarName(      mappings.getFilenameSafeDepString(), accessTransformedPrefix + "-" + Constants.MAPPED_NAMING_SCHEME + ".jar")
			.tinyTree(tinifier.getTinyTree())
			.inputJar(transformer.getTransformedJar())
			.deletedPrefixes(extension.forgeCapabilities.computeClassFilter())
			.remap()
			.installDependenciesToProject(Constants.MINECRAFT_NAMED, project.getDependencies());
		
		log.lifecycle("# Remapping mod dependencies...");
		DependencyRemapper dependencyRemapper = new DependencyRemapper(project, extension)
			.superProjectmapped(remapper.isProjectmapped() | tinifier.isProjectmapped() | transformer.isProjectmapped() | vanillaDeps.isProjectmapped())
			.mappingsSuffix(mappings.getFilenameSafeDepString())
			.tinyTree(tinifier.getTinyTree())
			.remappedConfigurationEntries(extension.remappedConfigurationEntries)
			.distributionNamingScheme(extension.forgeCapabilities.computeDistributionNamingScheme())
			.addToRemapClasspath(transformer.getTransformedJar())
			.addToRemapClasspath(vanillaDeps.getNonNativeLibraries_Todo())
			.remapDependencies()
			.installDependenciesToProject(project.getDependencies());
		
		log.lifecycle("# Configuring asset downloader...");
		AssetDownloader assets = new AssetDownloader(project, extension)
			.assetIndexFilename(versionManifest.assetIndex.getFabricId(mc.getVersion()) + ".json")
			.versionManifest(versionManifest)
			.resourcesBaseUrl(extension.resourcesBaseUrl);
			//.downloadAssets(); //Don't download assets just yet - a gradle task will do this later on the client
		
		makeAvailableToTasks(transformer, tinifier, remapper, assets);
		
		log.lifecycle("# Thank you for flying Voldeloom.");
		
		return this;
	}
	
	public ProviderGraph trySetup() {
		try {
			return setup();
		} catch (Exception e) {
			throw new RuntimeException("Exception setting up Voldeloom: " + e.getMessage(), e);
		}
	}
	
	//TODO: Deprecate this too - expose only the needful things to tasks, and keep providers as an implementation detail...
	// Mainly I'm thinking about 1.2.5 here, which will have parallel versions of the remapping pipeline for client and server,
	// there isn't really a meaning behind "get *the* minecraft jar from *the* remapping manager" - there are two of them
	@Deprecated
	@SuppressWarnings("unchecked")
	private void makeAvailableToTasks(NewProvider<?>... deps) {
		for(NewProvider<?> dep : deps)	newProviders.put((Class<? extends NewProvider<?>>) dep.getClass(), dep);
	}
	
	@Deprecated
	@SuppressWarnings("unchecked")
	public <T extends NewProvider<T>> T get(Class<T> type) {
		return (T) Objects.requireNonNull(newProviders.get(type));
	}
}

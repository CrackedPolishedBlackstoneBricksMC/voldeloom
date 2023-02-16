package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.nio.file.Path;
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
	
	//for better or for worse, "globals"
	public ConfigElementWrapper mc;
	public ResolvedConfigElementWrapper forge;
	public MappingsWrapper mappings;
	
	public ProviderGraph setup() throws Exception {
		log.lifecycle("# Wrapping basic dependencies...");
		mc = new ConfigElementWrapper(project, project.getConfigurations().getByName(Constants.MINECRAFT));
		forge = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE));
		
		log.lifecycle("# Parsing mappings...");
		mappings = new MappingsWrapper(project, extension, project.getConfigurations().getByName(Constants.MAPPINGS));
		
		log.lifecycle("# Fetching vanilla jars and indexes...");
		VanillaJarFetcher vanillaJars = new VanillaJarFetcher(project, extension)
			.mc(mc)
			.customManifestUrl(extension.customManifestUrl)
			.fetch();
		
		log.lifecycle("# Fetching vanilla dependencies...");
		VanillaDependencyFetcher vanillaDeps = new VanillaDependencyFetcher(project, extension)
			.superProjectmapped(vanillaJars.projectmapped)
			.mc(mc)
			.manifest(vanillaJars.getVersionManifest())
			.librariesBaseUrl(extension.librariesBaseUrl)
			.fetch()
			.installDependenciesToProject(Constants.MINECRAFT_DEPENDENCIES, project.getDependencies());
		
		log.lifecycle("# Fetching Forge's dependencies...");
		ForgeDependencyFetcher forgeDeps = new ForgeDependencyFetcher(project, extension)
			.forge(forge)
			.fmlLibrariesBaseUrl(extension.fmlLibrariesBaseUrl)
			.sniff()
			.fetch()
			.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies());
		
		log.lifecycle("# Binpatching?");
		Path binpatchedClient, binpatchedServer;
		BinpatchLoader binpatchLoader = new BinpatchLoader(project, extension)
			.forge(forge)
			.load();
		
		if(binpatchLoader.hasBinpatches()) {
			log.lifecycle("## Binpatching.");
			binpatchedClient = new Binpatcher(project, extension)
				.superProjectmapped(vanillaJars.projectmapped | binpatchLoader.projectmapped)
				.input(vanillaJars.getClientJar())
				.binpatches(binpatchLoader.getBinpatches().clientBinpatches)
				.patch()
				.getOutput();
			
			binpatchedServer = new Binpatcher(project, extension)
				.superProjectmapped(vanillaJars.projectmapped | binpatchLoader.projectmapped)
				.input(vanillaJars.getServerJar())
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
			.superProjectmapped(vanillaJars.projectmapped | binpatchLoader.projectmapped)
			.client(binpatchedClient)
			.server(binpatchedServer)
			.mc(mc)
			.merge();
		
		//TODO: Post-1.6, i think installing it like a jarmod is not strictly correct. Forge should get on the classpath some other way
		log.lifecycle("# Jarmodding...");
		Jarmodder patched = new Jarmodder(project, extension)
			.superProjectmapped(merger.projectmapped)
			.base(merger.getMerged())
			.overlay(forge.getPath())
			.mc(mc)
			.forge(forge)
			.patch();
		
		log.lifecycle("# Applying access transformers...");
		AccessTransformer transformer = new AccessTransformer(project, extension)
			.superProjectmapped(patched.projectmapped)
			.forge(forge)
			.forgeJarmodded(patched.getPatchedJar())
			.patchedVersionTag(patched.getPatchedVersionTag())
			.transform();
		
		log.lifecycle("# Converting mappings to tinyv2...");
		Tinifier tinifier = new Tinifier(project, extension)
			.superProjectmapped(transformer.projectmapped)
			.jarToScan(transformer.getTransformedJar())
			.mappings(mappings)
			.useSrgsAsFallback(extension.forgeCapabilities.useSrgsAsFallback())
			.tinify();
		
		log.lifecycle("# Remapping Minecraft...");
		Remapper remapper = new Remapper(project, extension)
			.superProjectmapped(tinifier.projectmapped | patched.projectmapped | transformer.projectmapped)
			.mappingsDepString(mappings.getMappingsDepString())
			.nonNativeLibs(vanillaDeps.getNonNativeLibraries_Todo())
			.patchedVersionTag(patched.getPatchedVersionTag())
			.tinyTree(tinifier.getTinyTree())
			.inputJar(transformer.getTransformedJar())
			.remap()
			.installDependenciesToProject(Constants.MINECRAFT_NAMED, project.getDependencies());
		
		log.lifecycle("# Remapping mod dependencies...");
		DependencyRemapper dependencyRemapper = new DependencyRemapper(project, extension)
			.superProjectmapped(remapper.projectmapped | tinifier.projectmapped | transformer.projectmapped | vanillaDeps.projectmapped)
			.mappings(mappings)
			.tinyTree(tinifier.getTinyTree()) //todo: both this and `mappings`?
			.remappedConfigurationEntries(extension.remappedConfigurationEntries)
			.distributionNamingScheme(extension.forgeCapabilities.computeDistributionNamingScheme())
			.addToRemapClasspath(transformer.getTransformedJar())
			.addToRemapClasspath(vanillaDeps.getNonNativeLibraries_Todo())
			.remapDependencies()
			.installDependenciesToProject(project.getDependencies());
		
		log.lifecycle("# Configuring asset downloader...");
		AssetDownloader assets = new AssetDownloader(project, extension)
			.mc(mc)
			.versionManifest(vanillaJars.getVersionManifest())
			.resourcesBaseUrl(extension.resourcesBaseUrl)
			.computePaths(); //Don't download assets just yet - a gradle task will do this later on the client
		
		makeAvailableToTasks(vanillaJars, vanillaDeps, forgeDeps, transformer, tinifier, remapper, assets);
		
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

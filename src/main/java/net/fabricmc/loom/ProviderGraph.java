package net.fabricmc.loom;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.newprovider.AccessTransformer;
import net.fabricmc.loom.newprovider.AssetDownloader;
import net.fabricmc.loom.newprovider.BinpatchLoader;
import net.fabricmc.loom.newprovider.Binpatcher;
import net.fabricmc.loom.newprovider.ConfigElementWrapper;
import net.fabricmc.loom.newprovider.DependencyRemapper;
import net.fabricmc.loom.newprovider.ForgeDependencyFetcher;
import net.fabricmc.loom.newprovider.Jarmodder;
import net.fabricmc.loom.newprovider.MappingsWrapper;
import net.fabricmc.loom.newprovider.Merger;
import net.fabricmc.loom.newprovider.Remapper;
import net.fabricmc.loom.newprovider.ResolvedConfigElementWrapper;
import net.fabricmc.loom.newprovider.Tinifier;
import net.fabricmc.loom.newprovider.VanillaDependencyFetcher;
import net.fabricmc.loom.newprovider.VanillaJarFetcher;
import net.fabricmc.loom.task.GenSourcesTask;
import net.fabricmc.loom.util.ThrowyFunction;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

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
	
	//"globals", accessible outside this class for various reasons. i try to keep this surface as small as possible
	
	//couple places would like access to the minecraft version number
	public ConfigElementWrapper mc;
	//run configs would like to know where this directory is
	public Path mcNativesDir;
	//called from ShimAssetsTask, client run configs need to invoke it, not invoked here because assets aren't needed on the server/in CI
	public AssetDownloader assets;
	//package of data used by GenSources
	public final List<GenSourcesTask.SourceGenerationJob> sourceGenerationJobs = new ArrayList<>();
	
	//used by RemapJarTask TODO: FIX, it's not 1.2.5 clean, but I dont know the right way to do it
	public TinyTree tinyTree;
	
	public void setup() throws Exception {
		log.lifecycle("# Wrapping basic dependencies...");
		mc = new ConfigElementWrapper(project, project.getConfigurations().getByName(Constants.MINECRAFT));
		
		log.lifecycle("# Fetching vanilla jars and indexes...");
		String mcPrefix = "minecraft-" + mc.getFilenameSafeVersion();
		VanillaJarFetcher vanillaJars = new VanillaJarFetcher(project, extension)
			.mc(mc)
			.customManifestUrl(extension.customManifestUrl)
			.clientFilename(mcPrefix + "-client.jar")
			.serverFilename(mcPrefix + "-server.jar")
			.fetch();
		
		log.lifecycle("# Fetching vanilla dependencies...");
		VanillaDependencyFetcher vanillaDeps = new VanillaDependencyFetcher(project, extension)
			.superProjectmapped(vanillaJars.isProjectmapped())
			.manifest(vanillaJars.getVersionManifest())
			.librariesBaseUrl(extension.librariesBaseUrl)
			.nativesDirname(mc.getFilenameSafeVersion())
			.fetch()
			.installDependenciesToProject(Constants.MINECRAFT_DEPENDENCIES, project.getDependencies());
		mcNativesDir = vanillaDeps.getNativesDir();
		
		log.lifecycle("# Configuring asset downloader...");
		assets = new AssetDownloader(project, extension)
			.versionManifest(vanillaJars.getVersionManifest())
			.resourcesBaseUrl(extension.resourcesBaseUrl)
			.prepare();
		
		if(!project.getConfigurations().getByName(Constants.FORGE).isEmpty()) {
			//unified jar (1.3+)
			ResolvedConfigElementWrapper forge = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE));
			
			log.lifecycle("# Parsing mappings...");
			MappingsWrapper mappings = new MappingsWrapper(project, extension, project.getConfigurations().getByName(Constants.MAPPINGS), null);
			
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
			
			log.lifecycle("# Merging client and server...");
			Merger merger = new Merger(project, extension)
				.superProjectmapped(vanillaJars.isProjectmapped() | binpatchLoader.isProjectmapped())
				.client(binpatchedClient)
				.server(binpatchedServer)
				.mergedFilename(mcPrefix + "-merged.jar")
				.merge();
			
			log.lifecycle("# Fetching Forge's dependencies...");
			new ForgeDependencyFetcher(project, extension)
				.forgeJar(forge.getPath())
				.fmlLibrariesBaseUrl(extension.fmlLibrariesBaseUrl)
				.libDownloaderDir(forge.getFilenameSafeDepString())
				.bouncycastleCheat(extension.forgeCapabilities.bouncycastleCheat.get())
				.sniff()
				.fetch()
				.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies());
			
			//TODO: Post-1.6, i think installing it like a jarmod is not strictly correct. Forge should get on the classpath some other way
			log.lifecycle("# Jarmodding...");
			String jarmoddedPrefix = mcPrefix + "-forge-" + forge.getFilenameSafeVersion();
			Jarmodder jarmod = new Jarmodder(project, extension)
				.superProjectmapped(merger.isProjectmapped())
				.base(merger.getMergedJar())
				.overlay(forge.getPath())
				.jarmoddedFilename(jarmoddedPrefix + "-jarmod.jar")
				.patch();
			
			log.lifecycle("# Converting mappings to tinyv2...");
			Tinifier tinifier = new Tinifier(project, extension)
				.superProjectmapped(jarmod.isProjectmapped())
				.scanJars(jarmod.getJarmoddedJar())
				.mappings(mappings)
				.useSrgsAsFallback(extension.forgeCapabilities.srgsAsFallback.get())
				.tinify();
			Path tinyMappingsFile = tinifier.getMappingsFile();
			tinyTree = tinifier.getTinyTree();
			
			log.lifecycle("# Preparing ATs...");
			AccessTransformer transformer = new AccessTransformer(project, extension)
				.superProjectmapped(tinifier.isProjectmapped())
				.regularForgeJar(forge.getPath())
				.loadCustomAccessTransformers();
			@Nullable String atHash = transformer.getCustomAccessTransformerHash();
			String atdSuffix = "-atd" + (atHash == null ? "" : "-" + atHash);
			
			Supplier<Remapper> remapperFactory = () -> new Remapper(project, extension)
				.superProjectmapped(transformer.isProjectmapped() | tinifier.isProjectmapped() | jarmod.isProjectmapped())
				.tinyTree(tinyTree)
				.mappingsDepString(mappings.getFilenameSafeDepString())
				.nonNativeLibs(vanillaDeps.getNonNativeLibraries_Todo())
				.deletedPrefixes(extension.forgeCapabilities.classFilter.get());
			
			Path mappedJar;
			if(extension.forgeCapabilities.mappedAccessTransformers.get()) { //1.7-style
				transformer.mappedAccessTransformers(true);
				//The ATs are presented using SRG names.
				//we will map the jar to SRG names, perform the AT, then map the ATd jar to named
				//The intermediary jar does lag behind when this is done, though
				//TODO: probably better to textually proguard the ATs or something
				
				log.lifecycle("# Remapping (before ATs)...");
				Remapper toIntermediateRemapper = remapperFactory.get()
					.inputJar(Constants.PROGUARDED_NAMING_SCHEME, jarmod.getJarmoddedJar())
					.addOutputJar(Constants.INTERMEDIATE_NAMING_SCHEME, jarmoddedPrefix + "-" + Constants.INTERMEDIATE_NAMING_SCHEME + ".jar")
					.remap();
				
				log.lifecycle("# Applying access transformers...");
				transformer
					.inputJar(toIntermediateRemapper.getMappedJar(Constants.INTERMEDIATE_NAMING_SCHEME))
					.transformedFilename(jarmoddedPrefix + "-" + Constants.INTERMEDIATE_NAMING_SCHEME + atdSuffix + ".jar")
					.transform();
				
				log.lifecycle("# Remapping again (after applying ATs)...");
				Remapper toNamedRemapper = remapperFactory.get()
					.inputJar(Constants.INTERMEDIATE_NAMING_SCHEME, transformer.getTransformedJar())
					.addOutputJar(Constants.MAPPED_NAMING_SCHEME, jarmoddedPrefix + atdSuffix + "-" + Constants.MAPPED_NAMING_SCHEME + ".jar")
					.remap();
				
				mappedJar = toNamedRemapper.getMappedJar(Constants.MAPPED_NAMING_SCHEME);
			} else {
				transformer.mappedAccessTransformers(false);
				//The ATs are presented with proguarded names.
				//simply AT the jar then map it to both namespaces.
				log.lifecycle("# Applying access transformers...");
				transformer
					.inputJar(jarmod.getJarmoddedJar())
					.transformedFilename(jarmoddedPrefix + atdSuffix + ".jar")
					.transform();
				
				log.lifecycle("# Remapping (after ATs)...");
				Remapper remapper = remapperFactory.get()
					.inputJar(Constants.PROGUARDED_NAMING_SCHEME, transformer.getTransformedJar())
					.addOutputJar(Constants.INTERMEDIATE_NAMING_SCHEME, jarmoddedPrefix + atdSuffix + "-" + Constants.INTERMEDIATE_NAMING_SCHEME + ".jar")
					.addOutputJar(Constants.MAPPED_NAMING_SCHEME, jarmoddedPrefix + atdSuffix + "-" + Constants.MAPPED_NAMING_SCHEME + ".jar")
					.remap();
				
				mappedJar = remapper.getMappedJar(Constants.MAPPED_NAMING_SCHEME);
			}
			project.getDependencies().add(Constants.MINECRAFT_NAMED, project.files(mappedJar));
			
			log.lifecycle("# Remapping mod dependencies...");
			new DependencyRemapper(project, extension)
				.superProjectmapped(tinifier.isProjectmapped() | transformer.isProjectmapped() | vanillaDeps.isProjectmapped())
				.mappingsSuffix(mappings.getFilenameSafeDepString())
				.tinyTree(tinyTree)
				.remappedConfigurationEntries(extension.remappedConfigurationEntries)
				.distributionNamingScheme(extension.forgeCapabilities.distributionNamingScheme.get())
				.addToRemapClasspath(jarmod.getJarmoddedJar())
				.addToRemapClasspath(vanillaDeps.getNonNativeLibraries_Todo())
				.remapDependencies()
				.installDependenciesToProject(project.getDependencies());
			
			log.lifecycle("# Initializing source generation job...");
			GenSourcesTask.SourceGenerationJob job = new GenSourcesTask.SourceGenerationJob();
			job.mappedJar = mappedJar;
			job.sourcesJar = LoomGradlePlugin.replaceExtension(mappedJar, "-sources-unlinemapped.jar");
			job.linemapFile = LoomGradlePlugin.replaceExtension(mappedJar, "-linemap.lmap");
			job.finishedJar = LoomGradlePlugin.replaceExtension(mappedJar, "-sources.jar");
			job.libraries = vanillaDeps.getNonNativeLibraries_Todo();
			job.tinyMappingsFile = tinyMappingsFile;
			sourceGenerationJobs.add(job);
		} else {
			//split jar (1.2.5-)
			ResolvedConfigElementWrapper forgeClient = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE_CLIENT));
			ResolvedConfigElementWrapper forgeServer = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE_SERVER));
			
			String clientMcPrefix = mcPrefix + "-client";
			String serverMcPrefix = mcPrefix + "-server";
			
			ThrowyFunction<ResolvedConfigElementWrapper, ForgeDependencyFetcher, Exception> deps = forge ->
				new ForgeDependencyFetcher(project, extension)
					.forgeJar(forge.getPath())
					.fmlLibrariesBaseUrl(extension.fmlLibrariesBaseUrl)
					.libDownloaderDir(forge.getFilenameSafeDepString())
					.bouncycastleCheat(extension.forgeCapabilities.bouncycastleCheat.get());
			log.lifecycle("# Fetching Forge's client dependencies...");
			ForgeDependencyFetcher clientForgeDeps = deps.apply(forgeClient)
				.sniff()
				.fetch()
				.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies());
			log.lifecycle("# Fetching Forge's server dependencies...");
			ForgeDependencyFetcher serverForgeDeps = deps.apply(forgeServer)
				.sniff()
				.fetch()
				.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies());
			
			String jarmoddedClientPrefix = clientMcPrefix + "-" + forgeClient.getFilenameSafeDepString();
			String jarmoddedServerPrefix = serverMcPrefix + "-" + forgeServer.getFilenameSafeDepString();
			ThrowyFunction.Tri<ResolvedConfigElementWrapper, Path, String, Jarmodder, Exception> jarrr = (forge, vanilla, prefix) ->
				new Jarmodder(project, extension)
					.superProjectmapped(clientForgeDeps.isProjectmapped() || serverForgeDeps.isProjectmapped() || vanillaJars.isProjectmapped())
					.base(vanilla)
					.overlay(forge.getPath())
					.jarmoddedFilename(prefix + "-jarmod.jar");
			log.lifecycle("# Jarmodding client...");
			Jarmodder clientJarmodder = jarrr.apply(forgeClient, vanillaJars.getClientJar(), jarmoddedClientPrefix)
				.patch();
			log.lifecycle("# Jarmodding server...");
			Jarmodder serverJarmodder = jarrr.apply(forgeServer, vanillaJars.getServerJar(), jarmoddedServerPrefix)
				.patch();
			
			//TODO this sucks, don't parse twice please
			log.lifecycle("# Parsing client mappings...");
			MappingsWrapper clientMappings = new MappingsWrapper(project, extension, project.getConfigurations().getByName(Constants.MAPPINGS), "client");
			log.lifecycle("# Parsing server mappings...");
			MappingsWrapper serverMappings = new MappingsWrapper(project, extension, project.getConfigurations().getByName(Constants.MAPPINGS), "server");
			
			ThrowyFunction.Tri<MappingsWrapper, Jarmodder, Function<MappingsWrapper, Srg>, Tinifier, Exception> mapper = (mappings, jarmodder, whichSrg) ->
				new Tinifier(project, extension)
					.superProjectmapped(jarmodder.isProjectmapped())
					.scanJars(jarmodder.getJarmoddedJar())
					.mappings(mappings)
					.uglyhack_whichSrg(whichSrg)
					.useSrgsAsFallback(extension.forgeCapabilities.srgsAsFallback.get())
					.tinify();
			log.lifecycle("# Converting client mappings to tinyv2...");
			Tinifier clientTiny = mapper.apply(clientMappings, clientJarmodder, MappingsWrapper::getClient);
			log.lifecycle("# Converting server mappings to tinyv2...");
			Tinifier serverTiny = mapper.apply(serverMappings, serverJarmodder, MappingsWrapper::getServer);
			
			//TODO: ATs (dont think forge had them yet)
			
			ThrowyFunction.Quad<Path, String, MappingsWrapper, Tinifier, Remapper, Exception> remapperFactory = (jar, prefix, mappings, tinifier) ->
				new Remapper(project, extension)
					.superProjectmapped(tinifier.isProjectmapped()) //TODO
					.tinyTree(tinifier.getTinyTree())
					.mappingsDepString(mappings.getFilenameSafeDepString())
					.nonNativeLibs(vanillaDeps.getNonNativeLibraries_Todo())
					.deletedPrefixes(extension.forgeCapabilities.classFilter.get())
					.inputJar(Constants.PROGUARDED_NAMING_SCHEME, jar)
					.addOutputJar(Constants.INTERMEDIATE_NAMING_SCHEME, prefix + "-" + Constants.INTERMEDIATE_NAMING_SCHEME + ".jar")
					.addOutputJar(Constants.MAPPED_NAMING_SCHEME, prefix + "-" + Constants.MAPPED_NAMING_SCHEME + ".jar");
			
			log.lifecycle("# Remapping client...");
			Remapper clientRemapper = remapperFactory.apply(clientJarmodder.getJarmoddedJar(), jarmoddedClientPrefix, clientMappings, clientTiny)
				.remap();
			log.lifecycle("# Remapping server...");
			Remapper serverRemapper = remapperFactory.apply(serverJarmodder.getJarmoddedJar(), jarmoddedServerPrefix, serverMappings, serverTiny)
				.remap();
			
			project.getDependencies().add(Constants.MINECRAFT_NAMED, project.files(clientRemapper.getMappedJar(Constants.MAPPED_NAMING_SCHEME)));
			project.getDependencies().add(Constants.MINECRAFT_NAMED, project.files(serverRemapper.getMappedJar(Constants.MAPPED_NAMING_SCHEME)));
			
			//TODO: Mod dependencies
			
			log.lifecycle("# Initializing source generation jobs...");
			GenSourcesTask.SourceGenerationJob clientJob = new GenSourcesTask.SourceGenerationJob();
			clientJob.mappedJar = clientRemapper.getMappedJar(Constants.MAPPED_NAMING_SCHEME);
			clientJob.sourcesJar = LoomGradlePlugin.replaceExtension(clientJob.mappedJar, "-sources-unlinemapped.jar");
			clientJob.linemapFile = LoomGradlePlugin.replaceExtension(clientJob.mappedJar, "-linemap.lmap");
			clientJob.finishedJar = LoomGradlePlugin.replaceExtension(clientJob.mappedJar, "-sources.jar");
			clientJob.libraries = vanillaDeps.getNonNativeLibraries_Todo();
			clientJob.tinyMappingsFile = clientTiny.getMappingsFile();
			sourceGenerationJobs.add(clientJob);
			
			GenSourcesTask.SourceGenerationJob serverJob = new GenSourcesTask.SourceGenerationJob();
			serverJob.mappedJar = serverRemapper.getMappedJar(Constants.MAPPED_NAMING_SCHEME);
			serverJob.sourcesJar = LoomGradlePlugin.replaceExtension(serverJob.mappedJar, "-sources-unlinemapped.jar");
			serverJob.linemapFile = LoomGradlePlugin.replaceExtension(serverJob.mappedJar, "-linemap.lmap");
			serverJob.finishedJar = LoomGradlePlugin.replaceExtension(serverJob.mappedJar, "-sources.jar");
			serverJob.libraries = vanillaDeps.getNonNativeLibraries_Todo();
			serverJob.tinyMappingsFile = serverTiny.getMappingsFile();
			sourceGenerationJobs.add(serverJob);
		}
		
		log.lifecycle("# Thank you for flying Voldeloom.");
	}
	
	public void trySetup() {
		try {
			setup();
		} catch (Exception e) {
			throw new RuntimeException("Exception setting up Voldeloom: " + e.getMessage(), e);
		}
	}
}

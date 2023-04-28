package net.fabricmc.loom;

import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.loom.newprovider.AccessTransformer;
import net.fabricmc.loom.newprovider.AssetDownloader;
import net.fabricmc.loom.newprovider.Binpatcher;
import net.fabricmc.loom.newprovider.ConfigElementWrapper;
import net.fabricmc.loom.newprovider.DependencyRemapperMcp;
import net.fabricmc.loom.newprovider.ForgeDependencyFetcher;
import net.fabricmc.loom.newprovider.Jarmodder;
import net.fabricmc.loom.newprovider.MappingsWrapper;
import net.fabricmc.loom.newprovider.Merger;
import net.fabricmc.loom.newprovider.NaiveRenamer;
import net.fabricmc.loom.newprovider.RemapperMcp;
import net.fabricmc.loom.newprovider.ResolvedConfigElementWrapper;
import net.fabricmc.loom.newprovider.VanillaDependencyFetcher;
import net.fabricmc.loom.newprovider.VanillaJarFetcher;
import net.fabricmc.loom.task.GenSourcesTask;
import net.fabricmc.loom.util.Props;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the tangle of DependencyProviders.
 * 
 * You should note that all of this stuff runs on every Gradle invocation, in afterEvaluate - caching is really important...
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
	public ConfigElementWrapper mcWrapper;
	//run configs would like to know where this directory is
	public Path mcNativesDir;
	//called from ShimAssetsTask, client run configs need to invoke it, not invoked here because assets aren't needed on the server/in CI
	public AssetDownloader assets;
	//package of data used by GenSources
	public final List<GenSourcesTask.SourceGenerationJob> sourceGenerationJobs = new ArrayList<>();
	
	//used by ReobfJarTask TODO: FIX, it's not 1.2.5 clean
	public Srg reobfSrg;
	
	public void setup() throws Exception {
		log.lifecycle("# Wrapping basic dependencies...");
		mcWrapper = new ConfigElementWrapper(project.getConfigurations().getByName(Constants.MINECRAFT));
		
		log.lifecycle("# Fetching vanilla jars and indexes...");
		String mcPrefix = "minecraft-" + mcWrapper.getFilenameSafeVersion();
		VanillaJarFetcher vanillaJars = new VanillaJarFetcher(project, extension)
			.mc(mcWrapper)
			.customManifestUrl(extension.customManifestUrl)
			.clientFilename(mcPrefix + "-client-{HASH}.jar")
			.serverFilename(mcPrefix + "-server-{HASH}.jar")
			.fetch();
		
		log.lifecycle("# Fetching vanilla dependencies...");
		VanillaDependencyFetcher vanillaDeps = new VanillaDependencyFetcher(project, extension)
			.superProps(vanillaJars)
			.manifest(vanillaJars.getVersionManifest())
			.librariesBaseUrl(extension.librariesBaseUrl)
			.nativesDirname(mcWrapper.getFilenameSafeVersion() + "-{HASH}")
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
			
			//first do binpatches (they're done on unmerged jars, and this passes through if forge doesn't use binpatches)
			log.lifecycle("# Binpatching...");
			Binpatcher binpatcher = new Binpatcher(project, extension)
				.superProps(vanillaJars)
				.client(vanillaJars.getClientJar())
				.server(vanillaJars.getServerJar())
				.forge(forge.getPath())
				.binpatchedClientName(mcPrefix + "-client-binpatched-{HASH}")
				.binpatchedServerName(mcPrefix + "-server-binpatched-{HASH}")
				.binpatch();
			
			//then merge jars
			log.lifecycle("# Joining client and server...");
			Merger merger = new Merger(project, extension)
				.superProps(binpatcher)
				.client(binpatcher.getBinpatchedClient())
				.server(binpatcher.getBinpatchedServer())
				.mergedFilename(mcPrefix + "-merged-{HASH}.jar")
				.merge();
			
			//and the rest is the same
			setupSide("joined", merger.getMergedJar(), merger.props, vanillaDeps, mcPrefix, forge);
		} else {
			//split jar (1.2.5-)
			ResolvedConfigElementWrapper forgeClient = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE_CLIENT));
			ResolvedConfigElementWrapper forgeServer = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE_SERVER));
			setupSide("client", vanillaJars.getClientJar(), vanillaJars.props, vanillaDeps, mcPrefix + "-client", forgeClient);
			setupSide("server", vanillaJars.getServerJar(), vanillaJars.props, vanillaDeps, mcPrefix + "-server", forgeServer);
		}
		
		log.lifecycle("# Thank you for flying Voldeloom.");
	}
	
	private void setupSide(
		String side,
		Path vanillaJar,
		Props vanillaJarProps,
		VanillaDependencyFetcher vanillaDeps,
		String mcPrefix,
		ResolvedConfigElementWrapper forgeWrapper
	) throws Exception {
		log.lifecycle("# ({}) Fetching Forge dependencies...", side);
		ForgeDependencyFetcher forgeDeps = new ForgeDependencyFetcher(project, extension)
			.forgeJar(forgeWrapper.getPath())
			.fmlLibrariesBaseUrl(extension.fmlLibrariesBaseUrl)
			.libDownloaderDir(forgeWrapper.getFilenameSafeDepString())
			.bouncycastleCheat(extension.forgeCapabilities.bouncycastleCheat.get())
			.sniff()
			.fetch()
			.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies());
		
		log.lifecycle("# ({}) Jarmodding...", side);
		String jarmoddedPrefix = mcPrefix + "-forge-" + forgeWrapper.getFilenameSafeVersion();
		Jarmodder jarmod = new Jarmodder(project, extension)
			.superProps(vanillaJarProps)
			.base(vanillaJar)
			.overlay(forgeWrapper.getPath())
			.jarmoddedFilename(jarmoddedPrefix + "-jarmod-{HASH}.jar")
			.patch();
		
		log.lifecycle("# ({}) Parsing mappings...", side);
		//the jarscandata comes from the jarmodded jar, not the vanilla one, because some inner-class relations i need to know about are added by forge
		MappingsWrapper mappingsWrapper = new MappingsWrapper(project, project.getConfigurations().getByName(Constants.MAPPINGS), jarmod.getJarmoddedJar());
		McpMappings mappings = mappingsWrapper.mappings;
		
		log.lifecycle("# ({}) Preparing ATs...", side);
		AccessTransformer transformer = new AccessTransformer(project, extension)
			.regularForgeJar(forgeWrapper.getPath())
			.loadCustomAccessTransformers();
		
		log.lifecycle("# ({}) Preparing SRG remapper...", side);
		RemapperMcp remapperMcp = new RemapperMcp(project, extension)
			.superProps(mappingsWrapper.props)
			.srg(mappings.chooseSrg(side))
			.addToRemapClasspath(vanillaDeps.getNonNativeLibraries_Todo())
			.deletedPrefixes(extension.forgeCapabilities.classFilter.get());
		
		Path srgAtdJar;
		if(extension.forgeCapabilities.mappedAccessTransformers.get()) { //1.7 and above
			log.lifecycle("# ({}) Remapping to SRG with tiny-remapper...", side);
			remapperMcp = remapperMcp
				.superProps(jarmod)
				.inputJar(jarmod.getJarmoddedJar())
				.outputSrgJar(mappingsWrapper.getFilenameSafeDepString(), jarmoddedPrefix + "-srg-{HASH}.jar")
				.remap();
			
			log.lifecycle("# ({}) Applying (mapped) access transformers...", side);
			transformer = transformer
				.superProps(remapperMcp)
				.mappedAccessTransformers(true)
				.inputJar(remapperMcp.getOutputSrgJar())
				.transformedFilename(jarmoddedPrefix + "-srg-atd-{HASH}.jar")
				.transform();
			
			srgAtdJar = transformer.getTransformedJar();
		} else { //1.6 and below
			log.lifecycle("# ({}) Applying (unmapped) access transformers...", side);
			transformer = transformer
				.superProps(jarmod)
				.inputJar(jarmod.getJarmoddedJar())
				.transformedFilename(jarmoddedPrefix + "-atd-{HASH}.jar")
				.transform();
			
			log.lifecycle("# ({}) Remapping to SRG with tiny-remapper...", side);
			remapperMcp = remapperMcp
				.superProps(transformer)
				.inputJar(transformer.getTransformedJar())
				.outputSrgJar(mappingsWrapper.getFilenameSafeDepString(), jarmoddedPrefix + "-atd-srg-{HASH}.jar")
				.remap();
			
			srgAtdJar = remapperMcp.getOutputSrgJar();
		}
		
		log.lifecycle("# ({}) Applying field and method names with NaiveRenamer...", side);
		NaiveRenamer naive = new NaiveRenamer(project, extension)
			.superProps(transformer, remapperMcp)
			.input(srgAtdJar)
			.outputFilename(mappingsWrapper.getFilenameSafeDepString(), jarmoddedPrefix + "-named-{HASH}.jar")
			.fields(mappings.fields)
			.methods(mappings.methods)
			.rename();
		
		//TODO: does this belong inside the per-side stuff, or outside
		// probably inside? but i need better delineation of client and server workspace mods...
		log.lifecycle("# ({}) Remapping mod dependencies...", side);
		new DependencyRemapperMcp(project, extension)
			.superProps(naive)
			.mappingsDepString(mappingsWrapper.getFilenameSafeDepString())
			.srg(mappings.chooseSrg(side))
			.fields(mappings.fields)
			.methods(mappings.methods)
			.remappedConfigurationEntries(extension.remappedConfigurationEntries)
			.distributionNamingScheme(extension.forgeCapabilities.distributionNamingScheme.get())
			.addToRemapClasspath(jarmod.getJarmoddedJar())
			.addToRemapClasspath(vanillaDeps.getNonNativeLibraries_Todo())
			.doIt(project.getDependencies());
		
		log.lifecycle("# ({}) Initializing source generation job...", side);
		GenSourcesTask.SourceGenerationJob job = new GenSourcesTask.SourceGenerationJob();
		job.mappedJar = naive.getOutput();
		job.sourcesJar = LoomGradlePlugin.replaceExtension(naive.getOutput(), "-sources.jar");
		job.linemapFile = LoomGradlePlugin.replaceExtension(naive.getOutput(), "-linemap.lmap");
		job.linemappedJar = LoomGradlePlugin.replaceExtension(naive.getOutput(), "-linemapped.jar");
		job.libraries = vanillaDeps.getNonNativeLibraries_Todo();
		job.mcpMappingsZip = mappingsWrapper.getPath();
		sourceGenerationJobs.add(job);
		
		//TODO: The idea is that using the linemapped jar is better than not using it, because debugger breakpoints work.
		// But linemapping is optional because it's a component of genSources.
		// Butbut, this is also ran before genSources, ideallyLinemappedJar's existence is from the *last* invocation...
		// So you have to refresh gradle a second time to have the plugin put the linemapped jar into the configuration :(
		// Also worried about cachebusting this?
		//I guess I could have the linemapper actually overwrite the original jar... ?
		Path ideallyLinemappedJar;
		if(Files.exists(job.linemappedJar)) ideallyLinemappedJar = job.linemappedJar;
		else ideallyLinemappedJar = naive.getOutput();
		
		project.getDependencies().add(Constants.MINECRAFT_NAMED, project.files(ideallyLinemappedJar));
		
		//TODO: oops all leaky abstraction again
		if(side.equals("joined")) {
			boolean srgFieldsMethodsAsFallback = extension.forgeCapabilities.srgsAsFallback.get();
			boolean reobfToSrg = extension.forgeCapabilities.distributionNamingScheme.get().equals(Constants.INTERMEDIATE_NAMING_SCHEME);
			
			log.lifecycle("# ({}) Initializing reobf mappings ({} -> {})...", side, Constants.MAPPED_NAMING_SCHEME,
				reobfToSrg ? Constants.INTERMEDIATE_NAMING_SCHEME : Constants.PROGUARDED_NAMING_SCHEME);
			
			reobfSrg = mappings.chooseSrg(side).reobf(mappings.fields, mappings.methods, srgFieldsMethodsAsFallback, reobfToSrg);
		}
	}
	
	public void trySetup() {
		try {
			setup();
		} catch (Exception e) {
			throw new RuntimeException("Exception setting up Voldeloom: " + e.getMessage(), e);
		}
	}
}

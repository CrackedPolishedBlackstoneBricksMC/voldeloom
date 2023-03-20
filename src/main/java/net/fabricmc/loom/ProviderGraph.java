package net.fabricmc.loom;

import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.loom.newprovider.AccessTransformer;
import net.fabricmc.loom.newprovider.AssetDownloader;
import net.fabricmc.loom.newprovider.BinpatchLoader;
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
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
	
	//used by ReobfJarTask TODO: FIX, it's not 1.2.5 clean
	public Srg reobfSrg;
	
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
			
			//first do binpatches (they're done on the unmerged jars)
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
			
			//then merge the jars
			log.lifecycle("# Joining client and server...");
			Merger merger = new Merger(project, extension)
				.superProjectmapped(vanillaJars.isProjectmapped() | binpatchLoader.isProjectmapped())
				.client(binpatchedClient)
				.server(binpatchedServer)
				.mergedFilename(mcPrefix + "-merged.jar")
				.merge();
			
			//and the rest is the same
			setupSide("joined", merger.getMergedJar(), vanillaDeps, mcPrefix, merger.isProjectmapped(), forge, mappings -> mappings.joined);
		} else {
			//split jar (1.2.5-)
			ResolvedConfigElementWrapper forgeClient = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE_CLIENT));
			ResolvedConfigElementWrapper forgeServer = new ResolvedConfigElementWrapper(project, project.getConfigurations().getByName(Constants.FORGE_SERVER));
			setupSide("client", vanillaJars.getClientJar(), vanillaDeps, mcPrefix + "-client", vanillaJars.isProjectmapped(), forgeClient, mappings -> mappings.client);
			setupSide("server", vanillaJars.getServerJar(), vanillaDeps, mcPrefix + "-server", vanillaJars.isProjectmapped(), forgeServer, mappings -> mappings.server);
		}
		
		log.lifecycle("# Thank you for flying Voldeloom.");
	}
	
	private void setupSide(String side, Path vanillaJar, VanillaDependencyFetcher vanillaDeps, String mcPrefix, boolean superProjectmapped, ResolvedConfigElementWrapper forge, Function<McpMappings, Srg> srgGetter) throws Exception {
		log.lifecycle("# ({}) Fetching Forge dependencies...", side);
		new ForgeDependencyFetcher(project, extension)
			.forgeJar(forge.getPath())
			.fmlLibrariesBaseUrl(extension.fmlLibrariesBaseUrl)
			.libDownloaderDir(forge.getFilenameSafeDepString())
			.bouncycastleCheat(extension.forgeCapabilities.bouncycastleCheat.get())
			.sniff()
			.fetch()
			.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies());
		
		log.lifecycle("# ({}) Jarmodding...", side);
		String jarmoddedPrefix = mcPrefix + "-forge-" + forge.getFilenameSafeVersion();
		Jarmodder jarmod = new Jarmodder(project, extension)
			.superProjectmapped(superProjectmapped)
			.base(vanillaJar)
			.overlay(forge.getPath())
			.jarmoddedFilename(jarmoddedPrefix + "-jarmod.jar")
			.patch();
		
		log.lifecycle("# ({}) Parsing mappings...", side);
		//the jarscandata comes from the jarmodded jar, not the vanilla one, because some inner-class relations i need to know about
		//are added by forge
		MappingsWrapper mappings = new MappingsWrapper(project, extension, project.getConfigurations().getByName(Constants.MAPPINGS), jarmod.getJarmoddedJar());
		
		log.lifecycle("# ({}) Preparing ATs...", side);
		AccessTransformer transformer = new AccessTransformer(project, extension)
			.superProjectmapped(jarmod.isProjectmapped())
			.regularForgeJar(forge.getPath())
			.loadCustomAccessTransformers();
		@Nullable String atHash = transformer.getCustomAccessTransformerHash();
		String atdSuffix = "-atd" + (atHash == null ? "" : "-" + atHash);
		
		log.lifecycle("# ({}) Preparing SRG remapper...", side);
		RemapperMcp remapperMcp = new RemapperMcp(project, extension)
			.srg(srgGetter.apply(mappings.mappings))
			.setProjectmapped(transformer.isProjectmapped())
			.addToRemapClasspath(vanillaDeps.getNonNativeLibraries_Todo())
			.deletedPrefixes(extension.forgeCapabilities.classFilter.get());
		
		Path srgAtdJar;
		if(extension.forgeCapabilities.mappedAccessTransformers.get()) {
			log.lifecycle("# ({}) Remapping to SRG with tiny-remapper...", side);
			remapperMcp
				.inputJar(jarmod.getJarmoddedJar())
				.outputSrgJar(mappings.getFilenameSafeDepString(), jarmoddedPrefix + "-srg.jar")
				.remap();
			
			log.lifecycle("# ({}) Applying (mapped) access transformers...", side);
			transformer
				.mappedAccessTransformers(true)
				.inputJar(remapperMcp.getOutputSrgJar())
				.transformedFilename(jarmoddedPrefix + "-srg-" + atdSuffix + ".jar")
				.transform();
			
			srgAtdJar = transformer.getTransformedJar();
		} else {
			log.lifecycle("# ({}) Applying (unmapped) access transformers...", side);
			transformer
				.inputJar(jarmod.getJarmoddedJar())
				.transformedFilename(jarmoddedPrefix + atdSuffix + ".jar")
				.transform();
			
			log.lifecycle("# ({}) Remapping to SRG with tiny-remapper...", side);
			remapperMcp
				.inputJar(transformer.getTransformedJar())
				.outputSrgJar(mappings.getFilenameSafeDepString(), jarmoddedPrefix + atdSuffix + "-srg.jar")
				.remap();
			
			srgAtdJar = remapperMcp.getOutputSrgJar();
		}
		
		//Remap to named using a naive remapper
		log.lifecycle("# ({}) Applying field and method names with NaiveRenamer...", side);
		NaiveRenamer naive = new NaiveRenamer(project, extension)
			.setProjectmapped(transformer.isProjectmapped() || remapperMcp.isProjectmapped())
			.input(srgAtdJar)
			.output(srgAtdJar.resolveSibling(srgAtdJar.getFileName() + "-named.jar")) //TODO
			.mappings(mappings.mappings) //mappings
			.doIt();
		
		project.getDependencies().add(Constants.MINECRAFT_NAMED, project.files(naive.getOutput()));
		
		//TODO: does this belong inside the per-side stuff, or outside
		// probably inside? but i need better delineation of client and server workspace mods...
		log.lifecycle("# ({}) Remapping mod dependencies...", side);
		new DependencyRemapperMcp(project, extension)
			.superProjectmapped(naive.isProjectmapped())
			.mappingsDepString(mappings.getFilenameSafeDepString())
			.srg(srgGetter.apply(mappings.mappings))
			.fields(mappings.mappings.fields)
			.methods(mappings.mappings.methods)
			.remappedConfigurationEntries(extension.remappedConfigurationEntries)
			.distributionNamingScheme(extension.forgeCapabilities.distributionNamingScheme.get())
			.addToRemapClasspath(jarmod.getJarmoddedJar())
			.addToRemapClasspath(vanillaDeps.getNonNativeLibraries_Todo())
			.doIt(project.getDependencies());
		
		log.lifecycle("# ({}) Initializing source generation job...", side);
		GenSourcesTask.SourceGenerationJob job = new GenSourcesTask.SourceGenerationJob();
		job.mappedJar = naive.getOutput();
		job.sourcesJar = LoomGradlePlugin.replaceExtension(naive.getOutput(), "-sources-unlinemapped.jar");
		job.linemapFile = LoomGradlePlugin.replaceExtension(naive.getOutput(), "-linemap.lmap");
		job.finishedJar = LoomGradlePlugin.replaceExtension(naive.getOutput(), "-sources.jar");
		job.libraries = vanillaDeps.getNonNativeLibraries_Todo();
		job.mcpMappingsZip = mappings.getPath();
		sourceGenerationJobs.add(job);
		
		//TODO: oops all leaky abstraction again
		if(side.equals("joined")) {
			log.lifecycle("# ({}) Initializing reobf mappings...", side);
			reobfSrg = srgGetter.apply(mappings.mappings)
				.named(mappings.mappings.fields, mappings.mappings.methods, extension.forgeCapabilities.srgsAsFallback.get())
				.inverted();
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

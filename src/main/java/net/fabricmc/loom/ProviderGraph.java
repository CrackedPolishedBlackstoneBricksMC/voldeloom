package net.fabricmc.loom;

import net.fabricmc.loom.newprovider.AccessTransformer;
import net.fabricmc.loom.newprovider.BinpatchLoader;
import net.fabricmc.loom.newprovider.Binpatcher;
import net.fabricmc.loom.newprovider.ForgeDependencyFetcher;
import net.fabricmc.loom.newprovider.ForgePatcher;
import net.fabricmc.loom.newprovider.Merger;
import net.fabricmc.loom.newprovider.NewProvider;
import net.fabricmc.loom.newprovider.Tinifier;
import net.fabricmc.loom.newprovider.VanillaDependencyFetcher;
import net.fabricmc.loom.newprovider.VanillaJarFetcher;
import net.fabricmc.loom.providers.AssetsProvider;
import net.fabricmc.loom.providers.MappedProvider;
import net.fabricmc.loom.providers.RemappedDependenciesProvider;
import org.gradle.api.Project;

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
		this.extension = extension;
	}
	
	private final Project project;
	private final LoomGradleExtension extension;
	
	private final Map<Class<? extends NewProvider<?>>, NewProvider<?>> newProviders = new HashMap<>();
	
	public void setup() throws Exception {
		/// NEW SYSTEM ///
		VanillaJarFetcher vanillaJars = new VanillaJarFetcher(project, extension)
			.mc(extension.mc)
			.customManifestUrl(extension.customManifestUrl)
			.fetch();
		
		VanillaDependencyFetcher vanillaDeps = new VanillaDependencyFetcher(project, extension)
			.superProjectmapped(vanillaJars.projectmapped)
			.mc(extension.mc)
			.manifest(vanillaJars.getVersionManifest())
			.librariesBaseUrl(extension.librariesBaseUrl)
			.fetch()
			.installDependenciesToProject(Constants.MINECRAFT_DEPENDENCIES, project.getDependencies());
		
		ForgeDependencyFetcher forgeDeps = new ForgeDependencyFetcher(project, extension)
			.forge(extension.forge)
			.fmlLibrariesBaseUrl(extension.fmlLibrariesBaseUrl)
			.sniff()
			.fetch()
			.installDependenciesToProject(Constants.FORGE_DEPENDENCIES, project.getDependencies(), project::files);
		
		Path binpatchedClient, binpatchedServer;
		BinpatchLoader binpatchLoader = new BinpatchLoader(project, extension)
			.forge(extension.forge)
			.load();
		
		if(binpatchLoader.hasBinpatches()) {
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
			binpatchedClient = vanillaJars.getClientJar();
			binpatchedServer = vanillaJars.getServerJar();
		}
		
		//TODO 1.2.5 split: - cut here?
		
		Merger merger = new Merger(project, extension)
			.superProjectmapped(vanillaJars.projectmapped | binpatchLoader.projectmapped)
			.client(binpatchedClient)
			.server(binpatchedServer)
			.mc(extension.mc)
			.merge();
		
		//TODO: Post-1.6, i think installing it like a jarmod is not strictly correct. Forge should get on the classpath some other way
		ForgePatcher patched = new ForgePatcher(project, extension)
			.superProjectmapped(merger.projectmapped)
			.vanilla(merger.getMerged())
			.forge(extension.forge.getPath())
			.mc(extension.mc)
			.patch();
		
		AccessTransformer transformer = new AccessTransformer(project, extension)
			.superProjectmapped(patched.projectmapped)
			.forge(extension.forge)
			.forgePatched(patched.getPatchedJar())
			.patchedVersionTag(patched.getPatchedVersionTag())
			.transform();
		
		Tinifier tinyMappings = new Tinifier(project, extension)
			.superProjectmapped(transformer.projectmapped)
			.jarToScan(transformer.getTransformedJar())
			.mappings(extension.mappings)
			.useSrgsAsFallback(extension.forgeCapabilities.useSrgsAsFallback())
			.tinify();
		
		makeAvailableToTasks(vanillaJars, vanillaDeps, forgeDeps, transformer, tinyMappings);
		
		/// OLD SYSTEM ///
		
		MappedProvider mapped = putOld(new MappedProvider(project, extension, patched.getPatchedVersionTag(), transformer.getTransformedJar(), tinyMappings, vanillaDeps.getNonNativeLibraries_Todo()));
		
		RemappedDependenciesProvider remappedDeps = putOld(new RemappedDependenciesProvider(project, extension, tinyMappings));
		AssetsProvider assets = putOld(new AssetsProvider(project, extension, vanillaJars.getVersionManifest()));
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
	
	private final Map<Class<? extends DependencyProvider>, DependencyProvider> constructedProviders = new HashMap<>();
	
	@Deprecated
	private <T extends DependencyProvider> T putOld(T dep) {
		constructedProviders.put(dep.getClass(), dep);
		return dep;
	}
	
	@Deprecated
	@SuppressWarnings("unchecked")
	public <T extends DependencyProvider> T getOld(Class<T> type) {
		if(constructedProviders.containsKey(type)) {
			return (T) constructedProviders.get(type);
		} else {
			throw new IllegalStateException("No provider of type " + type);
		}
	}
}

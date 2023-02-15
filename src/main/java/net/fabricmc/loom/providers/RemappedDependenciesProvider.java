package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.newprovider.AccessTransformer;
import net.fabricmc.loom.newprovider.Tinifier;
import net.fabricmc.loom.newprovider.VanillaDependencyFetcher;
import net.fabricmc.loom.util.TinyRemapperSession;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Minecraft mods are re-proguarded at distribution time. This provider is in charge of reversing that process,
 * so ready-for-distribution mods can be imported into the development workspace, instead of requiring a "dev jar".
 * <p>
 * This provider looks for deps in the {@code modImplementation} configuration, remaps them, and puts the remapped
 * result in the {@code modImplementationNamed} configuration, which is the configuration actually exposed to the workspace.
 * Ditto for the other {@code modBlah} configs.
 * <p>
 * Very derivative of ModCompileRemapper in old tools
 * 
 * @see net.fabricmc.loom.LoomGradlePlugin for where these configurations are created and set up
 * @see RemappedConfigurationEntry for what defines the relations between these named configurations
 * 
 * <h2>TODO: Clusterfuck</h2>
 */
public class RemappedDependenciesProvider extends DependencyProvider {
	public RemappedDependenciesProvider(Project project, LoomGradleExtension extension, Tinifier mappings) {
		super(project, extension);
		this.mappings = mappings;
	}
	
	private final Tinifier mappings;
	
	private static class RemappingJob {
		public RemappingJob(Path unmappedPath, Path mappedPath, Configuration outConfig) {
			this.unmappedPath = unmappedPath;
			this.mappedPath = mappedPath;
			this.outConfig = outConfig;
		}
		
		Path unmappedPath, mappedPath;
		Configuration outConfig;
	}
	private final List<RemappingJob> jobs = new ArrayList<>();
	
	@Override
	protected void performSetup() throws Exception {
		String mappingsSuffix = extension.mappings.getMappingsDepString().replaceAll("[^A-Za-z0-9.-]", "_");
		Path remappedModCache = WellKnownLocations.getRemappedModCache(project);
		
		for(RemappedConfigurationEntry entry : extension.remappedConfigurationEntries) {
			Configuration inputConfig = entry.getInputConfig();
			Configuration outputConfig = entry.getOutputConfig();
			
			for(File unmappedFile : inputConfig.getResolvedConfiguration().getFiles()) {
				Path unmappedPath = unmappedFile.toPath();
				Path mappedPath = remappedModCache.resolve(unmappedPath.getFileName().toString() + "-mapped-" + mappingsSuffix + ".jar");
				
				jobs.add(new RemappingJob(unmappedPath, mappedPath, outputConfig));
			}
		}
		
		int count = jobs.size();
		if(count > 0) project.getLogger().lifecycle("] {} remappable dependenc{}, dest {}", count, count == 1 ? "y" : "ies", remappedModCache);
		
		cleanOnRefreshDependencies(remappedModCache);
	}
	
	public void performInstall() throws Exception {
		for(RemappingJob job : jobs) {
			project.getLogger().info("|-> Found a mod dependency at {}", job.unmappedPath);
			project.getLogger().info("\\-> Need to remap to {}", job.mappedPath);
			
			//perform remap, if the output file does not exist
			if(extension.refreshDependencies || Files.notExists(job.mappedPath)) {
				project.getLogger().info("\\-> Remapped file doesn't exist, running remapper...");
				
				try {
					processMod(job.unmappedPath, job.mappedPath, null, null, mappings, extension.getProviderGraph().get(AccessTransformer.class).getTransformedJar(), extension.getProviderGraph().get(VanillaDependencyFetcher.class).getNonNativeLibraries_Todo());
				} catch (Exception e) {
					throw new RuntimeException("Failed to remap dependency at " + job.unmappedPath + " to " + job.mappedPath, e);
				}
				
				project.getLogger().info("\\-> Done! :)");
			} else {
				project.getLogger().info("\\-> Already remapped.");
			}
			
			//add it as a dependency to the project
			project.getLogger().info("\\-> Adding remapped result to the '{}' configuration", job.outConfig.getName());
			project.getDependencies().add(job.outConfig.getName(), project.files(job.mappedPath));
		}
			
			//TODO old code is below (that more-or-less correctly handles artifacts instead of using `files`, but also doesn't work with `files` input artifacts...)
			
//			for(ResolvedArtifact artifact : inputConfig.getResolvedConfiguration().getResolvedArtifacts()) {
//				String group;
//				String name;
//				String version;
//				String classifierSuffix = artifact.getClassifier() == null ? "" : (":" + artifact.getClassifier());
//				
//				if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier) {
//					group = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getGroup();
//					name = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getModule();
//					version = ((ModuleComponentIdentifier) artifact.getId().getComponentIdentifier()).getVersion();
//				} else {
//					group = "net.fabricmc.synthetic";
//					name = artifact.getId().getComponentIdentifier().getDisplayName().replace('.', '-').replace(" :", "-");
//					version = "0.1.0";
//				}
//				
//				String remappedLog = group + ":" + name + ":" + version + classifierSuffix + " (" + mappingsSuffix + ")";
//				project.getLogger().info(":MOD REMAP TIME we are remapping " + remappedLog);
//				
//				String remappedNotation = String.format("%s:%s:%s@%s%s", group, name, version, mappingsSuffix, classifierSuffix);
//				String remappedFilename = String.format("%s-%s@%s", name, version, mappingsSuffix + classifierSuffix.replace(':', '-'));
//				
//				try {
//					remapArtifact(outputConfig, artifact, remappedFilename, modStore);
//				} catch (Exception e) {
//					throw new RuntimeException("didnt remap good todo make this error better", e);
//				}
//				project.getDependencies().add(outputConfig.getName(), project.getDependencies().module(remappedNotation));
//	
//	//			File sources = findSources(dependencies, artifact);
//	//			if (sources != null) {
//	//				scheduleSourcesRemapping(project, postPopulationScheduler, sources, remappedLog, remappedFilename, modStore);
//	//			}
//			}
	}
	
	private void processMod(Path input, Path output, Configuration config, /* TODO */ ResolvedArtifact artifact, Tinifier tinifier, Path transformedJar, Collection<Path> nonNativeLibs) throws IOException {
		Set<Path> remapClasspath = new HashSet<>();
		
		remapClasspath.add(transformedJar);
		remapClasspath.addAll(nonNativeLibs);
		for(File file : project.getConfigurations().getByName(Constants.EVERY_UNMAPPED_MOD).getFiles()) {
			Path p = file.toPath();
			if(!p.equals(input)) {
				remapClasspath.add(p);
			}
		}
		
		// If the sources don't exist, we want remapper to give nicer names to the missing variable names.
		// However, if the sources do exist, if remapper gives names to the parameters that prevents IDEs (at least IDEA)
		// from replacing the parameters with the actual names from the sources.
		//boolean sourcesExist = findSources(project.getDependencies(), artifact) != null;
		boolean sourcesExist = false;
		
		new TinyRemapperSession()
			.setMappings(tinifier.getMappings())
			.setInputNamingScheme(extension.forgeCapabilities.computeDistributionNamingScheme())
			.setInputJar(input)
			.setInputClasspath(remapClasspath)
			.addOutputJar(Constants.MAPPED_NAMING_SCHEME, output)
			.setLogger(project.getLogger()::info)
			.dontRemapLocalVariables()
			.run();
		
		if (Files.notExists(output)) {
			throw new RuntimeException("Failed to remap JAR to 'named' - file not found: " + output.toAbsolutePath());
		}
	}
	
//	private void remapArtifact(Configuration config, ResolvedArtifact artifact, String remappedFilename, Path modStore) throws IOException {
//		Path input = artifact.getFile().toPath().toAbsolutePath();
//		Path output = modStore.resolve(remappedFilename + ".jar");
//		
//		long inputModtime = Files.getLastModifiedTime(input).toMillis();
//		long outputModtime = Files.getLastModifiedTime(output).toMillis();
//		
//		if(Files.notExists(output) || inputModtime <= 0 || inputModtime > outputModtime) {
//			//If the output doesn't exist, or appears to be outdated compared to the input we'll remap it
//			processMod(input, output, config, artifact);
//			
//			if (Files.notExists(output)) {
//				throw new RuntimeException("Failed to remap mod - file not found " + output);
//			}
//			
//			Files.setLastModifiedTime(output, FileTime.fromMillis(inputModtime));
//		} else {
//			project.getLogger().info(output.getFileName() + " is up to date with " + input.getFileName());
//		}
//	}
//	
//	public File findSources(DependencyHandler dependencies, ResolvedArtifact artifact) {
//		@SuppressWarnings("unchecked")
//		ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()
//			.forComponents(artifact.getId().getComponentIdentifier())
//			.withArtifacts(JvmLibrary.class, SourcesArtifact.class);
//		
//		for(ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
//			for(ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
//				if(srcArtifact instanceof ResolvedArtifactResult) {
//					return ((ResolvedArtifactResult) srcArtifact).getFile();
//				}
//			}
//		}
//		
//		return null;
//	}
//	
//	private static void scheduleSourcesRemapping(Project project, Consumer<Runnable> postPopulationScheduler, File sources, String remappedLog, String remappedFilename, File modStore) {
//		postPopulationScheduler.accept(() -> {
//			project.getLogger().info(":providing " + remappedLog + " sources");
//			File remappedSources = new File(modStore, remappedFilename + "-sources.jar");
//
//			if (!remappedSources.exists() || sources.lastModified() <= 0 || sources.lastModified() > remappedSources.lastModified()) {
//				try {
//					SourceRemapper.remapSources(project, sources, remappedSources, true);
//
//					//Set the remapped sources creation date to match the sources if we're likely succeeded in making it
//					remappedSources.setLastModified(sources.lastModified());
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//			} else {
//				project.getLogger().info(remappedSources.getName() + " is up to date with " + sources.getName());
//			}
//		});
//	}
}

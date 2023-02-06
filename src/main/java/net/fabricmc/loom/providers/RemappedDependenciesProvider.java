package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.TinyRemapperSession;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Minecraft mods are re-proguarded at distribution time. This is in charge of remapping them to the chosen mapping set.
 * <h2>TODO: Clusterfuck</h2>
 */
public class RemappedDependenciesProvider extends DependencyProvider {
	@Inject
	public RemappedDependenciesProvider(Project project, LoomGradleExtension extension, MinecraftDependenciesProvider minecraftDependenciesProvider, MappingsProvider mappingsProvider, ForgePatchedAccessTxdProvider patchedAccessTxdProvider) {
		super(project, extension);
		this.minecraftDependenciesProvider = minecraftDependenciesProvider;
		this.mappingsProvider = mappingsProvider;
		this.patchedAccessTxdProvider = patchedAccessTxdProvider;
	}
	
	private final MinecraftDependenciesProvider minecraftDependenciesProvider;
	private final MappingsProvider mappingsProvider;
	private final ForgePatchedAccessTxdProvider patchedAccessTxdProvider;
	
	public void performInstall() throws Exception {
		String mappingsSuffix = mappingsProvider.getMappingsName() + "-" + mappingsProvider.getMappingsVersion();
		
		//MERGED from ModCompileRemapper in old tools
		
		for(RemappedConfigurationEntry entry : extension.remappedConfigurationEntries) {
			Path modStore = WellKnownLocations.getRemappedModCache(project);
			
			Configuration inputConfig = entry.getInputConfig();
			Configuration outputConfig = entry.getOutputConfig();
			
			for(File unmappedFile : inputConfig.getResolvedConfiguration().getFiles()) {
				try {
					Path unmappedPath = unmappedFile.toPath();
					Path mappedPath = modStore.resolve(unmappedPath.getFileName().toString() + "-mapped-" + mappingsSuffix + ".jar");
					
					if(Files.notExists(mappedPath) || Constants.refreshDependencies) {
						processMod(unmappedPath, mappedPath, null, null, minecraftDependenciesProvider, mappingsProvider, patchedAccessTxdProvider);
					}
					
					project.getDependencies().add(outputConfig.getName(), project.files(mappedPath));
				} catch (Exception e) {
					throw new RuntimeException("phooey", e);
				}
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
		
		installed = true;
	}
	
	private void processMod(Path input, Path output, Configuration config, /* TODO */ ResolvedArtifact artifact, MinecraftDependenciesProvider minecraftDependenciesProvider, MappingsProvider mappingsProvider, ForgePatchedAccessTxdProvider patchedAccessTxdProvider) throws IOException {
		Set<Path> remapClasspath = new HashSet<>();
		
		remapClasspath.add(patchedAccessTxdProvider.getTransformedJar());
		remapClasspath.addAll(minecraftDependenciesProvider.getNonNativeLibraries());
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
			.setMappings(mappingsProvider.getMappings())
			.setInputNamingScheme(Constants.PROGUARDED_NAMING_SCHEME) //TODO: distribution naming scheme
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
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.singleton(WellKnownLocations.getRemappedModCache(project));
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

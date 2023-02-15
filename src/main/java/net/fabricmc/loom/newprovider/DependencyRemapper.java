package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.util.TinyRemapperSession;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
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
public class DependencyRemapper extends NewProvider<DependencyRemapper> {
	public DependencyRemapper(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private MappingsWrapper mappingsWrapper;
	private Iterable<RemappedConfigurationEntry> remappedConfigurationEntries;
	private TinyTree tinyTree;
	private Path minecraft;
	private Collection<Path> nonNativeLibraries;
	private String distributionNamingScheme;
	
	public DependencyRemapper mappings(MappingsWrapper mappingsWrapper) {
		this.mappingsWrapper = mappingsWrapper;
		return this;
	}
	
	public DependencyRemapper remappedConfigurationEntries(Iterable<RemappedConfigurationEntry> remappedConfigurationEntries) {
		this.remappedConfigurationEntries = remappedConfigurationEntries;
		return this;
	}
	
	public DependencyRemapper tinyTree(TinyTree tinyTree) {
		this.tinyTree = tinyTree;
		return this;
	}
	
	public DependencyRemapper minecraft(Path minecraft) {
		this.minecraft = minecraft;
		return this;
	}
	
	public DependencyRemapper nonNativeLibraries(Collection<Path> nonNativeLibraries) {
		this.nonNativeLibraries = nonNativeLibraries;
		return this;
	}
	
	public DependencyRemapper distributionNamingScheme(String distributionNamingScheme) {
		this.distributionNamingScheme = distributionNamingScheme;
		return this;
	}
	
	public DependencyRemapper remapDependencies() throws Exception {
		class RemappingJob {
			public RemappingJob(Path unmappedPath, Path mappedPath, Configuration outConfig) {
				this.unmappedPath = unmappedPath;
				this.mappedPath = mappedPath;
				this.outConfig = outConfig;
			}
			
			final Path unmappedPath, mappedPath;
			final Configuration outConfig;
		}
		
		List<RemappingJob> jobs = new ArrayList<>();
		
		String mappingsSuffix = mappingsWrapper.getMappingsDepString().replaceAll("[^A-Za-z0-9.-]", "_");
		Path remappedModCache = getRemappedModCache();
		
		for(RemappedConfigurationEntry entry : remappedConfigurationEntries) {
			Configuration inputConfig = entry.getInputConfig();
			Configuration outputConfig = entry.getOutputConfig();
			
			for(File unmappedFile : inputConfig.getResolvedConfiguration().getFiles()) {
				Path unmappedPath = unmappedFile.toPath();
				Path mappedPath = remappedModCache.resolve(unmappedPath.getFileName().toString() + "-mapped-" + mappingsSuffix + ".jar");
				
				jobs.add(new RemappingJob(unmappedPath, mappedPath, outputConfig));
			}
		}
		
		int count = jobs.size();
		if(count > 0) log.lifecycle("] {} remappable dependenc{}, dest {}", count, count == 1 ? "y" : "ies", remappedModCache);
		
		cleanOnRefreshDependencies(remappedModCache);
		
		for(RemappingJob job : jobs) {
			log.info("|-> Found a mod dependency at {}", job.unmappedPath);
			log.info("\\-> Need to remap to {}", job.mappedPath);
			
			//perform remap, if the output file does not exist
			if(Files.notExists(job.mappedPath)) {
				log.info("\\-> Remapped file doesn't exist, running remapper...");
				
				try {
					Set<Path> remapClasspath = new HashSet<>();
					
					remapClasspath.add(minecraft);
					remapClasspath.addAll(nonNativeLibraries);
					for(File file : getConfigurationByName(Constants.EVERY_UNMAPPED_MOD).getFiles()) {
						Path p = file.toPath();
						if(!p.equals(job.unmappedPath)) {
							remapClasspath.add(p);
						}
					}
					
					// If the sources don't exist, we want remapper to give nicer names to the missing variable names.
					// However, if the sources do exist, if remapper gives names to the parameters that prevents IDEs (at least IDEA)
					// from replacing the parameters with the actual names from the sources.
					//boolean sourcesExist = findSources(project.getDependencies(), artifact) != null;
					boolean sourcesExist = false;
					
					new TinyRemapperSession()
						.setMappings(tinyTree)
						.setInputNamingScheme(distributionNamingScheme)
						.setInputJar(job.unmappedPath)
						.setInputClasspath(remapClasspath)
						.addOutputJar(Constants.MAPPED_NAMING_SCHEME, job.mappedPath)
						.setLogger(log::info)
						.dontRemapLocalVariables()
						.run();
					
					if (Files.notExists(job.mappedPath)) {
						throw new RuntimeException("Failed to remap JAR to 'named' - file not found: " + job.mappedPath.toAbsolutePath());
					}
				} catch (Exception e) {
					throw new RuntimeException("Failed to remap dependency at " + job.unmappedPath + " to " + job.mappedPath, e);
				}
				
				log.info("\\-> Done! :)");
			} else {
				log.info("\\-> Already remapped.");
			}
			
			//add it as a dependency to the project
			//TODO move this to a separate step
			log.info("\\-> Adding remapped result to the '{}' configuration", job.outConfig.getName());
			project.getDependencies().add(job.outConfig.getName(), files(job.mappedPath));
		}
		
		return this;
	}
}

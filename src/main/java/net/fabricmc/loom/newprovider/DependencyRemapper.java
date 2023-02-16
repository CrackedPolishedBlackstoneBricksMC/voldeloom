package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.util.TinyRemapperSession;
import net.fabricmc.mapping.tree.TinyTree;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
	
	//inputs
	private MappingsWrapper mappingsWrapper;
	private TinyTree tinyTree;
	private Iterable<RemappedConfigurationEntry> remappedConfigurationEntries;
	private String distributionNamingScheme;
	private final Set<Path> remapClasspath = new HashSet<>();
	
	public DependencyRemapper mappings(MappingsWrapper mappingsWrapper) {
		this.mappingsWrapper = mappingsWrapper;
		return this;
	}
	
	public DependencyRemapper tinyTree(TinyTree tinyTree) {
		this.tinyTree = tinyTree;
		return this;
	}
	
	public DependencyRemapper remappedConfigurationEntries(Iterable<RemappedConfigurationEntry> remappedConfigurationEntries) {
		this.remappedConfigurationEntries = remappedConfigurationEntries;
		return this;
	}
	
	public DependencyRemapper distributionNamingScheme(String distributionNamingScheme) {
		this.distributionNamingScheme = distributionNamingScheme;
		return this;
	}
	
	public DependencyRemapper addToRemapClasspath(Collection<Path> remapClasspath) {
		this.remapClasspath.addAll(remapClasspath);
		return this;
	}
	
	public DependencyRemapper addToRemapClasspath(Path... paths) {
		return addToRemapClasspath(Arrays.asList(paths));
	}
	
	private static class RemappingJob {
		public RemappingJob(Path unmappedPath, Path mappedPath, Configuration outConfig) {
			this.unmappedPath = unmappedPath;
			this.mappedPath = mappedPath;
			this.outConfig = outConfig;
		}
		
		final Path unmappedPath, mappedPath;
		final Configuration outConfig;
	}
	private final List<RemappingJob> finishedRemaps = new ArrayList<>();
	
	public DependencyRemapper remapDependencies() throws Exception {
		Preconditions.checkNotNull(mappingsWrapper, "mappings version");
		Preconditions.checkNotNull(tinyTree, "tiny tree");
		Preconditions.checkNotNull(remappedConfigurationEntries, "remapped configuration entries");
		Preconditions.checkNotNull(distributionNamingScheme, "distribution naming scheme");
		
		List<RemappingJob> jobs = new ArrayList<>();
		
		String mappingsSuffix = mappingsWrapper.getMappingsDepString().replaceAll("[^A-Za-z0-9.-]", "_");
		Path remappedModCache = getRemappedModCache();
		cleanOnRefreshDependencies(remappedModCache);
		
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
		
		for(RemappingJob job : jobs) {
			log.info("|-> Found a mod dependency at {}", job.unmappedPath);
			log.info("\\-> Need to remap to {}", job.mappedPath);
			
			//perform remap, if the output file does not exist
			if(Files.notExists(job.mappedPath)) {
				log.info("\\-> Remapped file doesn't exist, running remapper...");
				
				try {
					Set<Path> remapClasspath = new HashSet<>(this.remapClasspath);
					
					//add the other mod dependencies to the remap classpath
					for(File file : getConfigurationByName(Constants.EVERY_UNMAPPED_MOD).getFiles()) {
						Path p = file.toPath();
						if(!p.equals(job.unmappedPath)) remapClasspath.add(p);
					}
					
					// If the sources don't exist, we want remapper to give nicer names to the missing variable names.
					// However, if the sources do exist, if remapper gives names to the parameters that prevents IDEs (at least IDEA)
					// from replacing the parameters with the actual names from the sources.
					//boolean sourcesExist = findSources(project.getDependencies(), artifact) != null;
					boolean sourcesExist = false; //im lazy sorry
					
					new TinyRemapperSession()
						.setMappings(tinyTree)
						.setInputNamingScheme(distributionNamingScheme)
						.setInputJar(job.unmappedPath)
						.setInputClasspath(remapClasspath)
						.addOutputJar(Constants.MAPPED_NAMING_SCHEME, job.mappedPath)
						.setLogger(log::info)
						.dontRemapLocalVariables()
						.run();
				} catch (Exception e) {
					throw new RuntimeException("Failed to remap dependency at " + job.unmappedPath + " to " + job.mappedPath, e);
				}
				
				if(Files.notExists(job.mappedPath)) {
					throw new RuntimeException("Failed to remap dependency at " + job.unmappedPath + " to " + job.mappedPath + " - the target file doesn't exist, hmm");
				}
			} else log.info("\\-> Already remapped.");
			
			finishedRemaps.add(job);
		}
		
		return this;
	}
	
	public DependencyRemapper installDependenciesToProject(DependencyHandler deps) {
		for(RemappingJob finishedJob : finishedRemaps) {
			log.info("\\-> Adding remapped mod at {} to the '{}' configuration", finishedJob.mappedPath, finishedJob.outConfig.getName());
			deps.add(finishedJob.outConfig.getName(), files(finishedJob.mappedPath));
		}
		
		return this;
	}
}

package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.mcp.Members;
import net.fabricmc.loom.mcp.Srg;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class DependencyRemapperMcp extends NewProvider<DependencyRemapperMcp> {
	public DependencyRemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
		setProjectmapped(true); //all dependencies are project-local
		
		tinyRemapperFactory = () -> new RemapperMcp(project, extension);
		naiveRenamerFactory = () -> new NaiveRenamer(project, extension);
	}
	
	private final Supplier<RemapperMcp> tinyRemapperFactory;
	private final Supplier<NaiveRenamer> naiveRenamerFactory;
	
	private String mappingsDepString;
	private Srg srg;
	private Members fields, methods;
	private Iterable<RemappedConfigurationEntry> remappedConfigurationEntries;
	private String distributionNamingScheme; //TODO weird
	private final Set<Path> remapClasspath = new HashSet<>();
	
	public DependencyRemapperMcp mappingsDepString(String mappingsDepString) {
		this.mappingsDepString = mappingsDepString;
		return this;
	}
	
	public DependencyRemapperMcp srg(Srg srg) {
		this.srg = srg;
		return this;
	}
	
	public DependencyRemapperMcp fields(Members fields) {
		this.fields = fields;
		return this;
	}
	
	public DependencyRemapperMcp methods(Members methods) {
		this.methods = methods;
		return this;
	}
	
	public DependencyRemapperMcp remappedConfigurationEntries(Iterable<RemappedConfigurationEntry> remappedConfigurationEntries) {
		this.remappedConfigurationEntries = remappedConfigurationEntries;
		return this;
	}
	
	public DependencyRemapperMcp distributionNamingScheme(String distributionNamingScheme) {
		this.distributionNamingScheme = distributionNamingScheme;
		return this;
	}
	
	public DependencyRemapperMcp addToRemapClasspath(Collection<Path> remapClasspath) {
		this.remapClasspath.addAll(remapClasspath);
		return this;
	}
	
	public DependencyRemapperMcp addToRemapClasspath(Path... paths) {
		return addToRemapClasspath(Arrays.asList(paths));
	}
	
	public DependencyRemapperMcp doIt(DependencyHandler deps) throws Exception {
		Path remappedModCache = getRemappedModCache();
		cleanOnRefreshDependencies(remappedModCache);
		
		for(RemappedConfigurationEntry entry : remappedConfigurationEntries) {
			Configuration inputConfig = entry.getInputConfig();
			Configuration outputConfig = entry.getOutputConfig();
			
			for(File unmappedFile : inputConfig.getResolvedConfiguration().getFiles()) {
				Path unmappedPath = unmappedFile.toPath();
				Path mappedPath = remappedModCache.resolve(unmappedPath.getFileName().toString() + "-mapped-" + mappingsDepString + ".jar");
				
				log.info("|-> Found a mod dependency at {}", unmappedPath);
				log.info("\\-> Need to remap to {}", mappedPath);
				
				//If mods are distributed proguarded, first run them through tiny-remapper to apply srg names
				Path srgMappedPath;
				if(distributionNamingScheme.equals(Constants.INTERMEDIATE_NAMING_SCHEME)) {
					log.info("\\-> distributionNamingScheme == Constants.INTERMEDIATE_NAMING_SCHEME, not applying tiny-remapper");
					srgMappedPath = unmappedPath;
				} else if(distributionNamingScheme.equals(Constants.PROGUARDED_NAMING_SCHEME)) {
					srgMappedPath = remappedModCache.resolve(unmappedPath.getFileName().toString() + "-srg-" + mappingsDepString + ".jar");
					
					log.info("\\-> First, mapping to SRG using tiny-remapper at {}", srgMappedPath);
					
					//add the other mod dependencies to the remap classpath
					Set<Path> remapClasspathIncludingOtherMods = new HashSet<>(remapClasspath);
					for(File file : getConfigurationByName(Constants.EVERY_UNMAPPED_MOD).getFiles()) {
						Path p = file.toPath();
						if(!p.equals(unmappedPath)) remapClasspathIncludingOtherMods.add(p);
					}
					
					tinyRemapperFactory.get()
						.inputJar(unmappedPath)
						.srg(srg)
						.outputSrgJar_Generic(srgMappedPath)
						.addToRemapClasspath(remapClasspathIncludingOtherMods)
						.remap();
				} else {
					throw new IllegalArgumentException("i should make than an enum");
				}
				
				//Then apply the fields.csv and methods.csv transformation, just like vanilla
				log.info("\\-> Applying NaiveRenamer...");
				naiveRenamerFactory.get()
					.fields(fields)
					.methods(methods)
					.input(srgMappedPath)
					.output(mappedPath)
					.doIt();
				
				//Finally, install this jar to the dependencies (TODO break this out into a separate pass, i'm lazy)
				log.info("\\-> Installing to {} configuration", outputConfig.getName());
				deps.add(outputConfig.getName(), files(mappedPath));
			}
		}
		
		return this;
	}
}

package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.mcp.Members;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DependencyRemapperMcp extends NewProvider<DependencyRemapperMcp> {
	public DependencyRemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private String mappingsDepString;
	private Srg srg;
	private Members fields, methods;
	private Iterable<RemappedConfigurationEntry> remappedConfigurationEntries;
	private String distributionNamingScheme; //TODO weird
	private final Set<Path> remapClasspath = new LinkedHashSet<>();
	private boolean needsAsm4;
	
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
	
	public DependencyRemapperMcp needsAsm4(boolean needsAsm4) {
		this.needsAsm4 = needsAsm4;
		return this;
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
				
				if(Files.notExists(mappedPath)) {
					//If mods are distributed proguarded, first run them through tiny-remapper to apply srg names
					Path srgMappedPath;
					if(distributionNamingScheme.equals(Constants.INTERMEDIATE_NAMING_SCHEME)) {
						log.info("\\-> distributionNamingScheme == Constants.INTERMEDIATE_NAMING_SCHEME, not applying tiny-remapper");
						srgMappedPath = unmappedPath;
					} else if(distributionNamingScheme.equals(Constants.PROGUARDED_NAMING_SCHEME)) {
						srgMappedPath = remappedModCache.resolve(unmappedPath.getFileName().toString() + "-srg-" + mappingsDepString + ".jar");
						
						log.info("\\-> First, mapping to SRG using tiny-remapper at {}", srgMappedPath);
						
						//add the other mod dependencies to the remap classpath
//						int fzClasses = 256;
//						while(true) {
							
							Set<Path> remapClasspathIncludingOtherMods = new LinkedHashSet<>(remapClasspath);
							for(File file : getConfigurationByName(Constants.EVERY_UNMAPPED_MOD).getFiles()) {
								Path p = file.toPath();
								if(!p.equals(unmappedPath)) remapClasspathIncludingOtherMods.add(p);
//								if(!p.equals(unmappedPath)) {
//									if(p.toString().contains("Factorization")) {
//										Path limited = p.resolveSibling("work").resolve("fz-" + fzClasses + "-classes.jar");
//										Files.createDirectories(limited.getParent());
//										evilFuckedUpFactorization(p, limited, fzClasses);
//
//										remapClasspathIncludingOtherMods.add(limited);
//										//if(unmappedPath.toString().contains("Factorization")) {
//											unmappedPath = limited;
//											srgMappedPath = srgMappedPath.resolveSibling("fz-" + fzClasses + "-classes-mapped.jar");
//										//}
//										fzClasses++;
//									} else remapClasspathIncludingOtherMods.add(p);
//								}
							}
//							System.out.println("REMAP CLASSPATH: " + remapClasspathIncludingOtherMods.stream().map(Object::toString).collect(Collectors.joining(",")));
//							System.out.println(unmappedPath);
//							System.out.println(srgMappedPath);
							RemapperMcp.doIt(unmappedPath, srgMappedPath, srg, log, null, remapClasspathIncludingOtherMods, needsAsm4);
							System.out.println("remap didn't crash, running it back");
//						}
					} else {
						throw new IllegalArgumentException("Unknown distributionNamingScheme... i should make than an enum");
					}
					
					//Then apply the fields.csv and methods.csv transformation, just like vanilla
					log.info("\\-> Applying NaiveRenamer...");
					NaiveRenamer.doIt(srgMappedPath, mappedPath, log, fields, methods);
				}
				
				//Finally, install this jar to the dependencies (TODO break this out into a separate pass, i'm lazy)
				log.info("\\-> Installing to {} configuration", outputConfig.getName());
				deps.add(outputConfig.getName(), files(mappedPath));
			}
		}
		
		return this;
	}
	
//	public void evilFuckedUpFactorization(Path fzIn, Path fzOut, int filesToCopy) {
//		System.out.println("Preparing Factorization with " + filesToCopy + " classes at " + fzOut);
//		try(
//			ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(fzIn)));
//			ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(fzOut)))
//		) {
//			int filesToGo = filesToCopy;
//			while(filesToGo > 0) {
//				ZipEntry e = zin.getNextEntry();
//				if(e == null) throw new RuntimeException("Copied every class in FZ");
//
//				//skip the known-bad one
//				if(e.getName().endsWith("GenericProxyPlayer.class")) {
//					System.out.println("Skipping GenericProxyPlayer");
//					continue;
//				}
//
//				zout.putNextEntry(e);
//				zout.write(ZipUtil.readFully(zin));
//				zout.closeEntry();
//
//				if(e.getName().endsWith(".class")) filesToGo--;
//				if(filesToGo == 0) System.out.println("Last file copied was " + e.getName());
//			}
//			zout.flush();
//		} catch (Exception e) {
//			throw new RuntimeException("ope", e);
//		}
//
//		try {
//			FileChannel ope = FileChannel.open(fzOut);
//			ope.force(true);
//		} catch (Exception e) {
//			throw new RuntimeException("flush dammit");
//		}
//	}
}

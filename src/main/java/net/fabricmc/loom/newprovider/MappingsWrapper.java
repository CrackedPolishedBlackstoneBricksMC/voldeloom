package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.loom.util.mcp.Members;
import net.fabricmc.loom.util.mcp.Packages;
import net.fabricmc.loom.util.mcp.Srg;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

/**
 * Loads and parses MCP mappings.
 * <p>
 * TODO: more than just MCP!
 */
public class MappingsWrapper extends ResolvedConfigElementWrapper {
	public MappingsWrapper(Project project, LoomGradleExtension extension, Configuration config) throws Exception {
		super(project, config);
		Logger log = project.getLogger();
		
		//TODO: REMOVE this hack
		if(extension.forgeCapabilities.srgsAsFallback.get()) mappingDiscriminant += "-srgfallback";
		
		mappingsDepString = getDepString() + mappingDiscriminant;
		log.lifecycle("] mappings dep: {}", mappingsDepString);
		log.lifecycle("] mappings source: {}", getPath());
		
		try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + getPath().toUri()), Collections.emptyMap())) {
			//TODO: Remove this crap when i do the good mappings system
			Path tinyv2FunnyMoments = mcpZipFs.getPath("mappings/mappings.tiny");
			if(Files.exists(tinyv2FunnyMoments)) {
				//WOW its already in tinyv2 format how neat!!!
				log.warn("MAPPINGS ALREADY TINYv2 I THINK!!!!! Fyi it should probably contain {} {} {} headers", Constants.PROGUARDED_NAMING_SCHEME, Constants.INTERMEDIATE_NAMING_SCHEME, Constants.MAPPED_NAMING_SCHEME);
				alreadyTinyv2 = true;
			} else {
				StringInterner strings = new StringInterner();
				
				log.info("|-> Looking for MCP mappings...");
				MCPMappingsRootFinder finder = new MCPMappingsRootFinder();
				Files.walkFileTree(mcpZipFs.getPath("/"), finder);
				Path conf = finder.mappingsRoot;
				if(conf == null) {
					throw new IllegalArgumentException("Unable to find MCP mappings in " + getPath() + " - " + finder.explain());
				}
				log.info("] Mappings root detected to be '{}'", conf);
				
				log.info("|-> Reading joined.srg...");
				if(Files.exists(conf.resolve("joined.srg"))) {
					joined = new Srg().read(conf.resolve("joined.srg"), strings);
				} else {
					//just assume we're manually merging a client and server srg
					//TODO: newids?
					log.info("\\-> No joined.srg exists. Reading client.srg...");
					Srg client = new Srg().read(conf.resolve("client.srg"), strings);
					
					log.info("\\-> Reading server.srg...");
					Srg server = new Srg().read(conf.resolve("server.srg"), strings);
					
					log.info("\\-> Manually joining srgs...");
					joined = client.mergeWith(server);
				}
				
				//TODO YEET this into the stratosphere
				for(String deleteThis : extension.hackHackHackDontMapTheseClasses) {
					joined.unmapClass(deleteThis);
				}
				
				log.info("|-> Reading fields.csv...");
				fields = new Members().read(conf.resolve("fields.csv"), strings);
				
				log.info("|-> Reading methods.csv...");
				methods = new Members().read(conf.resolve("methods.csv"), strings);
				
				log.info("|-> Reading packages.csv...");
				packages = new Packages();
				if(Files.exists(conf.resolve("packages.csv"))) {
					packages.read(conf.resolve("packages.csv"), strings);
				} else {
					log.info("\\-> No packages.csv exists.");
				}
				
				strings.close();
				log.info("|-> Done!");
			}
		}
	}
	
	//TODO: It's Bad!
	
	private final String mappingsDepString;
	private String mappingDiscriminant = "";
	
	private Srg joined;
	private Packages packages;
	private Members fields;
	private Members methods;
	
	private boolean alreadyTinyv2 = false;
	
	public String getMappingsDepString() {
		return mappingsDepString;
	}
	
	public String getMappingDiscriminant() {
		return mappingDiscriminant;
	}
	
	public Srg getJoined() {
		return joined;
	}
	
	public Packages getPackages() {
		return packages;
	}
	
	public Members getFields() {
		return fields;
	}
	
	public Members getMethods() {
		return methods;
	}
	
	public boolean isAlreadyTinyv2() {
		return alreadyTinyv2;
	}
	
	private static class MCPMappingsRootFinder extends SimpleFileVisitor<Path> {
		public Path mappingsRoot;
		
		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			//signal 1: look for an mcp.cfg file.
			//This file existing is a pretty unambiguous signal, but it is unlikely to be written by anything emitting
			//MCP artifacts in the modern day (it is a configuration file for the Python 2-based MCP tooling)
			if(Files.exists(dir.resolve("mcp.cfg"))) {
				mappingsRoot = dir;
				return FileVisitResult.TERMINATE;
			}
			
			//signal 2: Look for a version.cfg file.
			//That's kind of a generic filename, so also check that it actually looks like an MCP version file.
			Path versionCfg = dir.resolve("version.cfg");
			if(Files.exists(versionCfg) && new String(Files.readAllBytes(versionCfg), StandardCharsets.UTF_8).contains("MCPVersion")) {
				mappingsRoot = dir;
				return FileVisitResult.TERMINATE;
			}
			
			return FileVisitResult.CONTINUE;
		}
		
		String explain() {
			return "didn't find a 'mcp.cfg' file, and didn't find a 'version.cfg' file containing the string 'MCPVersion'";
		}
	}
}

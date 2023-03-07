package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.McpMappings;
import net.fabricmc.loom.util.mcp.Members;
import net.fabricmc.loom.util.mcp.Srg;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Loads and parses MCP mappings. thats the goal anyway
 * <br>
 * legacy somethingorother. in the process of phasing this class out.
 * The main reason this class is used (instead of McpMappings directly) is due to the "tinyv2 passthrough" feature, that should be implemented a different way.
 * If tinyv2 mappings shall be supported, they should be done by importing tinyfiles to the mcp format, not passing them straight through, tbh
 */
public class MappingsWrapper extends ResolvedConfigElementWrapper {
	public MappingsWrapper(Project project, LoomGradleExtension extension, Configuration config, @Nullable String discrim) throws Exception {
		super(project, config);
		Logger log = project.getLogger();
		
		//TODO: REMOVE this hack
		if(discrim != null) mappingDiscriminant += "-" + discrim;
		if(extension.forgeCapabilities.srgsAsFallback.get()) mappingDiscriminant += "-srgfallback";
		
		log.lifecycle("] mappings source: {}", getPath());
		
		try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + getPath().toUri()), Collections.emptyMap())) {
			//TODO: Remove this crap when i do the good mappings system
			Path tinyv2FunnyMoments = mcpZipFs.getPath("mappings/mappings.tiny");
			if(Files.exists(tinyv2FunnyMoments)) {
				//WOW its already in tinyv2 format how neat!!!
				log.warn("MAPPINGS ALREADY TINYv2 I THINK!!!!! Fyi it should probably contain {} {} {} headers", Constants.PROGUARDED_NAMING_SCHEME, Constants.INTERMEDIATE_NAMING_SCHEME, Constants.MAPPED_NAMING_SCHEME);
				alreadyTinyv2 = true;
			} else {
				mappings = new McpMappings().importFromZip(log, mcpZipFs);
				
				//TODO YEET this into the stratosphere
				// replace with layered mappings
				for(String deleteThis : extension.hackHackHackDontMapTheseClasses) {
					mappings.joined.unmapClass(deleteThis);
					mappings.client.unmapClass(deleteThis);
					mappings.server.unmapClass(deleteThis);
				}
				
				log.info("|-> Done!");
			}
		}
	}
	
	//TODO: It's Bad!
	private String mappingDiscriminant = "";
	//TODO: It's Bad!
	private boolean alreadyTinyv2 = false; //Also Bad!
	
	private McpMappings mappings;
	
	public String getMappingDiscriminant() {
		return mappingDiscriminant;
	}
	
	public Srg getJoined() {
		return mappings.joined;
	}
	
	public Srg getClient() {
		return mappings.client;
	}
	
	public Srg getServer() {
		return mappings.server;
	}
	
	public Members getFields() {
		return mappings.fields;
	}
	
	public Members getMethods() {
		return mappings.methods;
	}
	
	public boolean isAlreadyTinyv2() {
		return alreadyTinyv2;
	}
}

package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.McpMappings;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Collections;

/**
 * Loads and parses MCP mappings from a file.
 */
public class MappingsWrapper extends ResolvedConfigElementWrapper {
	public MappingsWrapper(Project project, LoomGradleExtension extension, Configuration config) throws Exception {
		super(project, config);
		Logger log = project.getLogger();
		
		log.lifecycle("] mappings source: {}", getPath());
		
		try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + getPath().toUri()), Collections.emptyMap())) {
			mappings = new McpMappings().importFromZip(log::info, mcpZipFs);
			log.info("|-> Done!");
		}
	}
	
	public McpMappings mappings;
}

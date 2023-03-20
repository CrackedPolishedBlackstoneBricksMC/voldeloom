package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.JarScanData;
import net.fabricmc.loom.mcp.McpMappings;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Loads and parses MCP mappings from a file.
 */
public class MappingsWrapper extends ResolvedConfigElementWrapper {
	public MappingsWrapper(Project project, LoomGradleExtension extension, Configuration config, Path scanJar) throws Exception {
		super(project, config);
		Logger log = project.getLogger();
		
		log.lifecycle("] mappings source: {}", getPath());
		
		log.info("|-> Loading mappings...");
		try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + getPath().toUri()), Collections.emptyMap())) {
			mappings = new McpMappings().importFromZip(log::info, mcpZipFs);
		}
		
		log.info("|-> Loaded. Gleaning inner-class info from '{}'...", scanJar);
		JarScanData scan = new JarScanData().scan(scanJar);
		
		log.info("|-> Augmenting mappings with inner-class info...");
		mappings.augment(scan);
	}
	
	public McpMappings mappings;
}

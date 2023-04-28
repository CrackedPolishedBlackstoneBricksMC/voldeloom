package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.mcp.JarScanData;
import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.mcp.McpMappingsBuilder;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Props;
import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Loads and parses MCP mappings from a file.
 */
public class MappingsWrapper extends ResolvedConfigElementWrapper {
	public MappingsWrapper(Project project, Configuration config, Path scanJar) throws Exception {
		super(project, config);
		Logger log = project.getLogger();
		
		log.lifecycle("] mappings source: {}", getPath());
		
		log.info("|-> Loading mappings...");
		McpMappingsBuilder mappingsBuilder = new McpMappingsBuilder();
		StringInterner mem = new StringInterner();
		try(FileSystem mcpZipFs = ZipUtil.openFs(getPath())) {
			mappingsBuilder.importEverythingFromZip(mcpZipFs, mem);
		}
		
		log.info("|-> Gleaning inner-class info from '{}'...", scanJar);
		mappingsBuilder.augment(new JarScanData().scan(scanJar));
		
		log.info("|-> Building...");
		mappings = mappingsBuilder.build();
		
		MessageDigest sha = Checksum.SHA256.get();
		Checksum.feedFileToHasher(getPath(), sha);
		this.props = new Props().put("mappings-hash", Checksum.toHexString(sha.digest()));
	}
	
	public final McpMappings mappings;
	public final Props props;
}

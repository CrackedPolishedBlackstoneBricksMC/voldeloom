package net.fabricmc.loom.util.mcp;

import net.fabricmc.loom.util.StringInterner;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

/**
 * "MCP mappings" is a loose name for a collection of these files:
 * <ul>
 *   <li>joined.srg, or both client.srg and server.srg</li>
 *   <li>fields.csv</li>
 *   <li>methods.csv</li>
 *   <li>(optionally) packages.csv</li>
 * </ul>
 */
public class McpMappings {
	public Srg joined = new Srg();
	public Srg client = new Srg();
	public Srg server = new Srg();
	public Members fields = new Members();
	public Members methods = new Members();
	public Packages packages = new Packages();
	
	public McpMappings importFromZip(Logger log, FileSystem fs) throws IOException {
		McpMappings mcp = new McpMappings();
		StringInterner mem = new StringInterner();
		
		Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				switch(path.getFileName().toString()) {
					case "joined.srg":   log.info("\\-> Reading {}", path); mcp.joined.read(path, mem);   break;
					case "client.srg":   log.info("\\-> Reading {}", path); mcp.client.read(path, mem);   break;
					case "server.srg":   log.info("\\-> Reading {}", path); mcp.server.read(path, mem);   break;
					case "fields.csv":   log.info("\\-> Reading {}", path); mcp.fields.read(path, mem);   break;
					case "methods.csv":  log.info("\\-> Reading {}", path); mcp.methods.read(path, mem);  break;
					case "packages.csv": log.info("\\-> Reading {}", path); mcp.packages.read(path, mem); break;
				}
				
				return FileVisitResult.CONTINUE;
			}
		});
		
		return mcp;
	}
	
	public McpMappings importFromZip(Logger log, Path zip) throws IOException {
		try(FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Collections.emptyMap())) {
			return importFromZip(log, fs);
		}
	}
}

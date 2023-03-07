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
	
	public McpMappings importFromZip(Logger log, FileSystem fs) throws IOException {
		McpMappings mcp = new McpMappings();
		StringInterner mem = new StringInterner();
		
		//look for all the relevant files inside the jar
		FindingVisitor v = new FindingVisitor();
		Files.walkFileTree(fs.getPath("/"), v);
		
		//load packaging transformation (if one exists)
		Packages packages;
		if(v.packagesPath == null) {
			log.info("\\-> No packaging transformation.");
			packages = null;
		} else {
			log.info("\\-> Reading {}", v.packagesPath);
			packages = new Packages().read(v.packagesPath, mem);
		}
		
		//parse the THINGS
		if(v.joinedPath != null) {
			log.info("\\-> Reading {}", v.joinedPath);
			mcp.joined = new Srg().read(v.joinedPath, mem);
			if(packages != null) {
				log.info("\\-> Repackaging {}", v.joinedPath);
				mcp.joined = mcp.joined.repackage(packages);
			}
		}
		if(v.clientPath != null) {
			log.info("\\-> Reading {}", v.clientPath);
			mcp.client = new Srg().read(v.clientPath, mem);
			if(packages != null) {
				log.info("\\-> Repackaging {}", v.clientPath);
				mcp.client = mcp.client.repackage(packages);
			}
		}
		if(v.serverPath != null) {
			log.info("\\-> Reading {}", v.serverPath);
			mcp.server = new Srg().read(v.serverPath, mem);
			if(packages != null) {
				log.info("\\-> Repackaging {}", v.serverPath);
				mcp.server = mcp.server.repackage(packages);
			}
		}
		if(v.fieldsPath != null) {
			log.info("\\-> Reading {}", v.fieldsPath);
			mcp.fields = new Members().read(v.fieldsPath, mem);
		}
		if(v.methodsPath != null) {
			log.info("\\-> Reading {}", v.methodsPath);
			mcp.methods = new Members().read(v.methodsPath, mem);
		}
		
		return mcp;
	}
	
	public McpMappings importFromZip(Logger log, Path zip) throws IOException {
		try(FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Collections.emptyMap())) {
			return importFromZip(log, fs);
		}
	}
	
	private static class FindingVisitor extends SimpleFileVisitor<Path> {
		private Path joinedPath, clientPath, serverPath, fieldsPath, methodsPath, packagesPath;
		
		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
			switch(path.getFileName().toString()) {
				case "joined.srg":   joinedPath = path;   break;
				case "client.srg":   clientPath = path;   break;
				case "server.srg":   serverPath = path;   break;
				case "fields.csv":   fieldsPath = path;   break;
				case "methods.csv":  methodsPath = path;  break;
				case "packages.csv": packagesPath = path; break;
			}
			
			return FileVisitResult.CONTINUE;
		}
	}
}

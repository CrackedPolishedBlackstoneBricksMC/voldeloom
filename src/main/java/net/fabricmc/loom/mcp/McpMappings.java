package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.tinyremapper.IMappingProvider;

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
import java.util.function.Consumer;
import java.util.function.Function;

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
	
	public McpMappings importFromZip(Consumer<String> log, FileSystem fs) throws IOException {
		StringInterner mem = new StringInterner();
		
		//look for all the relevant files inside the jar
		FindingVisitor v = new FindingVisitor();
		Files.walkFileTree(fs.getPath("/"), v);
		
		//load packaging transformation (if one exists)
		Packages packages;
		if(v.packagesPath == null) {
			log.accept("\\-> No packaging transformation.");
			packages = null;
		} else {
			log.accept("\\-> Reading " + v.packagesPath);
			packages = new Packages().read(v.packagesPath, mem);
		}
		
		//parse the THINGS
		//TODO: doesn't interact suuuper great if you call importFromZip twice on the same McpMappings object
		if(v.joinedPath != null) {
			log.accept("\\-> Reading " + v.joinedPath);
			joined.read(v.joinedPath, mem);
			if(packages != null) {
				log.accept("\\-> Repackaging " + v.joinedPath);
				joined = joined.repackage(packages);
			}
		}
		if(v.clientPath != null) {
			log.accept("\\-> Reading " + v.clientPath);
			client.read(v.clientPath, mem);
			if(packages != null) {
				log.accept("\\-> Repackaging " + v.clientPath);
				client = client.repackage(packages);
			}
		}
		if(v.serverPath != null) {
			log.accept("\\-> Reading " + v.serverPath);
			server.read(v.serverPath, mem);
			if(packages != null) {
				log.accept("\\-> Repackaging " + v.serverPath);
				server = server.repackage(packages);
			}
		}
		if(v.fieldsPath != null) {
			log.accept("\\-> Reading " + v.fieldsPath);
			fields.read(v.fieldsPath, mem);
		}
		if(v.methodsPath != null) {
			log.accept("\\-> Reading " + v.methodsPath);
			methods.read(v.methodsPath, mem);
		}
		
		return this;
	}
	
	public McpMappings importFromZip(Consumer<String> log, Path zip) throws IOException {
		try(FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Collections.emptyMap())) {
			return importFromZip(log, fs);
		}
	}
	
	public IMappingProvider toTinyRemapper(Function<McpMappings, Srg> whichSrg, boolean toNamed) {
		Srg srg = whichSrg.apply(this);
		
		return acceptor -> {
			srg.classMappings.forEach(acceptor::acceptClass);
			
			srg.fieldMappingsByOwningClass.forEach((owningClass, fieldMappings) ->
				fieldMappings.forEach((oldName, newName) -> {
					if(toNamed) {
						Members.Entry remapped = fields.remapSrg(newName);
						if(remapped != null) newName = remapped.remappedName;
					}
					
					//make up a field desc
					acceptor.acceptField(new IMappingProvider.Member(owningClass, oldName, "Ljava/lang/Void;"), newName);
				}));
			
			srg.methodMappingsByOwningClass.forEach((owningClass, methodMappings) -> 
				methodMappings.forEach((oldMethod, newMethod) -> {
					String newMethodName = newMethod.name;
					if(toNamed) {
						Members.Entry remapped = methods.remapSrg(newMethod.name);
						if(remapped != null) newMethodName = remapped.remappedName;
					}
					
					acceptor.acceptMethod(new IMappingProvider.Member(owningClass, oldMethod.name, oldMethod.descriptor), newMethodName);
				}));
		};
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

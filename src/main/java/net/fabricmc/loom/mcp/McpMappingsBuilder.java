package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

public class McpMappingsBuilder {
	//joined srg only:
	public Srg joined = new Srg();
	public Packages packages = new Packages();
	
	//split srgs only:
	public Srg client = new Srg();
	public Srg server = new Srg();
	
	//shared between both:
	public Members fields = new Members();
	public Members methods = new Members();
	
	public McpMappings build() {
		//Only apply packaging transformation to joined srg. Packaging transformations theoretically make sense for client/server
		//srgs too, but this never happened in practice because packages.csv was invented in 1.4, after the client/server srg merge.
		//todo: might be nice to come up with an anachronistic way to do it?
		if(!packages.isEmpty() && !joined.isEmpty()) joined = joined.repackage(packages);
		
		return new McpMappings(joined, client, server, fields, methods);
	}
	
	public McpMappingsBuilder mergeWith(McpMappingsBuilder other) {
		joined.mergeWith(other.joined);
		packages.mergeWith(other.packages);
		
		client.mergeWith(other.client);
		server.mergeWith(other.server);
		
		fields.mergeWith(other.fields);
		methods.mergeWith(other.methods);
		
		return this;
	}
	
	public McpMappingsBuilder augment(JarScanData jarScanData) {
		if(!joined.isEmpty()) joined.augment(jarScanData);
		if(!client.isEmpty()) client.augment(jarScanData);
		if(!server.isEmpty()) server.augment(jarScanData);
		
		return this;
	}
	
	public void mergeFromJoinedSrg(Path path, StringInterner mem) throws IOException {
		joined.mergeWith(new Srg().read(path, mem));
	}
	
	public void mergeFromPackagesCsv(Path path, StringInterner mem) throws IOException {
		packages.mergeWith(new Packages().read(path, mem));
	}
	
	public void mergeFromClientSrg(Path path, StringInterner mem) throws IOException {
		client.mergeWith(new Srg().read(path, mem));
	}
	
	public void mergeFromServerSrg(Path path, StringInterner mem) throws IOException {
		server.mergeWith(new Srg().read(path, mem));
	}
	
	public void mergeFromFieldsCsv(Path path, StringInterner mem) throws IOException {
		fields.mergeWith(new Members().read(path, mem));
	}
	
	public void mergeFromMethodsCsv(Path path, StringInterner mem) throws IOException {
		methods.mergeWith(new Members().read(path, mem));
	}
	
	public static @Nullable Path find(FileSystem fs, String name) throws IOException {
		Path[] result = new Path[] { null };
		
		Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
				if(path.getFileName().toString().equals(name)) {
					result[0] = path;
					return FileVisitResult.TERMINATE;
				} else return FileVisitResult.CONTINUE;
			}
		});
		
		return result[0];
	}
	
	/// TODO: It's not great how this api does a whole walkFileTree on each call ///
	
	public void mergeFromFoundJoinedSrg(FileSystem fs, StringInterner mem) throws IOException {
		Path path = find(fs, "joined.srg");
		if(path == null) path = find(fs, "joined.csrg");
		
		if(path != null) mergeFromJoinedSrg(path, mem);
	}
	
	public void mergeFromFoundPackagesCsv(FileSystem fs, StringInterner mem) throws IOException {
		Path path = find(fs, "packages.csv");
		if(path != null) mergeFromPackagesCsv(path, mem);
	}
	
	public void mergeFromFoundClientSrg(FileSystem fs, StringInterner mem) throws IOException {
		Path path = find(fs, "joined.srg");
		if(path != null) mergeFromClientSrg(path, mem);
	}
	
	public void mergeFromFoundServerSrg(FileSystem fs, StringInterner mem) throws IOException {
		Path path = find(fs, "server.srg");
		if(path != null) mergeFromServerSrg(path, mem);
	}
	
	public void mergeFromFoundFieldsCsv(FileSystem fs, StringInterner mem) throws IOException {
		Path path = find(fs, "fields.csv");
		if(path != null) mergeFromFieldsCsv(path, mem);
	}
	
	public void mergeFromFoundMethodsCsv(FileSystem fs, StringInterner mem) throws IOException {
		Path path = find(fs, "methods.csv");
		if(path != null) mergeFromMethodsCsv(path, mem);
	}
	
	public void mergeFromZip(FileSystem fs, StringInterner mem, Consumer<String> log) throws IOException {
		Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				String filename = path.getFileName().toString();
				switch(filename) {
					case "joined.srg":
					case "joined.csrg":
						log.accept("\\-> Reading " + path);
						mergeFromJoinedSrg(path, mem);
						break;
					case "packages.csv":
						log.accept("\\-> Reading " + path);
						mergeFromPackagesCsv(path, mem);
						break;
					case "client.srg":
						log.accept("\\-> Reading " + path);
						mergeFromClientSrg(path, mem);
						break;
					case "server.srg":
						log.accept("\\-> Reading " + path);
						mergeFromServerSrg(path, mem);
						break;
					case "fields.csv":
						log.accept("\\-> Reading " + path);
						mergeFromFieldsCsv(path, mem);
						break;
					case "methods.csv":
						log.accept("\\-> Reading " + path);
						mergeFromMethodsCsv(path, mem);
						break;
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}
}

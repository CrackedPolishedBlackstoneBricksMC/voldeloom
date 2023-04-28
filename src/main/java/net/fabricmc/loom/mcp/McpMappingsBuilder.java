package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

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
		if(path != null) joined.mergeWith(new Srg().read(path, mem));
	}
	
	public void mergeFromPackagesCsv(Path path, StringInterner mem) throws IOException {
		if(path != null) packages.mergeWith(new Packages().read(path, mem));
	}
	
	public void mergeFromClientSrg(Path path, StringInterner mem) throws IOException {
		if(path != null) client.mergeWith(new Srg().read(path, mem));
	}
	
	public void mergeFromServerSrg(Path path, StringInterner mem) throws IOException {
		if(path != null) server.mergeWith(new Srg().read(path, mem));
	}
	
	public void mergeFromFieldsCsv(Path path, StringInterner mem) throws IOException {
		if(path != null) fields.mergeWith(new Members().read(path, mem));
	}
	
	public void mergeFromMethodsCsv(Path path, StringInterner mem) throws IOException {
		if(path != null) methods.mergeWith(new Members().read(path, mem));
	}
	
	//Overloads that guess the path from a MappingScanner:
	
	public void mergeFromJoinedSrg(MappingScanner scan, StringInterner mem) throws IOException {
		Path path = scan.get(MappingScanner.JOINED_SRG);
		if(path == null) path = scan.get(MappingScanner.JOINED_CSRG); //weird spot to put this?
		mergeFromJoinedSrg(path, mem);
	}
	
	public void mergeFromPackagesCsv(MappingScanner scan, StringInterner mem) throws IOException {
		mergeFromPackagesCsv(scan.get(MappingScanner.PACKAGES_CSV), mem);
	}
	
	public void mergeFromClientSrg(MappingScanner scan, StringInterner mem) throws IOException {
		mergeFromClientSrg(scan.get(MappingScanner.CLIENT_SRG), mem);
	}
	
	public void mergeFromServerSrg(MappingScanner scan, StringInterner mem) throws IOException {
		mergeFromServerSrg(scan.get(MappingScanner.SERVER_SRG), mem);
	}
	
	public void mergeFromFieldsCsv(MappingScanner scan, StringInterner mem) throws IOException {
		mergeFromFieldsCsv(scan.get(MappingScanner.FIELDS_CSV), mem);
	}
	
	public void mergeFromMethodsCsv(MappingScanner scan, StringInterner mem) throws IOException {
		mergeFromMethodsCsv(scan.get(MappingScanner.METHODS_CSV), mem);
	}
	
	//And a big giant method that does all the MappingScannering for you:
	public void importEverythingFromZip(FileSystem fs, StringInterner mem) throws IOException {
		MappingScanner scan = new MappingScanner(fs);
		mergeFromJoinedSrg(scan, mem);
		mergeFromPackagesCsv(scan, mem);
		mergeFromClientSrg(scan, mem);
		mergeFromServerSrg(scan, mem);
		mergeFromFieldsCsv(scan, mem);
		mergeFromMethodsCsv(scan, mem);
	}
}

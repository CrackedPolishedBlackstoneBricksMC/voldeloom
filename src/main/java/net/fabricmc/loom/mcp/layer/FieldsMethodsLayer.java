package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.mcp.MappingScanner;
import net.fabricmc.loom.mcp.McpMappingsBuilder;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.security.MessageDigest;

public class FieldsMethodsLayer implements Layer {
	public FieldsMethodsLayer(Path zipPath) {
		this.zipPath = zipPath;
	}
	
	private final Path zipPath;
	
	@Override
	public void visit(Logger log, McpMappingsBuilder mappings, StringInterner mem) throws Exception {
		log.info("\t-- (FieldsMethodsLayer) Importing field and method mappings from {} --", zipPath);
		
		try(FileSystem fs = ZipUtil.openFs(zipPath)) {
			MappingScanner scan = new MappingScanner(fs);
			
			mappings.mergeFromFieldsCsv(scan, mem);
			mappings.mergeFromMethodsCsv(scan, mem);
		}
	}
	
	@Override
	public void updateHasher(MessageDigest hasher) throws Exception {
		hasher.update(zipPath.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
		hasher.update((byte) 0);
		Checksum.feedFileToHasher(zipPath, hasher);
	}
}

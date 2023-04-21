package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.mcp.McpMappings;
import org.gradle.api.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class FieldsMethodsLayer implements Layer {
	public FieldsMethodsLayer(Path zipPath) {
		this.zipPath = zipPath;
	}
	
	private final Path zipPath;
	
	@Override
	public void visit(Logger log, McpMappings mappings) throws Exception {
		log.info("\t-- (FieldsMethodsLayer) Importing field and method mappings from {} --", zipPath);
		
		//TODO: call a more specific method (this class should not be the same as BaseZipLayer)
		mappings.importFromZip(log::info, zipPath);
	}
	
	@Override
	public void updateHasher(MessageDigest hasher) throws Exception {
		hasher.update(zipPath.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
		hasher.update((byte) 0);
		hasher.update(Files.readAllBytes(zipPath)); //todo, potentially giant allocation lol
	}
}

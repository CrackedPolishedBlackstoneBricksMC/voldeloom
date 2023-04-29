package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.mcp.McpMappingsBuilder;
import net.fabricmc.loom.util.StringInterner;
import org.gradle.api.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Consumer;

public class ModifyLayer implements Layer {
	public ModifyLayer(String cacheKey, Consumer<McpMappingsBuilder> action) {
		this.cacheKey = cacheKey;
		this.action = action;
	}
	
	private final String cacheKey;
	private final Consumer<McpMappingsBuilder> action;
	
	@Override
	public void visit(Logger log, McpMappingsBuilder mappings, StringInterner mem) throws Exception {
		action.accept(mappings);
	}
	
	@Override
	public void updateHasher(MessageDigest hasher) throws Exception {
		hasher.update(cacheKey.getBytes(StandardCharsets.UTF_8));
	}
}

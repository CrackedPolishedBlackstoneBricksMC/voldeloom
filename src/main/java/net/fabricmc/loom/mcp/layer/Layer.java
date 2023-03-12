package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.mcp.McpMappings;
import org.gradle.api.logging.Logger;

import java.security.MessageDigest;

public interface Layer {
	void visit(Logger log, McpMappings mappings) throws Exception;
	void updateHasher(MessageDigest hasher) throws Exception;
	
	//just sticking these here because the java messagedigest interface kinda sucks
	default void updateHasher(MessageDigest md, int thing) {
		md.update((byte) ((thing & 0xFF000000) >> 24));
		md.update((byte) ((thing & 0x00FF0000) >> 16));
		md.update((byte) ((thing & 0x0000FF00) >> 8));
		md.update((byte) ((thing & 0x000000FF)));
	}
}

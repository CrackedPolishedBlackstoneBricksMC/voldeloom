package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.mcp.McpMappingsBuilder;
import net.fabricmc.loom.util.StringInterner;
import org.gradle.api.logging.Logger;

import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class RemoveClassesLayer implements Layer {
	public RemoveClassesLayer(Collection<String> reh) {
		classMappingsToRemove = new HashSet<>(reh);
	}
	
	private final Set<String> classMappingsToRemove;
	
	@Override
	public void visit(Logger log, McpMappingsBuilder mappings, StringInterner mem) throws Exception {
		for(String unmap : classMappingsToRemove) {
			log.info("\t-- (RemoveClassesLayer) Removing class {} from mappings --", unmap);
			mappings.joined.removeClassMapping(unmap);
			mappings.client.removeClassMapping(unmap);
			mappings.server.removeClassMapping(unmap);
		}
	}
	
	@Override
	public void updateHasher(MessageDigest hasher) throws Exception {
		updateHasher(hasher, classMappingsToRemove.hashCode()); //good enough
	}
}

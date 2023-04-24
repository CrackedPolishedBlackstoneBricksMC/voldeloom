package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.mcp.McpMappings;
import org.gradle.api.logging.Logger;

import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ClassUnmappingLayer implements Layer {
	public ClassUnmappingLayer(Collection<String> reh) {
		classMappingsToRemove = new HashSet<>(reh);
	}
	
	private final Set<String> classMappingsToRemove;
	
	@Override
	public void visit(Logger log, McpMappings mappings) throws Exception {
		for(String unmap : classMappingsToRemove) {
			log.info("\t-- (ClassUnmappingLayer) Removing class {} from mappings --", unmap);
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

package io.github.nuclearfarts.yarnforgedev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Map;
import java.util.function.BiFunction;

import net.fabricmc.mapping.tree.TinyMappingFactory;
import cpw.mods.modlauncher.api.INameMappingService;

public class Srg2YarnMappingService implements INameMappingService {

	private final TinyMappingHelper srg2yarn;
	
	public Srg2YarnMappingService() {
		try {
			System.out.println("SRG to Yarn mapping service using mappings " + System.getProperty("yarnforgedev.srgyarn"));
			srg2yarn = new TinyMappingHelper(TinyMappingFactory.loadWithDetection(Files.newBufferedReader(Paths.get(System.getProperty("yarnforgedev.srgyarn")))), "srg", "named");
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Exception loading srg -> yarn mapping service", e);
		}
	}

	@Override
	public String mappingName() {
		return "srgtoyarn";
	}

	@Override
	public String mappingVersion() {
		return "1234";
	}

	@Override
	public Map.Entry<String, String> understanding() {
		return new AbstractMap.SimpleImmutableEntry<String, String>("srg", "mcp");
	}

	@Override
	public BiFunction<Domain, String, String> namingFunction() {
		return this::map;
	}
	
	private String map(Domain domain, String name) {
		try {
		String result;
		switch(domain) {
		case CLASS:
			result = srg2yarn.mapClass(name);
			//System.out.printf("Mapping class from SRG %s to %s\n", name, result);
			return result;
		case FIELD:
			if(!name.startsWith("field_")) {
				return name;
			}
			result = srg2yarn.mapField(name);
			//System.out.printf("Mapping field from SRG %s to %s\n", name, result);
			return result;
		case METHOD:
			if(!name.startsWith("func_")) {
				return name;
			}
			result = srg2yarn.mapMethod(name);
			//System.out.printf("Mapping method from SRG %s to %s\n", name, result);
			return result;
		default:
			throw new IllegalArgumentException("null or unknown domain " + domain);
		}
		} catch(RuntimeException e) {
			System.err.println("Exception remapping " + name);
			throw e;
		}
	}
}

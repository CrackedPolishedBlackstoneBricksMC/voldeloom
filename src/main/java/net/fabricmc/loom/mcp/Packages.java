package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Forge's packages.csv.
 */
public class Packages {
	private final Map<String, String> packages = new HashMap<>();
	
	public Packages read(Path path, StringInterner mem) throws IOException {
		List<String> lines = Files.readAllLines(path);
		for(int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if(line.isEmpty()) continue;
			if((i == 0 && "class,package".equals(line))) continue; //the csv header
			int lineNo = i + 1;
			
			//Example packages.csv line:
			// BlockAnvil,net/minecraft/block
			
			String[] split = line.split(",");
			if(split.length != 2) {
				System.err.println("line " + lineNo + " has weird number of elements: " + line);
				continue;
			}
			
			packages.put(mem.intern(split[0]), mem.intern(split[1]));
		}
		
		return this;
	}
	
	public void mergeWith(Packages other) {
		packages.putAll(other.packages);
	}
	
	public boolean isEmpty() {
		return packages.isEmpty();
	}
	
	/**
	 * Applies the packaging transformation to a class name, in internal format.
	 */
	public String repackage(String srgClass) {
		//remove the package prefix from the class
		String srgClassNameOnly = srgClass;
		int lastSlash = srgClass.lastIndexOf('/');
		if(lastSlash != -1) srgClassNameOnly = srgClass.substring(lastSlash + 1);
		
		//the values of the map are the *new* package that the class should go in.
		//if we don't get a hit, leave the class unmoved; else, glue the new package onto the old class name
		String lookup = packages.get(srgClassNameOnly);
		if(lookup == null) return srgClass;
		else return lookup + "/" + srgClassNameOnly;
	}
	
	/**
	 * Calls {@code repackage} on all internal-format class names it can find in the descriptor.
	 */
	public String repackageDescriptor(String descriptor) {
		return DescriptorMapper.map(descriptor, this::repackage);
	}
}

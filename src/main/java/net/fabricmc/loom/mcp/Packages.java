package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	
	// L([^.;\[]*);
	//Matches a capital L, followed by a Java class internal name, followed by a semicolon (how class types are encoded in descriptors).
	//Capturing group 1 is set to just the stuff inside the L; brackets.
	//See JVMS section 4.2.1, 4.2.2 for a definition of "legal class name". This regex deviates from the standard in two ways that I know of:
	//* it will match empty classes (L;).
	//* it will match classes with empty path segments (Lagency/highlysuspect//nothing;).
	//This is fixable but, we're not being fed pathological class names, so I'll take the simpler regex.
	public static final Pattern CLASS_NAMES_FROM_DESCRIPTOR_SOUP = Pattern.compile("L([^.;\\[]*);");
	
	/**
	 * Calls {@code repackage} on all internal-format class names it can find in the descriptor.
	 */
	public String repackageDescriptor(String descriptor) {
		if(descriptor.indexOf('L') == -1) return descriptor; //Surely no class names in this descriptor (fast path)
		
		StringBuffer out = new StringBuffer(descriptor.length());
		Matcher m = CLASS_NAMES_FROM_DESCRIPTOR_SOUP.matcher(descriptor);
		while(m.find()) m.appendReplacement(out, "L" + repackage(m.group(1)) + ";");
		m.appendTail(out);
		return out.toString();
	}
}

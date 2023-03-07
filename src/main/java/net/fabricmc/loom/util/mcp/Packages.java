package net.fabricmc.loom.util.mcp;

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
			
			//I want to store the full name as the value of the map (so, net/minecraft/block/BlockAnvil)
			packages.put(mem.intern(split[0]), mem.intern(split[1] + "/" + split[0]));
		}
		
		return this;
	}
	
	public String repackage(String srgClass) {
		//remove the package prefix from the class
		String srgClassNameOnly = srgClass;
		int lastSlash = srgClass.lastIndexOf('/');
		if(lastSlash != -1) srgClassNameOnly = srgClass.substring(lastSlash + 1);
		
		//lookup
		return packages.getOrDefault(srgClassNameOnly, srgClass);
	}
	
	// L([\/\w]*);
	//Matches a capital L, followed by word-characters-or-slashes, followed by a semicolon (which is how class types are encoded in descriptors).
	//Capturing group 1 is set to just the stuff inside the L; brackets for convenience.
	@SuppressWarnings("RegExpRedundantEscape") //regexr.com (PCRE) seems to complain about unescaped *forward*-slashes in patterns
	private static final Pattern classNamesFromDescriptorSoup = Pattern.compile("L([\\/\\w]*);");
	public String repackageDescriptor(String descriptor) {
		if(descriptor.indexOf('L') == -1) return descriptor; //No class names in this descriptor (fast path)
		
		//N.B. if I wasn't targeting Java 8, Matcher.replaceAll would make quick work of this task
		String work = descriptor;
		while(true) {
			Matcher m = classNamesFromDescriptorSoup.matcher(work);
			
			if(m.find()) {
				String beforeMatch = work.substring(0, m.start());
				String match = m.group(1);
				String afterMatch = work.substring(m.end());
				
				//don't replace it with an L yet - the matcher will find it again next time round and infinitely loop
				work = beforeMatch + "\ud83d\udc09" + repackage(match) + ";" + afterMatch;
			} else break;
		}
		
		return work.replace("\ud83d\udc09", "L");
	}
}

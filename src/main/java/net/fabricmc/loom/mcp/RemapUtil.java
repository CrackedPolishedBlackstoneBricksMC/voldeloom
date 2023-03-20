package net.fabricmc.loom.mcp;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemapUtil {
	// L([\/\w]*);
	//Matches a capital L, followed by word-characters-or-slashes, followed by a semicolon (which is how class types are encoded in descriptors).
	//Capturing group 1 is set to just the stuff inside the L; brackets for convenience.
	@SuppressWarnings("RegExpRedundantEscape") //regexr.com (PCRE) seems to complain about unescaped *forward*-slashes in patterns
	public static final Pattern CLASS_NAMES_FROM_DESCRIPTOR_SOUP = Pattern.compile("L([\\/\\w]*);");
	
	public static String remapDescriptor(String descriptor, Function<String, String> mapper) {
		if(descriptor.indexOf('L') == -1) return descriptor; //No class names in this descriptor (fast path)
		
		StringBuffer out = new StringBuffer(descriptor.length());
		Matcher m = CLASS_NAMES_FROM_DESCRIPTOR_SOUP.matcher(descriptor);
		while(m.find()) {
			String match = m.group(1);
			String remappedMatch = mapper.apply(match);
			
			m.appendReplacement(out, "L" + (remappedMatch == null ? match : remappedMatch) + ";");
		}
		m.appendTail(out);
		return out.toString();
	}
}

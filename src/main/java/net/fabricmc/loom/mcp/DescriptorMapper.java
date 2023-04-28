package net.fabricmc.loom.mcp;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DescriptorMapper {
	// L([^;]*);
	//Matches nonnested brackets delimited on the left by a capital L and on the right by a semicolon. This is how class types are encoded in descriptors.
	//Capturing group 1 is set to the stuff inside the brackets.
	//See JVMS section 4.2.1, 4.2.2 for a definition of "legal class name". Basically any character is permissible except for ";", which terminates
	//the class name, and a few other characters (.[) that I don't expect to see because we're not being fed pathological class names.
	public static final Pattern CLASS_NAMES_FROM_DESCRIPTOR_SOUP = Pattern.compile("L([^;]*);");
	
	/**
	 * Feeds all class-names mentioned in the descriptor through the classMapper function.
	 * 
	 * @param classMapper is a function from class names to class names (in internal-name style).
	 *                    If there's no mapping for that class, it should return the input instead of `null`.
	 */
	public static String map(String descriptor, Function<String, String> classMapper) {
		if(descriptor.indexOf('L') == -1) return descriptor; //Surely no class names in this descriptor (fast path)
		
		StringBuffer out = new StringBuffer(descriptor.length());
		Matcher m = CLASS_NAMES_FROM_DESCRIPTOR_SOUP.matcher(descriptor);
		while(m.find()) {
			//Did you know that appendReplacement has magic handling for *replacements* containing $ characters ?????
			//Because Java needed more footguns, and more methods with string arguments that treat them nonliterally, imo
			//This almost feels like some sort of format-string security issue lol, view capture groups you're not supposed to see...
			m.appendReplacement(out, "L" + classMapper.apply(m.group(1)).replace("$", "\\$") + ";");
		}
		m.appendTail(out);
		return out.toString();
	}
}

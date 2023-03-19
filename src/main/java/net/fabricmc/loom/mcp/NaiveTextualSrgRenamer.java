package net.fabricmc.loom.mcp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaiveTextualSrgRenamer {
	public NaiveTextualSrgRenamer(Members fields, Members methods) {
		this.fields = fields;
		this.methods = methods;
	}
	
	public NaiveTextualSrgRenamer(McpMappings mappings) {
		this(mappings.fields, mappings.methods);
	}
	
	private final Members fields, methods;
	
	private static final Pattern FUNC = Pattern.compile("func_[0-9]+_[a-zA-Z_]+");
	private static final Pattern FIELD = Pattern.compile("field_[0-9]+_[a-zA-Z_]+");
	//private static final Pattern PARAM = Pattern.compile("p_[\\w]+_\\d+_]");
	//reference: mcp726a.zip, commands.py, line 1217
	
	public String rename(String input) {
		//again, i wish i had the fancy replaceAll version in java 17, but i need java 8
		//Cannot use StringBuilder until java 9 too, need the dorky `synchronized` StringBuffer
		StringBuffer out = new StringBuffer(input.length());
		
		//rename func_12345_a -> method names
		Matcher funcMatcher = FUNC.matcher(input);
		while(funcMatcher.find()) {
			Members.Entry entry = methods.remapSrg(funcMatcher.group());
			funcMatcher.appendReplacement(out, entry == null ? funcMatcher.group() : entry.remappedName);
		}
		funcMatcher.appendTail(out);
		
		//rebuffer
		input = out.toString();
		out = new StringBuffer(input.length());
		
		//rename field_12346_a -> field names
		Matcher fieldMatcher = FIELD.matcher(input);
		while(fieldMatcher.find()) {
			Members.Entry entry = fields.remapSrg(fieldMatcher.group());
			fieldMatcher.appendReplacement(out, entry == null ? fieldMatcher.group() : entry.remappedName);
		}
		fieldMatcher.appendTail(out);
		
		return out.toString();
	}
}

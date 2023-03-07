package net.fabricmc.loom.util.mcp;

import net.fabricmc.loom.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates some information about MCP-format mappings (srgs, fields.csv, methods.csv, etc) and creates output in tinyv2 format.
 */
public class McpTinyv2Writer {
	private Srg srg;
	private Members fields;
	private Members methods;
	
	private boolean srgsAsFallback = false;
	private JarScanData jarScanData;
	
	public McpTinyv2Writer srg(Srg srg) {
		this.srg = srg;
		return this;
	}
	
	public McpTinyv2Writer fields(Members fields) {
		this.fields = fields;
		return this;
	}
	
	public McpTinyv2Writer methods(Members methods) {
		this.methods = methods;
		return this;
	}
	
	/**
	 * If true, when an MCP name doesn't exist for a given field or method SRG, the SRG name will be used as-is.<br>
	 * If false, the proguarded field name will be used instead.<br>
	 * This helps support versions 1.4 and below, where runtime-remapping fields to SRG names did not happen yet.
	 */
	public McpTinyv2Writer srgsAsFallback(boolean srgsAsFallback) {
		this.srgsAsFallback = srgsAsFallback;
		return this;
	}
	
	/**
	 * SRGs don't include field type information, but tiny-remapper sometimes likes to have it, and it is a required part of the format.<br>
	 * If nonnull, this repository of scanned data from the unremapped jar will be used to guess field type information.<br>
	 * If null, every field will be typed as java.lang.Void.
	 */
	public McpTinyv2Writer jarScanData(JarScanData jarScanData) {
		this.jarScanData = jarScanData;
		return this;
	}
	
	public List<String> write() {
		List<String> lines = new ArrayList<>();
		
		lines.add("tiny\t2\t0\t" + (Constants.PROGUARDED_NAMING_SCHEME + "\t" + Constants.INTERMEDIATE_NAMING_SCHEME + "\t" + Constants.MAPPED_NAMING_SCHEME));
		
		//for each class:
		srg.classMappings.forEach((classProguard, classSrg) -> {
			//write class name
			lines.add("c\t" + classProguard + "\t" + classSrg + "\t" + classSrg); //srg class names == named class names
			
			//for each field in the class:
			Map<String, String> fieldMappings = srg.fieldMappingsByOwningClass.get(classProguard);
			if(fieldMappings != null) fieldMappings.forEach((fieldProguard, fieldMapped) -> {
				//find the remapped name of the field
				Members.Entry fieldNamed = fields.remapSrg(fieldMapped);
				String fieldName;
				if(fieldNamed != null) fieldName = fieldNamed.remappedName;
				else fieldName = chooseName(fieldProguard, fieldMapped);
				
				//write mapping.
				//There used to be a bit of code that tried to detect a proper field type and write that in to the file, instead of hardcoding
				//this obviously incorrect Ljava/lang/Void;. I didn't know that you could tell tiny-remapper to ignore field descriptors, though, so i enabled that.
				//MCP doesn't include field type information in the files; the only way we can get it is using JarScanData, which is brittle.
				//In practice this is fine because Mojang was nice and didn't name-clash fields (which is valid bytecode if the types are different, but not valid java).
				//We must insert *some* field type into the file, though, because the tinyv2 file format demands it.
				lines.add("\tf\tLjava/lang/Void;\t" + fieldProguard + "\t" + fieldMapped + "\t" + fieldName);
				
				//write javadoc comment
				if(fieldNamed != null && fieldNamed.comment != null) {
					lines.add("\t\tc\t" + sanitizeComment(fieldNamed.comment));
				}
			});
			
			//for each method in the class:
			Map<Srg.MethodEntry, Srg.MethodEntry> methodMappings = srg.methodMappingsByOwningClass.get(classProguard);
			if(methodMappings != null) methodMappings.forEach((methodProguard, methodSrg) -> {
				//find the remapped name of the method
				Members.Entry methodNamed = methods.remapSrg(methodSrg.name);
				String methodName;
				if(methodNamed != null) methodName = methodNamed.remappedName;
				else methodName = chooseName(methodProguard.name, methodSrg.name);
				
				//write mapping
				lines.add("\tm\t" + methodProguard.descriptor + "\t" + methodProguard.name + "\t" + methodSrg.name + "\t" + methodName);
				
				//write javadoc comment
				if(methodNamed != null && methodNamed.comment != null) {
					lines.add("\t\tc\t" + sanitizeComment(methodNamed.comment));
				}
			});
			
			//for each inner class:
			Set<String> childClassNames = jarScanData.innerClasses.get(classProguard);
			int weirdInventedMapping = 1;
			if(childClassNames != null) for(String childClassName : childClassNames) {
				if(srg.classMappings.containsKey(childClassName)) continue;
				
				//If we reach this point, there's a class that jarScanData says is a real class, but we don't have any mappings for it.
				//This happens a lot with switchmap classes added by a Forge patch.
				//We're not on a source-based toolchain, though, so we need to invent a name for this class out of thin air.
				
				//For example, on 1.4.7 this happens with the class `amq$1`.
				//`amq` is Block, and `amq$1` is the switchmap for a patch to canSustainPlant that Forge does.
				//I want to put this class at "Block$1".
				
				//find the class suffix (the $ and beyond)
				String inventedMapping;
				int dollarIndex = childClassName.indexOf("$");
				if(dollarIndex == -1) {
					inventedMapping = classSrg + "$" + weirdInventedMapping++ + "_voldeloom_invented";
					System.err.println("The class '" + childClassName + "' was found to be an inner class of " + classProguard + " (" + classSrg + ") through jar scanning,");
					System.err.println("but there's no mapping defined for it, and it doesn't contain a dollar character.");
					System.err.println("Inventing the name " + inventedMapping + " for it.");
				} else {
					//remap "amq$1" the same as how "amq" is mapped, but with a "$1"
					inventedMapping = classSrg + childClassName.substring(dollarIndex);
				}
				
				lines.add("c\t" + childClassName + "\t" + inventedMapping + "\t" + inventedMapping);
				lines.add("\tc\t" + sanitizeComment("Voldeloom-invented class mapping ('" + childClassName + "' -> '" + inventedMapping + "')"));
			}
		});
		
		return lines;
	}
	
	//TODO maybe move to Members parser
	private String sanitizeComment(String comment) {
		if(comment.startsWith("\"") && comment.endsWith("\"")) {
			//comment is a double-quoted string inside the csv (might contain commas etc)
			//it also escapes double-quotes with more quotes
			comment = comment
				.substring(1, comment.length() - 1)
				.replace("\"\"", "");
		}
		
		return comment
			.replace("[\\r\\n]", "") //it should be impossible for newlines to appear in the comment? but it will TURBO break the tiny parser if it happens
			.replace("\\", "\\\\"); //Should hopefully correctly escape according to TinyV2Factory.unescape
	}
	
	//TODO: Is this correct
	// SRG files contain "srg names", but also sometimes "correct" non-srg names names for things like the Argo library and enum variants
	private String chooseName(String proguardName, String srgName) {
		if(srgsAsFallback) return srgName;
		else return (srgName.startsWith("field_") || srgName.startsWith("func_")) ? proguardName : srgName;
	}
}

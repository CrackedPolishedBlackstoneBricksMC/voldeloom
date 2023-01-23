package net.fabricmc.loom.util.mcp;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aggregates some information about MCP-format mappings (srgs, fields.csv, methods.csv, etc) and creates output in tinyv2 format.
 */
public class McpTinyv2Writer {
	private Srg srg;
	private Members fields;
	private Members methods;
	private @Nullable Packages packages;
	
	private boolean threeColumn = false;
	private @Nullable JarScanData jarScanData;
	
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
	
	public McpTinyv2Writer packages(@Nullable Packages packages) {
		this.packages = packages;
		return this;
	}
	
	/**
	 * If true, proguard-to-srg-to-named mappings are written. If false, only proguard -> named mappings are written.<br>
	 * This helps support Minecraft 1.4 and below, which didn't ever remap the game to SRGs.<br>
	 * Unmapped fields exist in the live game environment as their proguarded names
	 */
	public McpTinyv2Writer threeColumn(boolean threeColumn) {
		this.threeColumn = threeColumn;
		return this;
	}
	
	/**
	 * SRGs don't include field type information, but tiny-remapper sometimes likes to have it, and it is a required part of the format.<br>
	 * If nonnull, this repository of scanned data from the unremapped jar will be used to guess field type information.<br>
	 * If null, every field will be typed as java.lang.Void.
	 */
	public McpTinyv2Writer jarScanData(@Nullable JarScanData jarScanData) {
		this.jarScanData = jarScanData;
		return this;
	}
	
	public List<String> write() {
		List<String> lines = new ArrayList<>();
		
		lines.add("tiny\t2\t0\t" + threecol(Constants.PROGUARDED_NAMING_SCHEME, Constants.INTERMEDIATE_NAMING_SCHEME, Constants.MAPPED_NAMING_SCHEME));
		
		//for each class:
		srg.classMappings.forEach((classProguard, classSrg) -> {
			//apply packaging transformation
			String classSrgRepackage = packages != null ? packages.repackage(classSrg) : classSrg;
			
			//write class name
			lines.add("c\t" + threecol(classProguard, classSrgRepackage, classSrgRepackage)); //srg class names == named class names
			
			//for each field in the class:
			Map<Srg.FieldEntry, Srg.FieldEntry> fieldMappings = srg.fieldMappingsByOwningClass.get(classProguard);
			if(fieldMappings != null) fieldMappings.forEach((fieldProguard, fieldSrg) -> {
				//try to guess the field type (because srgs don't natively include them!)
				String fieldType = "Ljava/lang/Void;";
				if(jarScanData != null) {
					String knownFieldType = jarScanData.fieldDescs.get(fieldProguard.owningClass + "/" + fieldProguard.name);
					if(knownFieldType != null) fieldType = knownFieldType;
				}
				
				//find the remapped name of the field
				Members.Entry fieldNamed = fields.remapSrg(fieldSrg.name);
				String fieldName = fieldNamed == null ? fieldSrg.name : fieldNamed.remappedName;
				
				//write mapping
				lines.add("\tf\t" + fieldType + "\t" + threecol(fieldProguard.name, fieldSrg.name, fieldName));
				
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
				String methodName = methodNamed == null ? methodSrg.name : methodNamed.remappedName;
				
				//write mapping
				lines.add("\tm\t" + methodProguard.descriptor + "\t" + threecol(methodProguard.name, methodSrg.name, methodName));
				
				//write javadoc comment
				if(methodNamed != null && methodNamed.comment != null) {
					lines.add("\t\tc\t" + sanitizeComment(methodNamed.comment));
				}
			});
		});
		
		return lines;
	}
	
	private String threecol(String proguard, String intermediate, String mapped) {
		//do NOT stringify `null`s into the file, ever
		Preconditions.checkNotNull(proguard, "proguard name");
		Preconditions.checkNotNull(intermediate, "intermediate name");
		Preconditions.checkNotNull(mapped, "mapped name");
		
		if(threeColumn) return proguard + "\t" + intermediate + "\t" + mapped;
		else return proguard + "\t" + mapped;
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
}

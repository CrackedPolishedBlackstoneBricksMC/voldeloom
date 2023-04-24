package net.fabricmc.loom.task.fernflower;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.util.ZipUtil;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class McpJavadocProvider implements IFabricJavadocProvider {
	public McpJavadocProvider(Path mcpZip) throws IOException {
		McpMappings mappings;
		try(FileSystem fs = ZipUtil.openFs(mcpZip)) {
			mappings = new McpMappings().importFromZip(System.out::println, fs);
		}
		
		//NB: This relies on MCP having unique names for all fields/methods with javadoc
		//I don't thiiink this is true (it has unique SRGs, sometimes methods are named oddly to make them unique,
		//but i don't know if they're all unique). Hopefully when i get a find-and-replace-based remapper in place
		//I can maybe run fernflower on the srg and use find-and-replace to remap to mcp, so fernflower will be able
		//to see the (unique) srg names. Right now this seems to work decently enough...?
		
		mappings.fields.members.values().forEach(entry -> {
			if(entry.comment != null && !entry.comment.trim().isEmpty()) fieldComments.put(entry.remappedName, entry.comment);
		});
		
		mappings.methods.members.values().forEach(entry -> {
			if(entry.comment != null && !entry.comment.trim().isEmpty()) methodComments.put(entry.remappedName, entry.comment);
		});
	}
	
	private final Map<String, String> fieldComments = new HashMap<>();
	private final Map<String, String> methodComments = new HashMap<>();
	
	@Override
	public String getClassDoc(StructClass structClass) {
		//MCP doesn't include class comments
		return null;
	}
	
	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		return fieldComments.get(structField.getName());
	}
	
	@Override
	public String getMethodDoc(StructClass structClass, StructMethod structMethod) {
		return methodComments.get(structMethod.getName());
	}
}

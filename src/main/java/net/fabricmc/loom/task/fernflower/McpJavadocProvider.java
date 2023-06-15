package net.fabricmc.loom.task.fernflower;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.mcp.MappingScanner;
import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.mcp.McpMappingsBuilder;
import net.fabricmc.loom.util.StringInterner;
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
		//Read just the fields.csv and methods.csv files:
		// * They're the only ones with comments.
		// * We don't care about the field/method's owning class because field/method names are always unique.
		McpMappingsBuilder builder = new McpMappingsBuilder();
		StringInterner mem = new StringInterner();
		try(FileSystem fs = ZipUtil.openFs(mcpZip)) {
			MappingScanner scan = new MappingScanner(fs);
			builder.mergeFromFieldsCsv(scan, mem);
			builder.mergeFromMethodsCsv(scan, mem);
		}
		McpMappings mappings = builder.build(true);
		
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

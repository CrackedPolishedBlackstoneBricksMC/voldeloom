package io.github.nuclearfarts.yarnforgedev;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyTree;

public class TinyMappingHelper {
	private Map<String, String> classes = new HashMap<>();
	private Map<String, String> fields = new HashMap<>();
	private Map<String, String> methods = new HashMap<>();
	
	public TinyMappingHelper(TinyTree tree, String srcNamespace, String dstNamespace) {
		for(ClassDef clazz : tree.getClasses()) {
			String srcClass = clazz.getName(srcNamespace);
			classes.put(srcClass.replace('/', '.'), clazz.getName(dstNamespace).replace('/', '.'));
			for(FieldDef field : clazz.getFields()) {
				fields.put(field.getName(srcNamespace), field.getName(dstNamespace)); 
			}
			for(MethodDef method : clazz.getMethods()) {
				methods.put(method.getName(srcNamespace), method.getName(dstNamespace));
			}
		}
	}

	public String mapClass(String clazz) {
		return classes.getOrDefault(clazz, clazz);
	}
	
	public String mapMethod(String method) {
		return methods.getOrDefault(method, method);
	}
	
	public String mapField(String field) {
		return fields.getOrDefault(field, field);
	}
}

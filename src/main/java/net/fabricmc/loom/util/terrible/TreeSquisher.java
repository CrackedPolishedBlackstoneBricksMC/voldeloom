package net.fabricmc.loom.util.terrible;

import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.Descriptored;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.LocalVariableDef;
import net.fabricmc.mapping.tree.Mapped;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyTree;

import java.lang.reflect.Field;
import java.util.Map;

public class TreeSquisher {
	private Field mappedimpl_names;
	private Field descriptoredimpl_mapper;
	private Field descriptoredimpl_signature;
	private Field descriptor_mapper_map;
	
	public void squish(TinyTree tree) {
		StringInterner mem = new StringInterner();
		
		try {
			mappedimpl_names = getPrivateFieldHierarchically(Class.forName("net.fabricmc.mapping.tree.MappedImpl"), "names");
			mappedimpl_names.setAccessible(true);
			
			descriptoredimpl_mapper = getPrivateFieldHierarchically(Class.forName("net.fabricmc.mapping.tree.DescriptoredImpl"), "mapper");
			descriptoredimpl_mapper.setAccessible(true);
			
			descriptoredimpl_signature = getPrivateFieldHierarchically(Class.forName("net.fabricmc.mapping.tree.DescriptoredImpl"), "signature");
			descriptoredimpl_signature.setAccessible(true);
			
			descriptor_mapper_map = getPrivateFieldHierarchically(Class.forName("net.fabricmc.mapping.tree.DescriptorMapper"), "map");
			descriptor_mapper_map.setAccessible(true);
			
			for(ClassDef classdef : tree.getClasses()) {
				squishClassDef(classdef, mem);
			}
			
			Field tree_map = getPrivateFieldHierarchically(Class.forName("net.fabricmc.mapping.tree.TinyMappingFactory$Tree"), "map");
			tree_map.setAccessible(true);
			@SuppressWarnings("unchecked") Map<String, ?> map = (Map<String, ?>) tree_map.get(tree);
			mem.internMapKeys(map);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		
		mem.close();
	}
	
	private void squishClassDef(ClassDef classdef, StringInterner mem) throws ReflectiveOperationException {
		squishMappedImpl(classdef, mem);
		
		for(FieldDef fielddef : classdef.getFields()) {
			squishMappedImpl(fielddef, mem);
			squishDescriptoredImpl(fielddef, mem);
		}
		
		for(MethodDef methoddef : classdef.getMethods()) {
			squishMappedImpl(methoddef, mem);
			squishDescriptoredImpl(methoddef, mem);
			
			for(ParameterDef parameter : methoddef.getParameters()) {
				squishMappedImpl(parameter, mem);
			}
			
			for(LocalVariableDef lvt : methoddef.getLocalVariables()) {
				squishMappedImpl(lvt, mem);
			}
		}
	}
	
	private void squishMappedImpl(Mapped mapped, StringInterner mem) throws ReflectiveOperationException {
		String[] namesArray = (String[]) mappedimpl_names.get(mapped);
		mem.internArray(namesArray);
	}
	
	private void squishDescriptoredImpl(Descriptored descd, StringInterner mem) throws ReflectiveOperationException {
		Object descriptoredimpl = descriptoredimpl_mapper.get(descd);
		
		@SuppressWarnings("unchecked") Map<String, ?> map = (Map<String, ?>) descriptor_mapper_map.get(descriptoredimpl);
		mem.internMapKeys(map);
		
		descriptoredimpl_signature.set(descd, mem.intern((String) descriptoredimpl_signature.get(descd)));
	}
	
	private Field getPrivateFieldHierarchically(Class<?> classs, String name) throws NoSuchFieldException {
		Class<?> classss = classs;
		while(true) {
			try {
				return classss.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				classss = classss.getSuperclass();
				if(Object.class.equals(classss)) throw new NoSuchFieldException("couldn't find field " + name + " in " + classs.getName() + " or any of its superclasses");
			}
		}
	}
}

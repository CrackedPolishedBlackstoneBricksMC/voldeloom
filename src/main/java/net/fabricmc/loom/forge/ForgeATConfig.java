package net.fabricmc.loom.forge;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ForgeATConfig {
	//key = classname
	private final Map<String, EnumAccessTransformation> classTransformers = new HashMap<>();
	
	//key = classname
	private final Map<String, EnumAccessTransformation> wildcardFieldTransformers = new HashMap<>();
	//key = classname + "." + fieldname
	private final Map<String, EnumAccessTransformation> fieldTransformers = new HashMap<>();
	
	//key = classname
	private final Map<String, EnumAccessTransformation> wildcardMethodTransformers = new HashMap<>();
	//key = classname + "." + methodname + methodddescriptor
	private final Map<String, EnumAccessTransformation> methodTransformers = new HashMap<>();
	
	public EnumAccessTransformation getClassTransformation(String className) {
		return classTransformers.getOrDefault(className, EnumAccessTransformation.NO_CHANGE);
	}
	
	public EnumAccessTransformation getFieldTransformation(String className, String fieldName) {
		EnumAccessTransformation wildcardResult = wildcardFieldTransformers.get(className);
		if(wildcardResult != null) return wildcardResult;
		else return fieldTransformers.getOrDefault(className + "." + fieldName, EnumAccessTransformation.NO_CHANGE);
	}
	
	public EnumAccessTransformation getMethodTransformation(String className, String methodName, String methodDescriptor) {
		EnumAccessTransformation wildcardResult = wildcardMethodTransformers.get(className);
		if(wildcardResult != null) return wildcardResult;
		else return methodTransformers.getOrDefault(className + "." + methodName + methodDescriptor, EnumAccessTransformation.NO_CHANGE);
	}
	
	public void load(InputStream from) {
		try(Scanner scan = new Scanner(from)) {
			while(scan.hasNextLine()) {
				String line = scan.nextLine();
				line = line.split("#", 2)[0].trim(); // strip comments, extraneous whitespace
				if(line.length() == 0 || line.startsWith("#")) { // skip empty lines
					continue;
				}
				
				String[] split = line.split(" ", 2);
				EnumAccessTransformation transformationType = EnumAccessTransformation.fromString(split[0]);
				String target = split[1];
				
				if(target.contains(".")) {
					//field or method transformer (they both have dots)
					if(target.contains("(")) {
						//method transformer
						if(target.endsWith(".*()")) {
							//wildcard method transformer. chop the wildcard signature off
							wildcardMethodTransformers.put(target.split("\\.", 2)[0], transformationType);
						} else {
							//non-wildcard method transformer
							methodTransformers.put(target, transformationType);
						}
					} else {
						//field transformer
						if(target.endsWith(".*")) {
							//wildcard field transformer. chop the wildcard signature off
							wildcardFieldTransformers.put(target.split("\\.", 2)[0], transformationType);
						} else {
							//non-wildcard field transformer
							fieldTransformers.put(target, transformationType);
						}
					}
				} else {
					//class transformer
					classTransformers.put(target, transformationType);
				}
			}
		}
		
		System.out.println("finished loading ATs");
	}
	
	public class AccessTransformingClassVisitor extends ClassVisitor {
		public AccessTransformingClassVisitor(ClassVisitor classVisitor) {
			super(Opcodes.ASM7, classVisitor);
		}
		
		private String visitingClass = "";
		
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			visitingClass = name;
			super.visit(version, getClassTransformation(name).apply(access), name, signature, superName, interfaces);
		}
		
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return super.visitField(getFieldTransformation(visitingClass, name).apply(access), name, descriptor, signature, value);
		}
		
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return super.visitMethod(getMethodTransformation(visitingClass, name, descriptor).apply(access), name, descriptor, signature, exceptions);
		}
	}
}

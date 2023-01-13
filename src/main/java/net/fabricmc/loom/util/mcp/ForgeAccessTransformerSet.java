package net.fabricmc.loom.util.mcp;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.IntUnaryOperator;

/**
 * A pile of Forge-format access transformers, and a parser for them.
 * 
 * @author quat
 */
public class ForgeAccessTransformerSet {
	//class
	private final Map<String, AccessTransformation> classTransformers = new HashMap<>();
	
	//class
	private final Map<String, AccessTransformation> wildcardFieldTransformers = new HashMap<>();
	//class + "." + field
	private final Map<String, AccessTransformation> fieldTransformers = new HashMap<>();
	
	//class
	private final Map<String, AccessTransformation> wildcardMethodTransformers = new HashMap<>();
	//class + "." + method + descriptor
	private final Map<String, AccessTransformation> methodTransformers = new HashMap<>();
	
	public @Nonnull AccessTransformation getClassTransformation(String className) {
		return classTransformers.getOrDefault(className, AccessTransformation.NO_CHANGE);
	}
	
	public @Nonnull AccessTransformation getFieldTransformation(String className, String fieldName) {
		AccessTransformation wildcardResult = wildcardFieldTransformers.get(className);
		if(wildcardResult != null) return wildcardResult;
		else return fieldTransformers.getOrDefault(className + "." + fieldName, AccessTransformation.NO_CHANGE);
	}
	
	public @Nonnull AccessTransformation getMethodTransformation(String className, String methodName, String methodDescriptor) {
		AccessTransformation wildcardResult = wildcardMethodTransformers.get(className);
		if(wildcardResult != null) return wildcardResult;
		else return methodTransformers.getOrDefault(className + "." + methodName + methodDescriptor, AccessTransformation.NO_CHANGE);
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
				AccessTransformation transformationType = AccessTransformation.fromString(split[0]);
				String target = split[1];
				
				if(target.contains(".")) { //field or method transformer (they both have dots)
					if(target.contains("(")) { //method transformer
						if(target.endsWith(".*()")) { //wildcard method transformer
							wildcardMethodTransformers.put(target.split("\\.", 2)[0], transformationType);
						} else { //non-wildcard method transformer
							methodTransformers.put(target, transformationType);
						}
					} else { //field transformer
						if(target.endsWith(".*")) { //wildcard field transformer
							wildcardFieldTransformers.put(target.split("\\.", 2)[0], transformationType);
						} else { //non-wildcard field transformer
							fieldTransformers.put(target, transformationType);
						}
					}
				} else { //class transformer
					classTransformers.put(target, transformationType);
				}
			}
		}
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
	
	public enum AccessTransformation implements Opcodes {
		NO_CHANGE            (acc -> acc),
		PUBLIC               (acc -> replacePublicityModifier(acc,             ACC_PUBLIC)),
		PUBLIC_DEFINALIZE    (acc -> replacePublicityModifier(definalize(acc), ACC_PUBLIC)),
		PUBLIC_FINALIZE      (acc -> replacePublicityModifier(finalize(acc),   ACC_PUBLIC)),
		PROTECTED            (acc -> replacePublicityModifier(acc,             ACC_PROTECTED)),
		PROTECTED_DEFINALIZE (acc -> replacePublicityModifier(definalize(acc), ACC_PROTECTED)),
		PROTECTED_FINALIZE   (acc -> replacePublicityModifier(finalize(acc),   ACC_PROTECTED)),
		PRIVATE              (acc -> replacePublicityModifier(acc,             ACC_PRIVATE)),
		PRIVATE_DEFINALIZE   (acc -> replacePublicityModifier(definalize(acc), ACC_PRIVATE)),
		PRIVATE_FINALIZE     (acc -> replacePublicityModifier(finalize(acc),   ACC_PRIVATE)),
		;
		
		AccessTransformation(IntUnaryOperator operation) {
			this.operation = operation;
		}
		
		public final IntUnaryOperator operation;
		
		public int apply(int prevAccess) {
			return operation.applyAsInt(prevAccess);
		}
		
		public static AccessTransformation fromString(String name) {
			switch(name) {
				case "default": return NO_CHANGE; //TODO: Forge 1.5.2 has one of these at the bottom of forge_at, might actually be a no-op at?
				case "public": return PUBLIC;
				case "public-f": return PUBLIC_DEFINALIZE;
				case "public+f": return PUBLIC_FINALIZE;
				case "protected": return PROTECTED;
				case "protected-f": return PROTECTED_DEFINALIZE;
				case "protected+f": return PROTECTED_FINALIZE;
				case "private": return PRIVATE;
				case "private-f": return PRIVATE_DEFINALIZE;
				case "private+f": return PRIVATE_FINALIZE;
				default: throw new IllegalArgumentException("Unknown access transformation type '" + name + "'");
			}
		}
		
		private static int replacePublicityModifier(int access, int publicityModifier) {
			return (access & ~(ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE)) | publicityModifier;
		}
		
		private static int finalize(int access) { //not a keyword!
			return access | ACC_FINAL;
		}
		
		private static int definalize(int access) {
			return access & ~ACC_FINAL;
		}
	}
}

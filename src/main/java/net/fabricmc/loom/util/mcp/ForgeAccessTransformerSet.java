package net.fabricmc.loom.util.mcp;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
	
	private final Set<String> touchedClasses = new HashSet<>();
	
	//and for debugging:
	private int count = 0;
	private final Set<String> usedClassTransformers = new HashSet<>();
	private final Set<String> usedWildcardFieldTransformers = new HashSet<>();
	private final Set<String> usedFieldTransformers = new HashSet<>();
	private final Set<String> usedWildcardMethodTransformers = new HashSet<>();
	private final Set<String> usedMethodTransformers = new HashSet<>();
	
	public @Nonnull AccessTransformation getClassTransformation(String className) {
		AccessTransformation classTransformation = classTransformers.get(className);
		if(classTransformation != null) {
			usedClassTransformers.add(className);
			return classTransformation;
		}
		
		return AccessTransformation.NO_CHANGE;
	}
	
	public @Nonnull AccessTransformation getFieldTransformation(String className, String fieldName) {
		AccessTransformation wildcardFieldTransformation = wildcardFieldTransformers.get(className);
		if(wildcardFieldTransformation != null) {
			usedWildcardFieldTransformers.add(className);
			return wildcardFieldTransformation;
		}
		
		String key = className + "." + fieldName;
		AccessTransformation fieldTransformation = fieldTransformers.get(key);
		if(fieldTransformation != null) {
			usedFieldTransformers.add(key);
			return fieldTransformation;
		}
		
		return AccessTransformation.NO_CHANGE;
	}
	
	public @Nonnull AccessTransformation getMethodTransformation(String className, String methodName, String methodDescriptor) {
		AccessTransformation wildcardMethodTransformation = wildcardMethodTransformers.get(className);
		if(wildcardMethodTransformation != null) {
			usedWildcardMethodTransformers.add(className);
			return wildcardMethodTransformation;
		}
		
		String key = className + "." + methodName + methodDescriptor;
		AccessTransformation methodTransformation = methodTransformers.get(key);
		if(methodTransformation != null) {
			usedMethodTransformers.add(key);
			return methodTransformation;
		}
		
		return AccessTransformation.NO_CHANGE;
	}
	
	public void load(Path path, boolean mappedAccessTransformers) throws IOException {
		for(String line : Files.readAllLines(path)) {
			line = line.split("#", 2)[0].trim(); //strip comments, extraneous whitespace
			if(line.length() == 0 || line.startsWith("#")) continue; //skip empty lines
			
			String[] split = line.split(" ");
			AccessTransformation transformationType = AccessTransformation.fromString(split[0]);
			
			if(mappedAccessTransformers) { //Forge 1.7 format - class names separated from method/field names with a space
				String owningClass = split[1].replace('.', '/'); //-> normalize to 'internal name'
				touchedClasses.add(owningClass);
				
				if(split.length > 2) {
					String member = split[2].replace('.', '/'); //-> normalize to 'internal name'
					if(member.contains("(")) {
						if(member.contains("*")) wildcardMethodTransformers.put(owningClass, transformationType);
						else methodTransformers.put(owningClass + "." + member, transformationType);
					} else {
						if(member.contains("*")) wildcardFieldTransformers.put(owningClass, transformationType);
						else fieldTransformers.put(owningClass + "." + member, transformationType);
					}
				} else {
					classTransformers.put(owningClass, transformationType);
				}
			} else { //Forge 1.6 and below format - class names glued to method/field names with a "."
				String target = split[1];
				if(target.contains(".")) {
					//TODO: splitting on `.` may only work because Minecraft obf classes are all unpackaged.
					// I don't know whether this format uses `/` to split packages, but if it doesn't, this is wrong.
					String[] targetSplit = target.split("\\.", 2);
					String owningClass = targetSplit[0];
					String member = targetSplit[1];
					
					touchedClasses.add(owningClass);
					
					if(member.contains("(")) {
						if(member.contains("*")) wildcardMethodTransformers.put(owningClass, transformationType);
						else methodTransformers.put(target, transformationType);
					} else {
						if(member.contains("*")) wildcardFieldTransformers.put(owningClass, transformationType);
						else fieldTransformers.put(target, transformationType);
					}
				} else {
					touchedClasses.add(target);
					classTransformers.put(target, transformationType);
				}
			}
		}
		
		count = classTransformers.size() + fieldTransformers.size() + methodTransformers.size() + wildcardFieldTransformers.size() + wildcardMethodTransformers.size();
	}
	
	public boolean touchesClass(String className) {
		return touchedClasses.contains(className);
	}
	
	public class AccessTransformingClassVisitor extends ClassVisitor implements Opcodes {
		public AccessTransformingClassVisitor(ClassVisitor classVisitor) {
			super(Opcodes.ASM7, classVisitor);
		}
		
		private String visitingClass = "";
		private boolean visitingExtendableClass = false;
		
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			int newAccess = getClassTransformation(name).apply(access);
			
			visitingClass = name;
			visitingExtendableClass = (newAccess & (ACC_PRIVATE | ACC_FINAL)) == 0;
			
			super.visit(version, newAccess, name, signature, superName, interfaces);
		}
		
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return super.visitField(getFieldTransformation(visitingClass, name).apply(access), name, descriptor, signature, value);
		}
		
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor sup = super.visitMethod(getMethodTransformation(visitingClass, name, descriptor).apply(access), name, descriptor, signature, exceptions);
			
			if(visitingExtendableClass) return new InvokeSpecialToInvokeVirtualVisitor(sup);
			else return sup;
		}
		
		//When a class calls a private method on itself, Java compiles the call to the INVOKESPECIAL instruction.
		//This instruction calls *exactly* the method named in its owner/name/desc arguments; no overriding is possible.
		//And that makes sense; the method is private, so there's no need to check for any overrides in any subclasses.
		//But since we're access transforming, it's possible that the method is now overridable when it wasn't before.
		//If that's the case, we need to update the call to an INVOKEVIRTUAL, so that extending the class works as expected.
		//
		//Caveat: we're doing this in a single visiting pass.
		//We've seen the owning class, so we know whether it's extendable, but since we might not have seen the access flags
		//of the target method yet, we don't know whether it's actually overridable. However, it's safe to simply assume the
		//worst and turn every intra-class INVOKESPECIAL call into an INVOKEVIRTUAL, because INVOKESPECIAL is not *required*
		//to call a private method on the same class.
		//
		//TODO: This was only added in https://github.com/MinecraftForge/FML/commit/c828bb63c57cb10c23d9b1c3a6934e9f9ddba37b
		// I think this is post-1.7.2 only?
		public class InvokeSpecialToInvokeVirtualVisitor extends MethodVisitor {
			public InvokeSpecialToInvokeVirtualVisitor(MethodVisitor parent) {
				super(Opcodes.ASM7, parent);
			}
			
			@Override
			public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
				if(opcode == Opcodes.INVOKESPECIAL && !name.equals("<init>") && owner.equals(visitingClass)) {
					opcode = Opcodes.INVOKEVIRTUAL;
				}
				
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
			}
		}
	}
	
	private static final int ACCESS_MASK = (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED); // == 7
	private static final int ACC_PACKAGE_PRIVATE = 0;
	public enum AccessTransformation implements Opcodes {
		NO_CHANGE                  (acc -> acc),
		PUBLIC                     (acc -> upgradePublicityModifier(acc,             ACC_PUBLIC)),
		PUBLIC_DEFINALIZE          (acc -> upgradePublicityModifier(definalize(acc), ACC_PUBLIC)),
		PUBLIC_FINALIZE            (acc -> upgradePublicityModifier(finalize(acc),   ACC_PUBLIC)),
		PACKAGE_PRIVATE            (acc -> upgradePublicityModifier(acc,             ACC_PACKAGE_PRIVATE)),
		PACKAGE_PRIVATE_FINALIZE   (acc -> upgradePublicityModifier(finalize(acc),   ACC_PACKAGE_PRIVATE)),
		PACKAGE_PRIVATE_DEFINALIZE (acc -> upgradePublicityModifier(definalize(acc), ACC_PACKAGE_PRIVATE)),
		PROTECTED                  (acc -> upgradePublicityModifier(acc,             ACC_PROTECTED)),
		PROTECTED_DEFINALIZE       (acc -> upgradePublicityModifier(definalize(acc), ACC_PROTECTED)),
		PROTECTED_FINALIZE         (acc -> upgradePublicityModifier(finalize(acc),   ACC_PROTECTED)),
		PRIVATE                    (acc -> upgradePublicityModifier(acc,             ACC_PRIVATE)),
		PRIVATE_DEFINALIZE         (acc -> upgradePublicityModifier(definalize(acc), ACC_PRIVATE)),
		PRIVATE_FINALIZE           (acc -> upgradePublicityModifier(finalize(acc),   ACC_PRIVATE)),
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
				case "public": return PUBLIC;
				case "public-f": return PUBLIC_DEFINALIZE;
				case "public+f": return PUBLIC_FINALIZE;
				case "default": return PACKAGE_PRIVATE;
				case "default-f": return PACKAGE_PRIVATE_DEFINALIZE;
				case "default+f": return PACKAGE_PRIVATE_FINALIZE;
				case "protected": return PROTECTED;
				case "protected-f": return PROTECTED_DEFINALIZE;
				case "protected+f": return PROTECTED_FINALIZE;
				case "private": return PRIVATE;
				case "private-f": return PRIVATE_DEFINALIZE;
				case "private+f": return PRIVATE_FINALIZE;
				default: throw new IllegalArgumentException("Unknown access transformation type '" + name + "'");
			}
		}
		
		@Override
		public String toString() {
			if(this == NO_CHANGE) return "NO_CHANGE__NOT_FROM_FORGE";
			else return this.name()
				.replace("PACKAGE_PRIVATE", "default")
				.replace("_DEFINALIZE", "-f")
				.replace("_FINALIZE", "+f")
				.toLowerCase(Locale.ROOT);
		}
		
		private static int upgradePublicityModifier(int currentAccess, int newPublicityModifier) {
			//Ok, so this is the access mask *replaced* with the new publicity modifier:
			int replacedAccess = (currentAccess & ~ACCESS_MASK) | newPublicityModifier;
			//We could return this now, but we need to check that we're actually *upgrading* the visibility.
			//In practice, this turns up in wildcard transformers. Forge 1.5.2 declares a 'protected aww.*()' transformer, e.g.
			//We should upgrade private methods into protected ones, but we shouldn't downgrade public ones.
			
			//See FML's AccessTransformer.getFixedAccess.
			//The original switch statement is kindof a mess, but it boils down to this.
			//Also the messy ACC_PUBLIC case in the original is redundant. The ternary expression evaluates to ACC_PUBLIC in both branches.
			switch(currentAccess & ACCESS_MASK) {
				case ACC_PRIVATE:
					//If the field is private, well, can't make it more private
					return replacedAccess;
				case ACC_PACKAGE_PRIVATE:
					//If the field is package-private, don't replace it with a private one
					return newPublicityModifier == ACC_PRIVATE ? currentAccess : replacedAccess;
				case ACC_PROTECTED:
					//If the field is protected, don't replace it with a private or package-private one
					return newPublicityModifier == ACC_PRIVATE || newPublicityModifier == ACC_PACKAGE_PRIVATE ? currentAccess : replacedAccess;
				default:
					//If the field is public, well, can't make it more public
					return currentAccess;
			}
		}
		
		private static int finalize(int access) { //not a keyword!
			return access | ACC_FINAL;
		}
		
		private static int definalize(int access) {
			return access & ~ACC_FINAL;
		}
	}
	
	//debugging stuff:
	
	public int getCount() {
		return count;
	}
	
	public int getTouchedClassCount() {
		return touchedClasses.size();
	}
	
	public void resetUsageData() {
		usedClassTransformers.clear();
		usedWildcardFieldTransformers.clear();
		usedFieldTransformers.clear();
		usedWildcardMethodTransformers.clear();
		usedMethodTransformers.clear();
	}
	
	public List<String> reportUnusedTransformers() {
		List<String> report = new ArrayList<>();
		
		reportUnused(classTransformers, usedClassTransformers, report, "class transformer");
		
		reportUnused(wildcardFieldTransformers, usedWildcardFieldTransformers, report, "wildcard field transformer");
		reportUnused(fieldTransformers, usedFieldTransformers, report, "field transformer");
		
		reportUnused(wildcardMethodTransformers, usedWildcardMethodTransformers, report, "wildcard method transformer");
		reportUnused(methodTransformers, usedMethodTransformers, report, "method transformer");
		
		return report;
	}
	
	private void reportUnused(Map<String, AccessTransformation> wholeSet, Set<String> usedSet, List<String> report, String reportPrefix) {
		wholeSet.forEach((key, transformation) -> {
			if(usedSet.contains(key)) return; //continue
			
			report.add(String.format("Unused %s: %s %s", reportPrefix, transformation.toString(), key));
		});
	}
}

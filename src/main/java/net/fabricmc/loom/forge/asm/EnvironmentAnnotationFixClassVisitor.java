package net.fabricmc.loom.forge.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class EnvironmentAnnotationFixClassVisitor extends ClassVisitor {
	public EnvironmentAnnotationFixClassVisitor(ClassVisitor classVisitor) {
		super(Opcodes.ASM7, classVisitor);
	}
	
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if(descriptor.equals("Lcpw/mods/fml/relauncher/SideOnly;")) {
			return new EnvFixAnnotationVisitor(visitAnnotation("Lnet/fabricmc/api/Environment;", visible));
		} else {
			return super.visitAnnotation(descriptor, visible);
		}
	}
	
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return new EnvFixMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions));
	}
	
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return new EnvFixFieldVisitor(super.visitField(access, name, descriptor, signature, value));
	}
	
	private static class EnvFixAnnotationVisitor extends AnnotationVisitor {
		public EnvFixAnnotationVisitor(AnnotationVisitor annotationVisitor) {
			super(Opcodes.ASM7, annotationVisitor);
		}
		
		public void visitEnum(String name, String descriptor, String value) {
			if(descriptor.equals("Lcpw/mods/fml/relauncher/Side;")) {
				super.visitEnum(name, "Lnet/fabricmc/api/EnvType;", value); // both forge and fabric use SERVER and CLIENT so no value change necessary
			} else {
				super.visitEnum(name, descriptor, value);
			}
		}
	}
	
	private static class EnvFixMethodVisitor extends MethodVisitor {
		public EnvFixMethodVisitor(MethodVisitor methodVisitor) {
			super(Opcodes.ASM7, methodVisitor);
		}
		
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if(descriptor.equals("Lcpw/mods/fml/relauncher/SideOnly;")) {
				return new EnvFixAnnotationVisitor(visitAnnotation("Lnet/fabricmc/api/Environment;", visible));
			} else {
				return super.visitAnnotation(descriptor, visible);
			}
		}
	}
	
	private static class EnvFixFieldVisitor extends FieldVisitor {
		public EnvFixFieldVisitor(FieldVisitor fieldVisitor) {
			super(Opcodes.ASM7, fieldVisitor);
		}
		
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if(descriptor.equals("Lcpw/mods/fml/relauncher/SideOnly;")) {
				return new EnvFixAnnotationVisitor(visitAnnotation("Lnet/fabricmc/api/Environment;", visible));
			} else {
				return super.visitAnnotation(descriptor, visible);
			}
		}
	}
}

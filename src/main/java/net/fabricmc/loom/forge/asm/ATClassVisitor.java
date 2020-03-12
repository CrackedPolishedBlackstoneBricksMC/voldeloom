package net.fabricmc.loom.forge.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.loom.forge.AccessTransformation;
import net.fabricmc.loom.forge.ForgeATConfig;

public class ATClassVisitor extends ClassVisitor {

	private final ForgeATConfig atConfig;
	private String visiting = "";

	public ATClassVisitor(ClassVisitor classVisitor, ForgeATConfig atConfig) {
		super(Opcodes.ASM7, classVisitor);
		this.atConfig = atConfig;
	}

	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		visiting = name;
		AccessTransformation at;
		if ((at = atConfig.accessTransformers.get(name)) != null) {
			System.out.println("AT for type " + name);
			super.visit(version, at.transform(access), name, signature, superName, interfaces);
		} else {
			super.visit(version, access, name, signature, superName, interfaces);
		}
	}

	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		AccessTransformation at;
		if ((at = atConfig.accessTransformers.get(visiting + "." + name + descriptor)) != null) {
			System.out.println("AT for method " + visiting + "." + name + descriptor);
			return super.visitMethod(at.transform(access), name, descriptor, signature, exceptions);
		} else {
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}

	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		AccessTransformation at;
		if ((at = atConfig.accessTransformers.get(visiting + "." + name)) != null) {
			System.out.println("AT for field " + visiting + "." + name);
			return super.visitField(at.transform(access), name, descriptor, signature, value);
		} else {
			return super.visitField(access, name, descriptor, signature, value);
		}
	}
}

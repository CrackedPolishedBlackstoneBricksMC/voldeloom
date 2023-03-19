package net.fabricmc.loom.mcp;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class NaiveAsmSrgRenamer extends ClassVisitor implements Opcodes {
	public NaiveAsmSrgRenamer(ClassVisitor classVisitor, Members fields, Members methods) {
		super(Opcodes.ASM9, classVisitor);
		this.fields = fields;
		this.methods = methods;
	}
	
	private final Members fields, methods;
	
	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		//fields
		Members.Entry entry = fields.remapSrg(name);
		return super.visitField(access, entry == null ? name : entry.remappedName, descriptor, signature, value);
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		//methods - remap the name if applicable, but always visit the code of the method
		Members.Entry entry = methods.remapSrg(name);
		return new NaiveAsmMethodVisitor(super.visitMethod(access, entry == null ? name : entry.remappedName, descriptor, signature, exceptions));
	}
	
	private /* non-static */ class NaiveAsmMethodVisitor extends MethodVisitor implements Opcodes {
		public NaiveAsmMethodVisitor(MethodVisitor methodVisitor) {
			super(Opcodes.ASM9, methodVisitor);
		}
		
		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			//accesses to fields (loads and stores)
			Members.Entry entry = fields.remapSrg(name);
			super.visitFieldInsn(opcode, owner, entry == null ? name : entry.remappedName, descriptor);
		}
		
		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			//calls to methods
			Members.Entry entry = methods.remapSrg(name);
			super.visitMethodInsn(opcode, owner, entry == null ? name : entry.remappedName, descriptor, isInterface);
		}
		
		@Override
		public void visitLdcInsn(Object value) {
			//string literals (!)
			//yes, mcp does this; check patches/Start.java, it refers to a field using an SRG name
			//even though *we* don't use Start.java, it's still correct to map like this, pretty sure
			if(value instanceof String) super.visitLdcInsn(new NaiveTextualSrgRenamer(fields, methods).rename((String) value));
			else super.visitLdcInsn(value);
		}
	}
}

package net.fabricmc.loom.forge.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class UserdevLaunchHandlerVisitor extends ClassVisitor {

	public UserdevLaunchHandlerVisitor(ClassVisitor classVisitor) {
		super(Opcodes.ASM8, classVisitor);
	}

	@Override
	public void visitEnd() {
		MethodVisitor mv = visitMethod(Opcodes.ACC_PUBLIC, "setup", "(Lcpw/mods/modlauncher/api/IEnvironment;Ljava/util/Map;)V", "(Lcpw/mods/modlauncher/api/IEnvironment;Ljava/util/Map<Ljava/lang/String;*>;)V", null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/nucleafarts/yarnforgedev/ArgumentInjectCallback", "callback", "(Ljava/util/Map;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		/*mv.visitVarInsn(Opcodes.ALOAD, 0); // this (1)
		mv.visitVarInsn(Opcodes.ALOAD, 1); // environment (2)
		mv.visitVarInsn(Opcodes.ALOAD, 2); // arguments (3)
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraftforge/userdev/FMLUserdevLaunchProvider", "setup", "(Lcpw/mods/modlauncher/api/IEnvironment;Ljava/util/Map;)V", false); // (0)
		mv.visitVarInsn(Opcodes.ALOAD, 2); // arguments (1)
		mv.visitLdcInsn("explodedTargets"); // (2)
		mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList"); // (3)
		mv.visitInsn(Opcodes.DUP); // (4)
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false); // (3)
		mv.visitInsn(Opcodes.DUP_X2); // (4)
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true); // arguments.put("explodedTargets", new ArrayList()) (2)
		mv.visitInsn(Opcodes.POP); // (1)
		mv.visitLdcInsn("yarnforgedev.modResourcesPath"); // (2)
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false); // (2)
		mv.visitInsn(Opcodes.ICONST_0); // (3)
		mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String"); // (3)
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/nio/file/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", false); // (2)
		mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList"); // (3)
		mv.visitInsn(Opcodes.DUP); // (4)
		mv.visitInsn(Opcodes.DUP); // (5)
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false); // ArrayList() (4)
		mv.visitLdcInsn("yarnforgedev.modClassesPath"); // (5)
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false); // (5)
		mv.visitInsn(Opcodes.ICONST_0); // (6)
		mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String"); // (6)
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/nio/file/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", false); // (5)
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);  // (4)
		mv.visitInsn(Opcodes.POP); // (3)
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/apache/commons/lang3/tuple/Pair", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Lorg/apache/commons/lang3/tuple/Pair;", false); // (2)
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false); // (1)
		mv.visitInsn(Opcodes.POP); // (0)
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(6, 3);*/
		mv.visitEnd();
	}
}

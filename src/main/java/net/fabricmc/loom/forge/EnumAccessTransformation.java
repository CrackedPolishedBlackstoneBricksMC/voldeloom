package net.fabricmc.loom.forge;

import java.util.function.IntUnaryOperator;

public enum EnumAccessTransformation implements org.objectweb.asm.Opcodes {
	NO_CHANGE            (acc -> acc),
	PUBLIC               (acc -> replacePublicityModifier(acc, ACC_PUBLIC)),
	PUBLIC_DEFINALIZE    (acc -> definalize(replacePublicityModifier(acc, ACC_PUBLIC))),
	PUBLIC_FINALIZE      (acc -> finalize(replacePublicityModifier(acc, ACC_PUBLIC))),
	PROTECTED            (acc -> replacePublicityModifier(acc, ACC_PROTECTED)),
	PROTECTED_DEFINALIZE (acc -> definalize(replacePublicityModifier(acc, ACC_PROTECTED))),
	PROTECTED_FINALIZE   (acc -> finalize(replacePublicityModifier(acc, ACC_PROTECTED))),
	PRIVATE              (acc -> replacePublicityModifier(acc, ACC_PRIVATE)),
	PRIVATE_DEFINALIZE   (acc -> definalize(replacePublicityModifier(acc, ACC_PRIVATE))),
	PRIVATE_FINALIZE     (acc -> finalize(replacePublicityModifier(acc, ACC_PRIVATE))),
	;
	
	EnumAccessTransformation(IntUnaryOperator operation) {
		this.operation = operation;
	}
	
	public final IntUnaryOperator operation;
	
	public int apply(int prevAccess) {
		return operation.applyAsInt(prevAccess);
	}
	
	public static EnumAccessTransformation fromString(String name) {
		//never thought i'd miss rusts's string handling functions but here we are
		name = name.trim();
		String firstWord;
		int blah = name.indexOf(' ');
		if(blah == -1) {
			firstWord = name;
		} else {
			firstWord = name.substring(0, blah);
		}
		
		switch(firstWord) {
			case "public": return PUBLIC;
			case "public-f": return PUBLIC_DEFINALIZE;
			case "public+f": return PUBLIC_FINALIZE;
			case "protected": return PROTECTED;
			case "protected-f": return PROTECTED_DEFINALIZE;
			case "protected+f": return PROTECTED_FINALIZE;
			case "private": return PRIVATE;
			case "private-f": return PRIVATE_DEFINALIZE;
			case "private+f": return PRIVATE_FINALIZE;
			default: throw new IllegalArgumentException("Unknown access transformation type " + name);
		}
	}
	
	private static int clearPublicityModifiers(int access) {
		int publicityModifiers = ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE;
		return access & ~publicityModifiers;
	}
	
	private static int replacePublicityModifier(int access, int publicityModifier) {
		return clearPublicityModifiers(access) | publicityModifier;
	}
	
	private static int finalize(int access) { //not a keyword!
		return access | ACC_FINAL;
	}
	
	private static int definalize(int access) {
		return access & ~ACC_FINAL;
	}
}

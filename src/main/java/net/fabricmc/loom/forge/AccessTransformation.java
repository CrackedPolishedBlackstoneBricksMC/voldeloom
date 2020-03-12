package net.fabricmc.loom.forge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Opcodes;

public class AccessTransformation {

	private static final Map<String, Integer> ACCESS_MAP = new HashMap<>();
	
	static {
		ACCESS_MAP.put("public", Opcodes.ACC_PUBLIC);
		ACCESS_MAP.put("protected", Opcodes.ACC_PROTECTED);
		ACCESS_MAP.put("private", Opcodes.ACC_PRIVATE);
	}
	
	private final int newAccess;
	private final boolean affectsFinal;
	
	public AccessTransformation(int newAccess) {
		this.newAccess = newAccess;
		affectsFinal = false;
	}
	
	public AccessTransformation(int newAccess, boolean newFinal) {
		this.newAccess = newFinal ? newAccess | Opcodes.ACC_FINAL : newAccess;
		affectsFinal = true;
	}
		
	public int transform(int access) {
		if(affectsFinal) {
			access &= ~Opcodes.ACC_FINAL;
		}
		access &= ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
		return access | newAccess;
	}
	
	public static AccessTransformation parse(String string) {
		//System.out.println(string);
		String[] split = string.split("[+-]", 2);
		int newAccess = ACCESS_MAP.getOrDefault(split[0], -1);
		if(newAccess == -1) {
			System.out.println(Arrays.toString(split));
			throw new RuntimeException("Could not parse AT " + string);
		}
		if(split.length > 1) {
			return new AccessTransformation(newAccess, string.contains("+"));
		} else {
			return new AccessTransformation(newAccess);
		}
	}
}

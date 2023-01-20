package net.fabricmc.loom.util.mcp;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * A mappings buffer, from one naming scheme to another. Loads in a bunch of mappings, then dispenses them into another MappingAcceptor.
 * 
 * @author TwilightFlower
 */
public class AcceptorProvider implements IMappingProvider, MappingAcceptor {
	private final Map<String, String> classes = new HashMap<>();
	private final Map<HashableMember, String> methods = new HashMap<>();
	private final Map<HashableMember, String> fields = new HashMap<>();
	private final Map<HashableArgIndex, String> methodArgs = new HashMap<>();

	@Override
	public void acceptClass(String srcName, String dstName) {
		classes.put(srcName, dstName);
	}

	@Override
	public void acceptMethod(Member method, String dstName) {
		methods.put(new HashableMember(method), dstName);
	}

	@Override
	public void acceptMethodArg(Member method, int lvIndex, String dstName) {
		methodArgs.put(new HashableArgIndex(new HashableMember(method), lvIndex), dstName);
	}

	@Override
	public void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
		//it's a secret to everybody
	}

	@Override
	public void acceptField(Member field, String dstName) {
		fields.put(new HashableMember(field), dstName);
	}

	@Override
	public void load(MappingAcceptor out) {
		classes.forEach(out::acceptClass);
		methods.forEach((method, dstName) -> out.acceptMethod(method.actual, dstName));
		fields.forEach((field, dstName) -> out.acceptField(field.actual, dstName));
		methodArgs.forEach((argIndex, dstName) -> out.acceptMethodArg(argIndex.method.actual, argIndex.lvIndex, dstName));
	}
	
	/**
	 * tiny-remapper's Member doesn't have equals/hashcode defined, so this is a trivial wrapper around it
	 */
	private static class HashableMember {
		final Member actual;
		
		HashableMember(Member actual) {
			this.actual = actual;
		}
		
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			HashableMember other = (HashableMember) o;
			return other.actual.owner.equals(actual.owner) &&
				other.actual.name.equals(actual.name) &&
				other.actual.desc.equals(actual.desc);
		}
		
		public int hashCode() {
			return ((actual.name.hashCode() * 31) + actual.desc.hashCode() * 31) + actual.owner.hashCode();
		}
	}
	
	/**
	 * we have records at home
	 */
	private static class HashableArgIndex {
		final HashableMember method;
		final int lvIndex;
		
		HashableArgIndex(HashableMember method, int lvIndex) {
			this.method = method;
			this.lvIndex = lvIndex;
		}
		
		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			HashableArgIndex other = (HashableArgIndex) o;
			return lvIndex == other.lvIndex && method.equals(other.method);
		}
		
		@Override
		public int hashCode() {
			return 31 * method.hashCode() + lvIndex;
		}
	}
}

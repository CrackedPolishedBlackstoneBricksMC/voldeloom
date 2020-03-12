package net.fabricmc.loom.forge.mapping;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;

public class AcceptorProvider implements IMappingProvider, MappingAcceptor {

	private final Map<String, String> classes = new HashMap<>();
	private final Map<MemberHashCode, String> methods = new HashMap<>();
	private final Map<MemberHashCode, String> fields = new HashMap<>();
	private final Map<ArgIndex, String> methodArgs = new HashMap<>();

	@Override
	public void acceptClass(String srcName, String dstName) {
		classes.put(srcName, dstName);
	}

	@Override
	public void acceptMethod(Member method, String dstName) {
		methods.put(new MemberHashCode(method), dstName);
	}

	@Override
	public void acceptMethodArg(Member method, int lvIndex, String dstName) {
		methodArgs.put(new ArgIndex(method, lvIndex), dstName);
	}

	@Override
	public void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
		
	}

	@Override
	public void acceptField(Member field, String dstName) {
		fields.put(new MemberHashCode(field), dstName);
	}

	@Override
	public void load(MappingAcceptor out) {
		classes.forEach(out::acceptClass);
		methods.forEach((m, d) -> out.acceptMethod(m.actual, d));
		fields.forEach((m, d) -> out.acceptField(m.actual, d));
		methodArgs.forEach((a, d) -> out.acceptMethodArg(a.method, a.lvIndex, d));
	}
	
	private static class MemberHashCode {
		final Member actual;
		
		MemberHashCode(Member actual) {
			this.actual = actual;
		}
		
		public int hashCode() {
			return actual.name.hashCode() 
					* actual.desc.hashCode() 
					* actual.owner.hashCode();
		}
		
		public boolean equals(Object o) {
			if(!(o instanceof MemberHashCode)) return false;
			MemberHashCode m = (MemberHashCode) o;
			return m.actual.owner.equals(actual.owner) && m.actual.name.equals(actual.name) && m.actual.desc.equals(actual.desc);
		}
	}
	
	private static class ArgIndex {
		final Member method;
		final int lvIndex;
		ArgIndex(Member method, int lvIndex) {
			this.method = method;
			this.lvIndex = lvIndex;
		}
		
		public int hashCode() {
			return method.name.hashCode() * method.desc.hashCode() * method.owner.hashCode() * lvIndex;
		}
	}
}

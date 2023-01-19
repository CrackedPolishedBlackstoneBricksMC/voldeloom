package net.fabricmc.loom.util.mcp;

import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import net.fabricmc.tinyremapper.IMappingProvider.Member;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A MappingAcceptor that buffers all mappings it accepts and can write them back as a tinyv1 file.
 * Input mappings from "firstCol" to "secondCol", then call {@code acceptSecond} and input mappings from "secondCol" to "thirdCol".
 * (I (quat) think that's how it works anyway)
 * 
 * @author TwilightFlower
 * <hr>
 * Original comment:
 * <p>
 * note: if the 2nd set has mappings that are not in the first, this will error.
 * if the 1st set contains mappings not in the 1st, the output tiny file will be malformed.
 */
public class TinyWriter3Column implements MappingAcceptor {
	
	private final Map<String, MutablePair<String, String>> classes = new HashMap<>();
	private final Map<MemberHashCode, MutablePair<String, String>> fields = new HashMap<>();
	private final Map<MemberHashCode, MutablePair<String, String>> methods = new HashMap<>();
	private final String header;
	private boolean acceptingSecond = false;
	
	private static <K, V> void put(Map<K, MutablePair<V, V>> map, K key, V value, boolean second) {
		if(second) {
			map.get(key).second = value;
		} else {
			map.put(key, new MutablePair<>(value));
		}
	}
	
	public TinyWriter3Column(String firstCol, String secondCol, String thirdCol) {
		header = "v1\t" + firstCol + "\t" + secondCol + "\t" + thirdCol;
	}
	
	public void acceptSecond() {
		acceptingSecond = true;
	}
	
	@Override
	public void acceptClass(String srcName, String dstName) {
		put(classes, srcName, dstName, acceptingSecond);
	}

	@Override
	public void acceptMethod(Member method, String dstName) {
		put(methods, new MemberHashCode(method), dstName, acceptingSecond);
	}

	@Override
	public void acceptMethodArg(Member method, int lvIndex, String dstName) {
		//tinyv1 writer. does not support.
	}

	@Override
	public void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
		//tinyv1 writer. does not support.
	}

	@Override
	public void acceptField(Member field, String dstName) {
		put(fields, new MemberHashCode(field), dstName, acceptingSecond);
	}

	public void write(OutputStream outStream) {
		if(!acceptingSecond) throw new IllegalStateException("Attempted to write 3 column tiny without accepting 3rd column?");
		PrintStream out = new PrintStream(outStream);
		out.println(header);
		for(Map.Entry<String, MutablePair<String, String>> e : classes.entrySet()) {
			out.println("CLASS\t" + e.getKey() + "\t" + e.getValue());
		}
		for(Map.Entry<MemberHashCode, MutablePair<String, String>> e : methods.entrySet()) {
			out.println("METHOD\t" + e.getKey() + "\t" + e.getValue());
		}
		for(Map.Entry<MemberHashCode, MutablePair<String, String>> e : fields.entrySet()) {
			out.println("FIELD\t" + e.getKey() + "\t" + e.getValue());
		}
	}
	
	private static class MemberHashCode {
		final Member actual;
		final String str;
		
		MemberHashCode(Member actual) {
			this.actual = actual;
			str = actual.owner + "\t" + actual.desc + "\t" + actual.name;
		}
		
		public int hashCode() {
			return str.hashCode();
		}
		
		public String toString() {
			return str;
		}
		
		public boolean equals(Object o) {
			if(!(o instanceof MemberHashCode)) return false;
			MemberHashCode m = (MemberHashCode) o;
			return m.actual.owner.equals(actual.owner) && m.actual.name.equals(actual.name) && m.actual.desc.equals(actual.desc);
		}
	}
	
	private static class MutablePair<T, U> {
		T first;
		U second;
		MutablePair(T first, U second) {
			this.first = first;
			this.second = second;
		}
		
		MutablePair(T first) {
			this.first = first;
		}
		
		MutablePair() { }
		
		public String toString() {
			return first + "\t" + second;
		}
	}
}

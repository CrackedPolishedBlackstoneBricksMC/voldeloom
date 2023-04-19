package net.fabricmc.loom.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Key-value property store, that hashes stably - the same set of properties leads to the same hash.
 * These are used to suffix filenames in the Gradle cache.
 */
public class Props {
	private final Map<String, String> properties = new TreeMap<>(); //keeps keys sorted, so hashing is stable
	
	public Props put(String key, String value) {
		//nulls to empties
		if(key == null) key = "";
		if(value == null) value = "";
		
		//this'd break the parser, and i can't be bothered to implement escaping ;)
		if(key.indexOf('=') != -1) throw new IllegalArgumentException("Invalid character '=' in key: " + key);
		
		properties.put(key, value);
		
		return this;
	}
	
	public Props putAll(Props other) {
		other.properties.forEach(this::put);
		return this;
	}
	
	public boolean has(String prop) {
		return properties.containsKey(prop);
	}
	
	public Props remove(String prop) {
		properties.remove(prop);
		return this;
	}
	
	public String hexHash() {
		MessageDigest sha = Checksum.SHA256.get();
		properties.forEach((key, value) -> {
			sha.update(key.getBytes(StandardCharsets.UTF_8));
			sha.update((byte) 0);
			sha.update(value.getBytes(StandardCharsets.UTF_8));
			sha.update((byte) 0);
		});
		return Checksum.toHexStringPrefix(sha.digest(), 8);
	}
	
	public String subst(String s) {
		if(s.contains("{HASH}")) return s.replace("{HASH}", hexHash());
		else throw new IllegalArgumentException("String " + s + " doesn't contain hash template ({HASH})");
	}
	
	public Props copy() {
		Props copy = new Props();
		copy.properties.putAll(properties);
		return copy;
	}
	
	//TODO: useful?
//	public List<String> write() {
//		List<String> result = new ArrayList<>(properties.size());
//		properties.forEach((key, value) -> result.add(key + '=' + value));
//		return result;
//	}
//	
//	public Props read(List<String> in) {
//		properties.clear();
//		for(String line : in) {
//			if(line.isEmpty() || line.indexOf('=') == -1) continue;
//			String[] split = line.split("=", 2);
//			put(split[0], split[1] == null ? "" : split[1]);
//		}
//		
//		return this;
//	}
//	
//	public void write(Path path) throws IOException {
//		Files.write(path, write(), StandardCharsets.UTF_8);
//	}
//	
//	public void read(Path path) throws IOException {
//		read(Files.readAllLines(path));
//	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		return properties.equals(((Props) o).properties);
	}
	
	@Override
	public int hashCode() {
		return properties.hashCode();
	}
}

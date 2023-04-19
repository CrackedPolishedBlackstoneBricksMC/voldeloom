package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class Props {
	private final Map<String, String> properties = new TreeMap<>(); //keeps keys sorted, so hashing is stable
	private transient String hexHashCache = null;
	
	public Props put(String key, String value) {
		hexHashCache = null; //invalidate cache
		
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
	
	public String hexHash() {
		if(hexHashCache != null) return hexHashCache;
		
		MessageDigest sha = Checksum.SHA256.get();
		properties.forEach((key, value) -> {
			sha.update(key.getBytes(StandardCharsets.UTF_8));
			sha.update((byte) 0);
			sha.update(value.getBytes(StandardCharsets.UTF_8));
			sha.update((byte) 0);
		});
		
		hexHashCache = Checksum.toHexStringPrefix(sha.digest(), 8);
		return hexHashCache;
	}
	
	public Path substFilename(Path path) {
		return path.resolveSibling(subst(path.getFileName().toString()));
	}
	
	public String subst(String s) {
		if(s.contains("{HASH}")) return s.replace("{HASH}", hexHash());
		else throw new IllegalArgumentException("String " + s + " doesn't contain props hash template ({HASH})");
	}
	
	public Props copy() {
		Props copy = new Props();
		copy.properties.putAll(properties);
		return copy;
	}
	
	//TODO: useful?
	public List<String> write() {
		List<String> result = new ArrayList<>(properties.size());
		properties.forEach((key, value) -> result.add(key + '=' + value));
		return result;
	}
	
	//TODO: useful?
	public Props read(List<String> in) {
		properties.clear();
		for(String line : in) {
			if(line.isEmpty() || line.indexOf('=') == -1) continue;
			String[] split = line.split("=", 2);
			put(split[0], split[1] == null ? "" : split[1]);
		}
		
		return this;
	}
	
	//TODO: useful?
	public void write(Path path) throws IOException {
		Files.write(path, write(), StandardCharsets.UTF_8);
	}
	
	//TODO: useful?
	public void read(Path path) throws IOException {
		read(Files.readAllLines(path));
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		Props props = (Props) o;
		return properties.equals(props.properties);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(properties);
	}
}

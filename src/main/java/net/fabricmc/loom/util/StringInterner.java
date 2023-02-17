package net.fabricmc.loom.util;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Java strings are immutable, so if two strings contain exactly the same content, there's no reason to have
 * two separate memory allocations. To help you avoid this problem, this class contains a cache of strings;
 * adding a string to the bucket that equals a previously-added string will return the previously-added string.
 * <p>
 * The main difference this class has compared to {@code String.intern} is that that method adds them to a permanent
 * static cache of strings, but this cache is temporary and goes away when the {@code StringInterner} does.
 * <p>
 * Do NOT store these in a field if it can be avoided. Keep it method-scoped.
 */
public class StringInterner implements Closeable {
	private Map<String, String> strings = new HashMap<>();
	
	/**
	 * If an identical copy of this string already exists in the StringInterner's memory space, it will be returned.<br>
	 * Otherwise, it will be cached in the memory space, and returned.<br>
	 * The return value of this function always {@code .equals()} the argument.
	 * @param s  the string to intern
	 * @return deduplicated version of the string
	 */
	public String intern(String s) {
		if(s == null) return s;
		
		if(strings.containsKey(s)) return strings.get(s);
		
		strings.put(s, s);
		return s;
	}
	
	/**
	 * Calls {@code intern} on every string of an array in-place.
	 * @param array the array to intern
	 */
	public void internArray(String[] array) {
		for(int i = 0; i < array.length; i++) {
			array[i] = intern(array[i]);
		}
	}
	
	/**
	 * Mutates a map with string keys. After mutation, the map will have interned keys.
	 */
	public <T> void internMapKeys(Map<String, T> map) {
		//first, let's check if the map is mutable.
		//Obviously this doesn't test all aspects of mutating the map.
		//For most realistic collection types, this should be a good enough guess.
		String probe = UUID.randomUUID() + "-mutability-probe";
		try {
			map.remove(probe);
		} catch (Throwable e) {
			return;
		}
		
		//copy data out into a new map, interning keys as we go
		Map<String, T> internedCopy = new HashMap<>(map.size());
		map.forEach((k, v) -> internedCopy.put(intern(k), v));
		
		//copy the newly-interned values back into the original map
		//(here I use putAll in hopes that the new map will have a tigher capacity bound)
		map.clear();
		map.putAll(internedCopy);
	}
	
	/**
	 * Drops the cache of interned strings.
	 */
	@Override
	public void close() {
		strings.clear();
		strings = null;
	}
}

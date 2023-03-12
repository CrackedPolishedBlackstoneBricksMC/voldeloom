/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

/**
 * Utilities for hashing things, comparing hashes of files, etc
 */
public class Checksum {
	public static final Supplier<MessageDigest> SHA1 = () -> newMessageDigest("SHA-1");
	public static final Supplier<MessageDigest> SHA256 = () -> newMessageDigest("SHA-256");
	
	/**
	 * Compares the hash of the file with the known hash. Returns {@code true} if the file exists and the hash matches.
	 * @return {@code true} if the file exists <i>and</i> the hash matches, {@code false} if the file does not exist or if its hash is different
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted") //yeah i mean. you typically wanna do things when the hashes are *different*
	public static boolean compareFileHexHash(Path path, String knownDigest, MessageDigest alg) throws IOException {
		if(path == null || knownDigest == null || knownDigest.length() != (alg.getDigestLength() * 2) || Files.notExists(path)) return false;
		else return fileHexHash(path, alg).equalsIgnoreCase(knownDigest);
	}
	
	/**
	 * Runs the file through the specified hashing function, and returns the hash as a string in hexadecimal format.
	 */
	public static String fileHexHash(Path path, MessageDigest alg) throws IOException {
		return bytesHexHash(Files.readAllBytes(path), alg);
	}
	
	/**
	 * Runs the character string through the specified hashing function, and returns the hash as a string in hexadecimal format.
	 */
	public static String stringHexHash(String s, MessageDigest alg) {
		return bytesHexHash(s.getBytes(StandardCharsets.UTF_8), alg);
	}
	
	/**
	 * Runs the byte array through the specified hashing function, and returns the hash as a string in hexadecimal format.
	 */
	public static String bytesHexHash(byte[] bytes, MessageDigest alg) {
		alg.update(bytes);
		return toHexString(alg.digest());
	}
	
	/**
	 * Converts a byte array into hexadecimal form, the familiar format used to present hashes to people.
	 * The string will have twice as many characters as the byte array, so it covers the entire hash.
	 */
	public static String toHexString(byte[] data) {
		return toHexStringPrefix(data, data.length * 2);
	}
	
	/**
	 * Converts the start of a byte array into hexadecimal form, the familiar format used to present hashes to people.
	 * The string will have up-to as many characters as {@code prefixLength}, with one byte in the hash corresponding
	 * to two characters of the string. Subsequent bytes will be skipped.
	 */
	public static String toHexStringPrefix(byte[] data, int prefixLength) {
		StringBuilder out = new StringBuilder(prefixLength);
		for(byte b : data) {
			if(out.length() == prefixLength) return out.toString();
			
			int hi = (b & 0xF0) >>> 4;
			if(hi <= 9) out.append((char) ('0' + hi));
			else out.append((char) ('a' + hi - 10));
			
			if(out.length() == prefixLength) return out.toString();
			
			int lo = (b & 0x0F);
			if(lo <= 9) out.append((char) ('0' + lo));
			else out.append((char) ('a' + lo - 10));
		}
		return out.toString();
	}
	
	/**
	 * Helper for obtaining a {@code MessageDigest} instance without worrying about java's goofy ass checked exception
	 */
	public static MessageDigest newMessageDigest(String type) {
		try {
			return MessageDigest.getInstance(type);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}

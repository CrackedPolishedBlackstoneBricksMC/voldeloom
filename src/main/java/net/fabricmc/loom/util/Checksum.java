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

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilities for comparing file hashes.
 */
public class Checksum {
	private static final Logger log = Logging.getLogger(Checksum.class);

	@SuppressWarnings({
		"BooleanMethodIsAlwaysInverted", //yeah i mean. you typically wanna do things when the hashes are *different*
		"UnstableApiUsage", //Guava hashing
		"deprecation" //Not my fault Mojang uses SHA-1
	})
	public static boolean compareSha1(Path path, String knownDigest) {
		if(path == null || knownDigest == null || knownDigest.length() != 40) {
			return false;
		}
		
		try {
			if(Files.notExists(path)) return false;
			
			HashCode digest = MoreFiles.asByteSource(path).hash(Hashing.sha1());
			String digestString = String.format("%040x", new BigInteger(1, digest.asBytes()));
			
			log.info("Checksum check: '" + digestString + "' == '" + knownDigest + "'?");
			return digestString.equals(knownDigest);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
}

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class Checksum {
	private static final Logger log = Logging.getLogger(Checksum.class);

	@SuppressWarnings("BooleanMethodIsAlwaysInverted") //yeah i mean. you typically wanna do things when the hashes are different
	public static boolean compareSha1(Path path, String knownDigest) {
		if(path == null || knownDigest == null || knownDigest.length() != 40) {
			return false;
		}
		
		//TODO(VOLDELOOM-DISASTER) migrate to Path
		File file = path.toFile();
		
		try {
			//noinspection deprecation
			HashCode hash = MoreFiles.asByteSource(path).hash(Hashing.sha1());
			
			//Yes you have to use this fuckedup way of converting the hash byte array into a string
			//Everything else seemingly gets it wrong
			StringBuilder builder = new StringBuilder();
			for(Byte hashBytes : hash.asBytes()) {
				builder.append(Integer.toString((hashBytes & 0xFF) + 0x100, 16).substring(1));
			}
			
			log.info("Checksum check: '" + builder + "' == '" + knownDigest + "'?");
			return builder.toString().equals(knownDigest);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	//		try {
	//			if(!Files.exists(path)) {
	//				return false;
	//			}
	//			
	//			//compute SHA-1 of the file
	//			MessageDigest readersDigest = MessageDigest.getInstance("SHA-1");
	//			readersDigest.update(Files.readAllBytes(path));
	//			byte[] digest = readersDigest.digest();
	//			
	//			//stringify it for easier comparison lol
	//			String digestString = String.format("%040x", new BigInteger(1, digest));
	//			
	//			log.info("Checksum check: computed '" + digestString + "', known '" + knownDigest + "'");
	//			return digestString.equals(knownDigest);
	//		} catch (Exception e) {
	//			e.printStackTrace();
	//			return false;
	//		}
	//	}
}

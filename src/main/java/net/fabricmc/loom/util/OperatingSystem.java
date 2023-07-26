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

import java.util.Locale;

/**
 * Utilities for reading properties of the current operating system.
 */
public class OperatingSystem {
	public OperatingSystem(String shortName, int bitness, String architecture) {
		this.shortName = shortName;
		this.thinkDifferent = shortName.contains("osx");
		this.bitness = bitness;
		this.architecture = architecture;
		
		String prismArchitectureSuffix = architecture.startsWith("x86") ? "" : "-" + architecture;
		this.longName = shortName + prismArchitectureSuffix;
	}
	
	public static OperatingSystem CURRENT;
	
	/**
	 * "windows", "osx", or "linux", like mojang uses in version manifests
	 */
	public final String shortName;
	
	/**
	 * Whether the operating system was designed by apple in california
	 */
	public final boolean thinkDifferent;
	
	/**
	 * 64 or 32
	 */
	public final int bitness;
	
	/**
	 * arm64, arm32, x86_64, or x86
	 */
	public final String architecture;
	
	/**
	 * OS name *and* architecture (like "osx-arm64"), except x86 which is unsuffixed.
	 * Generally this is used by Prism launcher
	 */
	public final String longName;
	
	public boolean matches(String key) {
		return shortName.equals(key) || longName.equals(key);
	}
	
	@Override
	public String toString() {
		return String.format("os short name: '%s', long name: '%s', bitness: %d, arch: %s", shortName, longName, bitness, architecture);
	}
	
	static {
		//TODO include more funny architectures
		
		String shortName, architecture;
		int bitness;
		
		String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		String dataModel = System.getProperty("sun.arch.data.model").toLowerCase(Locale.ROOT); //Surely theres a nicer way to do this
		String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
		
		if(osName.contains("win")) shortName = "windows";
		else if(osName.contains("mac")) shortName = "osx";
		else shortName = "linux";
		
		if(dataModel.contains("64")) {
			bitness = 64;
			if(osArch.contains("aarch") || osArch.contains("arm")) architecture = "arm64";
			else architecture = "x86_64";
		} else {
			bitness = 32;
			if(osArch.contains("aarch") || osArch.contains("arm")) architecture = "arm32";
			else architecture = "x86";
		}
		
		CURRENT = new OperatingSystem(shortName, bitness, architecture);
		
		System.out.println("current " + CURRENT);
	}
}

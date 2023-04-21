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

package net.fabricmc.loom.task.fernflower;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Entry point for Forked FernFlower task.<br>
 * Takes one parameter, a single file, each line is treated as command line input.<br>
 * Forces one input file.<br>
 * Forces one output file using @{code -o=/path/to/output}.<br>
 * Created by covers1624 on 11/02/19.
 */
public class ForkedFFExecutor {
	public static void main(String[] args) throws IOException {
		System.out.println("\\-> ForkedFFExecutor starting. Parsing options...");
		
		Map<String, Object> options = new HashMap<>();
		String input = null;
		File output = null;
		File lineMap = null;
		List<File> libraries = new ArrayList<>();
		
		Function<String, IBytecodeProvider> bytecodeProviderProvider = FairlyUnsafeNioBytecodeProvider::new;

		boolean isFernflowerOption = true;

		for(String arg : args) {
			System.out.println("\\-> Got argument: " + arg);
			
			if(isFernflowerOption && arg.length() > 5 && arg.charAt(0) == '-' && arg.charAt(4) == '=') {
				//Standard fernflower option. These have to come first.
				options.put(arg.substring(1, 4), arg.substring(5));
			} else {
				//Custom ForkedFFExecutor option.
				isFernflowerOption = false;

				if(arg.startsWith("-library=")) {
					libraries.add(new File(arg.substring("-library=".length())));
				} else if (arg.startsWith("-output=")) {
					output = new File(arg.substring("-output=".length()));
				} else if (arg.startsWith("-linemap=")) {
					lineMap = new File(arg.substring("-linemap=".length()));
				} else if (arg.startsWith("-mcpmappings=")) {
					options.put(IFabricJavadocProvider.PROPERTY_NAME, new McpJavadocProvider(Paths.get(arg.substring("-mcpmappings=".length()))));
				} else if(arg.equals("-safer-bytecode-provider")) {
					bytecodeProviderProvider = (__) -> SAFER_BUT_SLOWER_BYTECODE_PROVIDER;
				} else if(arg.startsWith("-input=")){
					input = arg.substring("-input=".length());
				}
			}
		}

		Objects.requireNonNull(input, "Input not set.");
		Objects.requireNonNull(output, "Output not set.");
		
		System.out.println("\\-> Creating bytecode provider...");
		IBytecodeProvider provider = bytecodeProviderProvider.apply(input);
		
		try {
			System.out.println("\\-> Creating Fernflower...");
			Fernflower ff = new Fernflower(provider, new ThreadSafeResultSaver(output, lineMap), options, new PrintStreamLogger(System.out));
			
			System.out.println("\\-> Adding libraries...");
			for(File library : libraries) ff.addLibrary(library);
			
			System.out.println("\\-> Adding input...");
			ff.addSource(new File(input));
			
			System.out.println("\\-> Let's Decompiling");
			ff.decompileContext();
		} finally {
			System.out.println("\\-> Cleaning up...");
			if(provider instanceof Closeable) ((Closeable) provider).close();
		}
	}
	
	private static class FairlyUnsafeNioBytecodeProvider implements IBytecodeProvider, Closeable {
		public FairlyUnsafeNioBytecodeProvider(String expectedExternalPath) {
			System.out.println("\\-> [!] Using FairlyUnsafeNioBytecodeProvider");
			try {
				fs = FileSystems.newFileSystem(URI.create("jar:" + Paths.get(expectedExternalPath).toUri()), Collections.emptyMap());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
		private final FileSystem fs;
		
		@Override
		public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
			//This class is unsafe because:
			//1. It blindly assumes `externalPath` refers to the same file as the filesystem opened in the constructor.
			// (In practice, this is the case when the amount of input files provided to the decompiler is one.)
			//2. It blindly assumes `internalPath` is never null. The stock IBytecodeProviders check for this and open the entire file instead.
			//But hey it saves like 15 seconds on the fernflower runtime :sunglas
			return Files.readAllBytes(fs.getPath(internalPath));
		}
		
		@Override
		public void close() throws IOException {
			fs.close();
		}
	}
	
	//styled after the one in ConsoleDecompiler
	private static final IBytecodeProvider SAFER_BUT_SLOWER_BYTECODE_PROVIDER = (externalPath, internalPath) -> {
		File file = new File(externalPath);
		
		if (internalPath == null) {
			return InterpreterUtil.getBytes(file);
		} else {
			try (ZipFile archive = new ZipFile(file)) {
				ZipEntry entry = archive.getEntry(internalPath);
				
				if (entry == null) {
					throw new IOException("Entry not found: " + internalPath);
				}
				
				return InterpreterUtil.getBytes(archive, entry);
			}
		}
	};
}

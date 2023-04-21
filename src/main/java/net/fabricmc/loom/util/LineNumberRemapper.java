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

import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

//Fernflower linemap format.
//
//If the line starts with a non-tab character, this starts a new class definition. Split on tabs:
// element 0 is the class name (internal-name format)
// element 1 is the last line-number with code on it in the *input* jar.
// element 2 is the last line-number with code on it in fernflower's outputted sources.
//These numbers will be smaller than the number of lines in the file; a line with only a } on may or may not count.
//
//This will be followed by a zero or more lines that *do* start with a tab character. Each line has two tab-separated integers.
// element 0 is the line number in the input jar, and element 1 is the corresponding line number in the output jar.
//
//When performing a remap, it's not guaranteed that a line-number node you find in the file
//has a corresponding entry in the remap table. That's fine; advance forwards until one is found.
//(That's what the original implementation did anyway?)
//
//TODO: what to do about class names with $ in them? These are obviously Java inner classes.
// They're showing up in my IDE with dollar signs in them though... that's an unrelated problem to this file.
public class LineNumberRemapper {
	private final Map<String, RemapTable> tablesByInternalName = new HashMap<>();
	
	private static class RemapTable {
		public RemapTable(int lastLineSrc, int lastLineDst) {
			this.lastLineSrc = lastLineSrc;
			this.lastLineDst = lastLineDst;
		}
		
		private final int lastLineSrc;
		private final int lastLineDst;
		private final NavigableMap<Integer, Integer> lineMap = new TreeMap<>();
		private final Map<Integer, Integer> dstToSrcUsedMappings = new HashMap<>(); //for debugging
	}
	
	public LineNumberRemapper readMappings(Path lineMappings) throws IOException {
		RemapTable currentTable = null;
		
		for(String line : Files.readAllLines(lineMappings)) {
			if(line.isEmpty()) continue;
			
			String[] split = line.trim().split("\t");
			if(line.charAt(0) == '\t') {
				if(currentTable != null) {
					//Adding to an existing entry.
					currentTable.lineMap.put(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
				}
			} else {
				//Creating a new entry.
				currentTable = new RemapTable(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
				tablesByInternalName.put(split[0], currentTable);
			}
		}
		
		return this;
	}

	public void process(FileSystem srcFs, FileSystem dstFs, Logger logger) throws Exception {
		Files.walkFileTree(srcFs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path srcDir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(dstFs.getPath(srcDir.toString()));
				return FileVisitResult.CONTINUE;
			}
			
			@Override
			public FileVisitResult visitFile(Path srcPath, BasicFileAttributes attrs) throws IOException {
				String pathString = srcPath.toString();
				Path dstPath = dstFs.getPath(pathString);
				
				if(pathString.endsWith(".class")) {
					RemapTable table = tablesByInternalName.get(
						//guess the class name from the filename
						pathString
							.replace("\\", "/")    //maybe windows does this idk?
							.substring(1)          //leading slash
							.replace(".class", "") //funny extension
					);
					
					if(table != null) {
						//we have a line-number remap table for this class, perform a line remap.
						try(InputStream srcReader = new BufferedInputStream((Files.newInputStream(srcPath)))) {
							ClassReader srcClassReader = new ClassReader(srcReader);
							ClassWriter dstClassWriter = new ClassWriter(0);
							
							srcClassReader.accept(new LineNumberVisitor(Opcodes.ASM9, dstClassWriter, table), 0);
							
							Files.write(dstPath, dstClassWriter.toByteArray());
							return FileVisitResult.CONTINUE;
						}
					}
				}
				
				//file is not a class, or we don't have a table for this class
				Files.copy(srcPath, dstPath);
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	public void processDebug(FileSystem sourcesFs, FileSystem processedSourcesFs) throws Exception {
		Files.walkFileTree(sourcesFs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path sourcesPath, BasicFileAttributes attrs) throws IOException {
				String pathString = sourcesPath.toString();
				Path processedSourcesPath = processedSourcesFs.getPath(pathString);
				
				if(pathString.endsWith(".java")) {
					//Found a java source file. Let's see if we have a remap table for its corresponding class file...
					RemapTable table = tablesByInternalName.get(
						pathString
							.replace("\\", "/")   //maybe windows does this idk?
							.substring(1)         //leading slash
							.replace(".java", "") //funny extension
					);
					
					if(table != null) {
						//We do have a table!
						//Process each line of source code, prefixing it with the line number it corresponded to in Mojang's sources.
						
						//First, we want the prefixes to be the same length so the sources don't come out jagged.
						int lengthOfLongestNumberWritten = table.dstToSrcUsedMappings.values().stream()
							.map(i -> Integer.toString(i).length()) //we have log10 at home
							.max(Integer::compareTo)
							.orElse(1);
						String spaces = String.join("", Collections.nCopies(lengthOfLongestNumberWritten + 7, " ")); //we have String.repeat at home
						String fmt = "/* %" + lengthOfLongestNumberWritten + "d */ "; //the %3d fmt argument left-pads a number to 3 characters using spaces
						
						//Now process it line-by-line.
						List<String> sources = Files.readAllLines(sourcesPath);
						List<String> processedSources = new ArrayList<>();
						
						for(int i = 0; i < sources.size(); i++) {
							String sourceLine = sources.get(i);
							
							Integer mojangsLineNumber = table.dstToSrcUsedMappings.get(i + 1); //line numbers are one-indexed
							if(mojangsLineNumber == null) processedSources.add(spaces + sourceLine);
							else processedSources.add(String.format(fmt, mojangsLineNumber) + sourceLine);
						}
						
						if(processedSourcesPath.getParent() != null) Files.createDirectories(processedSourcesPath.getParent());
						Files.write(processedSourcesPath, processedSources);
						return FileVisitResult.CONTINUE;
					}
				}
				
				Files.copy(sourcesPath, processedSourcesPath);
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	private static class LineNumberVisitor extends ClassVisitor {
		private LineNumberVisitor(int api, ClassVisitor classVisitor, RemapTable remapTable) {
			super(api, classVisitor);
			this.remapTable = remapTable;
		}
		
		private final RemapTable remapTable;
		
		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
				@Override
				public void visitLineNumber(int srcLine, Label start) {
					//Sometimes classes that don't contain any source code (lastLineSrc == 0) still have synthetic methods.
					//Picture Enum#values()). In these cases, *usually* the original line number in the LNT points to *something*
					//interesting, like maybe the definition of the enum itself. Can't hurt to pass it through.
					if(remapTable.lastLineSrc == 0) {
						remapTable.dstToSrcUsedMappings.put(srcLine, srcLine);
						super.visitLineNumber(srcLine, start);
						return;
					}
					
					//Magic algorithm:
					Integer nextSrcLineBoxed = remapTable.lineMap.ceilingKey(srcLine);
					int nextSrcLine = nextSrcLineBoxed == null ? -1 : nextSrcLineBoxed;
					int dstLine = remapTable.lineMap.getOrDefault(nextSrcLine, remapTable.lastLineDst);
					remapTable.dstToSrcUsedMappings.put(dstLine, srcLine);
					super.visitLineNumber(dstLine, start);
				}
			};
		}
	}
}

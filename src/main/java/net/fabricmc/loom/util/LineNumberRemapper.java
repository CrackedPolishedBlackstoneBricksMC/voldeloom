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
import java.util.HashMap;
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
	}
	
	public LineNumberRemapper readMappings(Path lineMappings) throws IOException {
		RemapTable currentRemapTable = null;
		
		for(String line : Files.readAllLines(lineMappings)) {
			if(line.isEmpty()) continue;
			
			String[] split = line.trim().split("\t");
			if(line.charAt(0) == '\t') {
				if(currentRemapTable != null) {
					//Adding to an existing entry.
					currentRemapTable.lineMap.put(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
				}
			} else {
				//Creating a new entry.
				currentRemapTable = new RemapTable(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
				tablesByInternalName.put(split[0], currentRemapTable);
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
				Path dstPath = dstFs.getPath(srcPath.toString());
				
				if(dstPath.toString().endsWith(".class")) {
					//kludgily glean the class name from the filename:
					String className = srcPath.toString()
						.replace("\\", "/")     //maybe windows does this idk?
						.substring(1)           //leading slash
						.replace(".class", ""); //funny extension
					
					//see if we have a line-number remap table for this class
					RemapTable table = tablesByInternalName.get(className);
					if(table == null) {
						Files.copy(srcPath, dstPath);
						return FileVisitResult.CONTINUE;
					}
					
					//we do - perform a line remap.
					try(InputStream srcReader = new BufferedInputStream((Files.newInputStream(srcPath)))) {
						ClassReader srcClassReader = new ClassReader(srcReader);
						ClassWriter dstClassWriter = new ClassWriter(0);
						srcClassReader.accept(new LineNumberVisitor(Opcodes.ASM9, dstClassWriter, table), 0);
						Files.write(dstPath, dstClassWriter.toByteArray());
					}
				} else {
					Files.copy(srcPath, dstPath);
				}
				
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	private static class LineNumberVisitor extends ClassVisitor {
		LineNumberVisitor(int api, ClassVisitor classVisitor, RemapTable remapTable) {
			super(api, classVisitor);
			this.remapTable = remapTable;
		}
		
		private final RemapTable remapTable;
		
		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
				@Override
				public void visitLineNumber(int srcLine, Label start) {
					//Sometimes classes that don't contain any source code still have synthetic methods (imagine Enum#values()).
					//In these cases, *usually* the original line number in the LNT points to something interesting, like
					//the definition of the enum itself. Can't hurt to pass it through.
					if(remapTable.lastLineSrc == 0) {
						super.visitLineNumber(srcLine, start);
						return;
					}
					
					if(srcLine > remapTable.lastLineSrc) {
						//System.err.println("overly large line (line " + srcLine + " of " + remapTable.lastLineSrc + " in file), in " + who + "." + name + descriptor);
						//System.err.println("(putting it at " + remapTable.lastLineDst + ")");
						super.visitLineNumber(remapTable.lastLineDst, start);
						return;
					}
					
					//If we can't find an entry in the table for this line number, find the next-highest one.
					//This is what original Loom did, I'm not sure why this algorithm was chosen... seems to work okay...?
					Integer nextSrcLine = remapTable.lineMap.ceilingKey(srcLine); // returns the lowest key that's >= the argument
					if(nextSrcLine == null) {
						//Fell off the end of the table. I haven't identified if there's a pattern where this happens exactly, but it seems to
						//always happen with the last line of the last method anyways; it should be fine to bring this to the last codebearing line.
						//System.err.println("fell off end of navigablemap: src line " + srcLine + " in " + who + "." + name + descriptor);
						//System.err.println("(putting it at " + remapTable.lastLineDst + ")");
						super.visitLineNumber(remapTable.lastLineDst, start);
						return;
					} else if(nextSrcLine != srcLine) {
						//System.err.println("rounded up from line " + srcLine + " to line " + nextSrcLine + " in " + who + "." + name + descriptor);
						//System.err.println("(putting it at " + remapTable.lineMap.get(nextSrcLine) + ")");
					} else {
						//Exact hit.
					}
					
					super.visitLineNumber(remapTable.lineMap.get(nextSrcLine), start);
				}
			};
		}
	}
}

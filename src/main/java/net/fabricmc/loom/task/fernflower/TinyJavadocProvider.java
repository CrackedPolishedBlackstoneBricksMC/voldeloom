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
import net.fabricmc.loom.Constants;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappings.EntryTriple;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TinyJavadocProvider implements IFabricJavadocProvider {
	private final Map<String, String> classComments = new HashMap<>();
	private final Map<EntryTriple, String> fieldComments = new HashMap<>();
	private final Map<EntryTriple, MethodDef> methods = new HashMap<>();

	private final String namespace = Constants.MAPPED_NAMING_SCHEME;

	public TinyJavadocProvider(Path tinyFile) {
		TinyTree mappings;
		try (BufferedReader reader = Files.newBufferedReader(tinyFile)) {
			mappings = TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			//not really a show stopper, just means you won't get comments; everything else in fernflower works fine 
			System.err.println("(TinyJavadocProvider) Failed to read mappings");
			e.printStackTrace(System.err);
			return;
		}

		for(ClassDef classDef : mappings.getClasses()) {
			String className = classDef.getName(namespace);
			
			if(classDef.getComment() != null) classComments.put(className, classDef.getComment());
			
			for(FieldDef fieldDef : classDef.getFields()) {
				//Using empty string for field descriptor (MCP data doesn't have them -> McpTinyv2Writer writes junk to the tinyfile field descriptor slot)
				if(fieldDef.getComment() != null) fieldComments.put(new EntryTriple(className, fieldDef.getName(namespace), ""), fieldDef.getComment());
			}

			for(MethodDef methodDef : classDef.getMethods()) {
				methods.put(new EntryTriple(className, methodDef.getName(namespace), methodDef.getDescriptor(namespace)), methodDef);
			}
		}
	}

	@Override
	public String getClassDoc(StructClass structClass) {
		return classComments.get(structClass.qualifiedName);
	}

	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		//Using empty string for field descriptor
		return fieldComments.get(new EntryTriple(structClass.qualifiedName, structField.getName(), ""));
	}

	@Override
	public String getMethodDoc(StructClass structClass, StructMethod structMethod) {
		MethodDef methodDef = methods.get(new EntryTriple(structClass.qualifiedName, structMethod.getName(), structMethod.getDescriptor()));

		if (methodDef != null) {
			List<String> parts = new ArrayList<>();

			if (methodDef.getComment() != null) {
				parts.add(methodDef.getComment());
			}

			boolean addedParam = false;

			for (ParameterDef param : methodDef.getParameters()) {
				String comment = param.getComment();

				if (comment != null) {
					if (!addedParam && methodDef.getComment() != null) {
						//Add a blank line before params when the method has a comment
						parts.add("");
						addedParam = true;
					}

					parts.add(String.format("@param %s %s", param.getName(namespace), comment));
				}
			}

			if (parts.isEmpty()) {
				return null;
			}

			return String.join("\n", parts);
		}

		return null;
	}
}

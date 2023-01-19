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

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.LocalVariableDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;

/**
 * Utilities for bridging tiny-mappings-parser and tiny-remapper data structures.
 * <p>
 * Also (TODO) currently the home of about ten kludges.
 */
public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() { }

	private static IMappingProvider.Member memberOf(String className, String memberName, String descriptor) {
		return new IMappingProvider.Member(className, memberName, descriptor);
	}

	public static IMappingProvider create(TinyTree mappings, String from, String to, boolean remapLocalVariables) {
		return (acceptor) -> {
			for (ClassDef classDef : mappings.getClasses()) {
				String className = classDef.getName(from);
				
				//ThreadDownloadResources, ThreadDownloadImageData TODO HACK for Ears
				//Basically ThreadDownloadResources is actually an anonymous class in minecraft proper. Proguard stripped the inner class part,
				//so it's private in remapped mc jars. Ears uses `Class.forName("bas")` instead of a class literal because of the privateness.
				//This worked during Ears dev, because at the time 1.4 dev environments didn't exist, and the mod was tested by always compiling and remapping to proguard
				//But oops, some nutjob is coming along and inventing 1.4 dev environments. What an idiot
				//If I go ahead and forcibly unmap this class, the release Ears binary remapped to a named workspace does work.
				//Need this hack because remapping can't see into string literals passed into Class.forName (and i dont think it's a good idea to let it)
				if(className.equals("bas") || className.equals("bar")) continue;
				
				acceptor.acceptClass(className, classDef.getName(to).replace("net/minecraft/src/Block$1", "net/minecraft/block/Block$1")); //TODO(VOLDELOOM-DISASTER) HACK fix for some bug in the mcp mappings parser i think

				for (FieldDef field : classDef.getFields()) {
					acceptor.acceptField(memberOf(className, field.getName(from), field.getDescriptor(from)), field.getName(to));
				}

				for (MethodDef method : classDef.getMethods()) {
					IMappingProvider.Member methodIdentifier = memberOf(className, method.getName(from), method.getDescriptor(from));
					acceptor.acceptMethod(methodIdentifier, method.getName(to));

					if (remapLocalVariables) {
						for (ParameterDef parameter : method.getParameters()) {
							acceptor.acceptMethodArg(methodIdentifier, parameter.getLocalVariableIndex(), parameter.getName(to));
						}

						for (LocalVariableDef localVariable : method.getLocalVariables()) {
							acceptor.acceptMethodVar(methodIdentifier, localVariable.getLocalVariableIndex(),
											localVariable.getLocalVariableStartOffset(), localVariable.getLocalVariableTableIndex(),
											localVariable.getName(to));
						}
					}
				}
			}
		};
	}
}

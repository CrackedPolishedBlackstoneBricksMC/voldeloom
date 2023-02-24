/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loom.yoinked.stitch;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * This is a modified copy of Stitch's ClassMerger.
 *
 * Mainly I modified it to allow changing the annotatation types JarMerger adds to the jars, and to avoid adding
 * duplicate copies of those annotations when one already exists (Forge binpatches add annotations sometimes)
 */
public class ClassMergerCooler {
	private String sideEnum = "Lnet/fabricmc/api/EnvType;";
	private String sidedInterfaceAnnotation = "Lnet/fabricmc/api/EnvironmentInterface;";
	private String sidedInterfaceListAnnotation = "Lnet/fabricmc/api/EnvironmentInterfaces;";
	private String sideDescriptorAnnotation = "Lnet/fabricmc/api/Environment;";
	
	public ClassMergerCooler sideEnum(String sideEnum) {
		this.sideEnum = sideEnum;
		return this;
	}
	
	public ClassMergerCooler sidedInterfaceAnnotation(String sidedInterfaceAnnotation) {
		this.sidedInterfaceAnnotation = sidedInterfaceAnnotation;
		return this;
	}
	
	public ClassMergerCooler sidedInterfaceListAnnotation(String sidedInterfaceListAnnotation) {
		this.sidedInterfaceListAnnotation = sidedInterfaceListAnnotation;
		return this;
	}
	
	public ClassMergerCooler sideDescriptorAnnotation(String sideDescriptorAnnotation) {
		this.sideDescriptorAnnotation = sideDescriptorAnnotation;
		return this;
	}
	
	private abstract static class Merger<T> {
		private final Map<String, T> entriesClient, entriesServer;
		private final List<String> entryNames;
		
		public Merger(List<T> entriesClient, List<T> entriesServer) {
			this.entriesClient = new LinkedHashMap<>();
			this.entriesServer = new LinkedHashMap<>();
			
			List<String> listClient = toMap(entriesClient, this.entriesClient);
			List<String> listServer = toMap(entriesServer, this.entriesServer);
			
			this.entryNames = mergePreserveOrder(listClient, listServer);
		}
		
		public abstract String getName(T entry);
		public abstract void applySide(T entry, String side);
		
		private List<String> toMap(List<T> entries, Map<String, T> map) {
			List<String> list = new ArrayList<>(entries.size());
			for (T entry : entries) {
				String name = getName(entry);
				map.put(name, entry);
				list.add(name);
			}
			return list;
		}
		
		public void merge(List<T> list) {
			for (String s : entryNames) {
				T entryClient = entriesClient.get(s);
				T entryServer = entriesServer.get(s);
				
				if (entryClient != null && entryServer != null) {
					list.add(entryClient);
				} else if (entryClient != null) {
					applySide(entryClient, "CLIENT");
					list.add(entryClient);
				} else {
					applySide(entryServer, "SERVER");
					list.add(entryServer);
				}
			}
		}
	}
	
	private void visitSideAnnotation(AnnotationVisitor av, String side) {
		av.visitEnum("value", sideEnum, side.toUpperCase(Locale.ROOT));
		av.visitEnd();
	}
	
	private void visitItfAnnotation(AnnotationVisitor av, String side, List<String> itfDescriptors) {
		for (String itf : itfDescriptors) {
			AnnotationVisitor avItf = av.visitAnnotation(null, sidedInterfaceAnnotation);
			avItf.visitEnum("value", sideEnum, side.toUpperCase(Locale.ROOT));
			avItf.visit("itf", Type.getType("L" + itf + ";"));
			avItf.visitEnd();
		}
	}
	
	public class SidedClassVisitor extends ClassVisitor {
		private final String side;
		private boolean alreadyHasOne = false;
		
		public SidedClassVisitor(int api, ClassVisitor cv, String side) {
			super(api, cv);
			this.side = side;
		}
		
		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			//avoid adding duplicate annotations where a forge binpatch already added one
			if(descriptor.equals(sideDescriptorAnnotation)) alreadyHasOne = true;
			
			return super.visitAnnotation(descriptor, visible);
		}
		
		@Override
		public void visitEnd() {
			if(!alreadyHasOne) {
				AnnotationVisitor av = cv.visitAnnotation(sideDescriptorAnnotation, true);
				visitSideAnnotation(av, side);
			}
			super.visitEnd();
		}
	}
	
	public byte[] merge(byte[] classClient, byte[] classServer) {
		ClassReader readerC = new ClassReader(classClient);
		ClassReader readerS = new ClassReader(classServer);
		ClassWriter writer = new ClassWriter(0);
		
		ClassNode nodeC = new ClassNode(Opcodes.ASM9);
		readerC.accept(nodeC, 0);
		
		ClassNode nodeS = new ClassNode(Opcodes.ASM9);
		readerS.accept(nodeS, 0);
		
		ClassNode nodeOut = new ClassNode(Opcodes.ASM9);
		nodeOut.version = nodeC.version;
		nodeOut.access = nodeC.access;
		nodeOut.name = nodeC.name;
		nodeOut.signature = nodeC.signature;
		nodeOut.superName = nodeC.superName;
		nodeOut.sourceFile = nodeC.sourceFile;
		nodeOut.sourceDebug = nodeC.sourceDebug;
		nodeOut.outerClass = nodeC.outerClass;
		nodeOut.outerMethod = nodeC.outerMethod;
		nodeOut.outerMethodDesc = nodeC.outerMethodDesc;
		nodeOut.module = nodeC.module;
		nodeOut.nestHostClass = nodeC.nestHostClass;
		nodeOut.nestMembers = nodeC.nestMembers;
		nodeOut.attrs = nodeC.attrs;
		
		if (nodeC.invisibleAnnotations != null) {
			nodeOut.invisibleAnnotations = new ArrayList<>();
			nodeOut.invisibleAnnotations.addAll(nodeC.invisibleAnnotations);
		}
		if (nodeC.invisibleTypeAnnotations != null) {
			nodeOut.invisibleTypeAnnotations = new ArrayList<>();
			nodeOut.invisibleTypeAnnotations.addAll(nodeC.invisibleTypeAnnotations);
		}
		if (nodeC.visibleAnnotations != null) {
			nodeOut.visibleAnnotations = new ArrayList<>();
			nodeOut.visibleAnnotations.addAll(nodeC.visibleAnnotations);
		}
		if (nodeC.visibleTypeAnnotations != null) {
			nodeOut.visibleTypeAnnotations = new ArrayList<>();
			nodeOut.visibleTypeAnnotations.addAll(nodeC.visibleTypeAnnotations);
		}
		
		List<String> itfs = mergePreserveOrder(nodeC.interfaces, nodeS.interfaces);
		nodeOut.interfaces = new ArrayList<>();
		
		List<String> clientItfs = new ArrayList<>();
		List<String> serverItfs = new ArrayList<>();
		
		for (String s : itfs) {
			boolean nc = nodeC.interfaces.contains(s);
			boolean ns = nodeS.interfaces.contains(s);
			nodeOut.interfaces.add(s);
			if (nc && !ns) {
				clientItfs.add(s);
			} else if (ns && !nc) {
				serverItfs.add(s);
			}
		}
		
		if (!clientItfs.isEmpty() || !serverItfs.isEmpty()) {
			AnnotationVisitor envInterfaces = nodeOut.visitAnnotation(sidedInterfaceListAnnotation, false);
			AnnotationVisitor eiArray = envInterfaces.visitArray("value");
			
			if (!clientItfs.isEmpty()) {
				visitItfAnnotation(eiArray, "CLIENT", clientItfs);
			}
			if (!serverItfs.isEmpty()) {
				visitItfAnnotation(eiArray, "SERVER", serverItfs);
			}
			eiArray.visitEnd();
			envInterfaces.visitEnd();
		}
		
		new Merger<InnerClassNode>(nodeC.innerClasses, nodeS.innerClasses) {
			@Override
			public String getName(InnerClassNode entry) {
				return entry.name;
			}
			
			@Override
			public void applySide(InnerClassNode entry, String side) {
			}
		}.merge(nodeOut.innerClasses);
		
		new Merger<FieldNode>(nodeC.fields, nodeS.fields) {
			@Override
			public String getName(FieldNode entry) {
				return entry.name + ";;" + entry.desc;
			}
			
			@Override
			public void applySide(FieldNode entry, String side) {
				//avoid adding duplicate annotations where a forge binpatch already added one
				if(entry.visibleAnnotations == null || entry.visibleAnnotations.stream().noneMatch(a -> a.desc.equals(sideDescriptorAnnotation))) {
					AnnotationVisitor av = entry.visitAnnotation(sideDescriptorAnnotation, false);
					visitSideAnnotation(av, side);
				}
			}
		}.merge(nodeOut.fields);
		
		new Merger<MethodNode>(nodeC.methods, nodeS.methods) {
			@Override
			public String getName(MethodNode entry) {
				return entry.name + entry.desc;
			}
			
			@Override
			public void applySide(MethodNode entry, String side) {
				//avoid adding duplicate annotations where a forge binpatch already added one
				if(entry.visibleAnnotations == null || entry.visibleAnnotations.stream().noneMatch(a -> a.desc.equals(sideDescriptorAnnotation))) {
					AnnotationVisitor av = entry.visitAnnotation(sideDescriptorAnnotation, false);
					visitSideAnnotation(av, side);
				}
			}
		}.merge(nodeOut.methods);
		
		nodeOut.accept(writer);
		return writer.toByteArray();
	}
	
	public static List<String> mergePreserveOrder(List<String> first, List<String> second) {
		List<String> out = new ArrayList<>();
		int i = 0;
		int j = 0;
		
		while (i < first.size() || j < second.size()) {
			while (i < first.size() && j < second.size()
				&& first.get(i).equals(second.get(j))) {
				out.add(first.get(i));
				i++;
				j++;
			}
			
			while (i < first.size() && !second.contains(first.get(i))) {
				out.add(first.get(i));
				i++;
			}
			
			while (j < second.size() && !first.contains(second.get(j))) {
				out.add(second.get(j));
				j++;
			}
		}
		
		return out;
	}
}

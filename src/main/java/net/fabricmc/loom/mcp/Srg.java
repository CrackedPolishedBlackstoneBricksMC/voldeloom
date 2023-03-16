package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.tinyremapper.IMappingProvider;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parser for .srg-format files, like joined.srg. These map classes to named classes, and fields/methods to SRG names.
 * 
 * Shouldn't break on the 1.6-format SRGs with the #c and #s tags.
 */
public class Srg {
	public Srg() {
		classMappings = new LinkedHashMap<>();
		fieldMappingsByOwningClass = new LinkedHashMap<>();
		methodMappingsByOwningClass = new LinkedHashMap<>();
	}
	
	public Srg(Map<String, String> classMappings, Map<String, Map<String, String>> fieldMappingsByOwningClass, Map<String, Map<MethodEntry, MethodEntry>> methodMappingsByOwningClass) {
		this.classMappings = classMappings;
		this.fieldMappingsByOwningClass = fieldMappingsByOwningClass;
		this.methodMappingsByOwningClass = methodMappingsByOwningClass;
	}
	
	public final Map<String, String> classMappings;
	public final Map<String, Map<String, String>> fieldMappingsByOwningClass;
	public final Map<String, Map<MethodEntry, MethodEntry>> methodMappingsByOwningClass;
	
	public Srg read(Path path, StringInterner mem) throws IOException {
		List<String> lines = Files.readAllLines(path);
		for(int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if(line.isEmpty()) continue;
			int lineNo = i + 1;
			
			String[] split = line.split(" ");
			if(split.length < 3) {
				System.err.println("line " + lineNo + " is too short: " + line);
				continue;
			}
			
			if("CL:".equals(split[0])) {
				//Example class line:
				// CL: abk net/minecraft/src/WorldGenDeadBush
				classMappings.put(mem.intern(split[1]), mem.intern(split[2]));
			} else if("FD:".equals(split[0])) {
				//Example field line:
				// FD: abk/a net/minecraft/src/WorldGenDeadBush/field_76516_a
				//
				//In fields 1 and 2, the name of the field is attached to the owning class with a `/`.
				int fstSlash = split[1].lastIndexOf('/');
				String fromOwningClass = split[1].substring(0, fstSlash);
				String fromName = split[1].substring(fstSlash + 1);
				
				int sndSlash = split[2].lastIndexOf('/');
				String toName = split[2].substring(sndSlash + 1);
				
				fieldMappingsByOwningClass.computeIfAbsent(fromOwningClass, __ -> new LinkedHashMap<>())
					.put(mem.intern(fromName), mem.intern(toName));
			} else if("MD:".equals(split[0])) {
				//Example method line:
				// MD: acn/b (Lacn;)V net/minecraft/src/StructureBoundingBox/func_78888_b (Lnet/minecraft/src/StructureBoundingBox;)V
				//
				//In fields 1 and 3, the name of the method is also attached to the owning class with a `/`.
				//Fields 2 and 4 are the descriptors of the method. Field 4 is simply a conveniently-remapped version of field 2.
				//(note/todo: field 4 does not exist in the TSRG format. i dont really use it anyway though)
				if(split.length < 5) {
					System.err.println("line " + lineNo + " is too short for method descriptor: " + line);
					continue;
				}
				
				int fstSlash = split[1].lastIndexOf('/');
				String fromOwningClass = split[1].substring(0, fstSlash);
				String fromName = split[1].substring(fstSlash + 1);
				String fromDesc = split[2];
				
				int trdSlash = split[3].lastIndexOf('/');
				String toName = split[3].substring(trdSlash + 1);
				String toDesc = split[4];
				
				//TODO: KLUDGE for 1.6.4, need to debug. Naming conflicts. May be less of an issue after switching off tiny-remapper?
				// (This is accurate to the actual contents of the SRG, btw, there are duplicates)
				if(toName.equals("func_130000_a") && fromDesc.equals("(Lof;DDDFF)V")) continue;
				else if(toName.equals("func_82408_c") && fromDesc.equals("(Lof;IF)V")) continue;
				//TODO: KLUDGE for 1.2.5 client
				else if(toName.equals("func_319_i") && fromDesc.equals("(Lxd;III)V")) continue;
				else if(toName.equals("func_35199_b") && fromDesc.equals("(Laan;I)V")) continue;
				
				methodMappingsByOwningClass.computeIfAbsent(fromOwningClass, __ -> new LinkedHashMap<>())
					.put(new MethodEntry(mem.intern(fromName), mem.intern(fromDesc)), new MethodEntry(mem.intern(toName), mem.intern(toDesc)));
			} else if(!"PK:".equals(split[0])) { //We acknowledge PK lines but they're useless to us, more to do with the source-based toolchain i think
				System.err.println("line " + lineNo + " has unknown type (not CL/FD/MD/PK): " + line);
			}
		}
		
		return this;
	}
	
	public void writeTo(Path path) throws IOException {
		try(
			OutputStream o = new BufferedOutputStream(Files.newOutputStream(path));
			OutputStreamWriter w = new OutputStreamWriter(o)
		) {
			write(w);
			o.flush();
		}
	}
	
	public void write(OutputStreamWriter o) throws IOException {
		for(Map.Entry<String, String> classMapping : classMappings.entrySet()) {
			o.write("CL: ");
			o.write(classMapping.getKey());
			o.write(' ');
			o.write(classMapping.getValue());
			o.write('\n');
		}
		
		for(Map.Entry<String, Map<String, String>> bleh : fieldMappingsByOwningClass.entrySet()) {
			String owningClass = bleh.getKey();
			String mappedOwningClass = classMappings.getOrDefault(owningClass, owningClass);
			for(Map.Entry<String, String> fieldMapping : bleh.getValue().entrySet()) {
				o.write("FD: ");
				o.write(owningClass); o.write('/'); o.write(fieldMapping.getKey());
				o.write(' ');
				o.write(mappedOwningClass); o.write('/'); o.write(fieldMapping.getValue());
				o.write('\n');
			}
		}
		
		for(Map.Entry<String, Map<MethodEntry, MethodEntry>> bleh : methodMappingsByOwningClass.entrySet()) {
			String owningClass = bleh.getKey();
			String mappedOwningClass = classMappings.getOrDefault(owningClass, owningClass);
			for(Map.Entry<MethodEntry, MethodEntry> methodMapping : bleh.getValue().entrySet()) {
				o.write("MD: ");
				o.write(owningClass); o.write('/'); o.write(methodMapping.getKey().name); o.write(' '); o.write(methodMapping.getKey().descriptor);
				o.write(' ');
				o.write(mappedOwningClass); o.write('/'); o.write(methodMapping.getValue().name); o.write(' '); o.write(methodMapping.getValue().descriptor);
				o.write('\n');
			}
		}
	}
	
	public boolean isEmpty() {
		//strictly speaking, mappings would be considered nonempty if a fieldMappingsByOwningClass entry existed, even if it points to an empty collection?
		//my advice for that situation: don't do that
		return classMappings.isEmpty() && fieldMappingsByOwningClass.isEmpty() && methodMappingsByOwningClass.isEmpty();
	}
	
	public IMappingProvider toMappingProvider() {
		return acceptor -> {
			classMappings.forEach(acceptor::acceptClass);
			
			fieldMappingsByOwningClass.forEach((owningClass, fieldMappings) ->
				fieldMappings.forEach((oldName, newName) ->
					//make up a field desc; tiny-remapper is put into a mode that ignores field descriptors
					acceptor.acceptField(new IMappingProvider.Member(owningClass, oldName, "Ljava/lang/Void;"), newName)));
			
			methodMappingsByOwningClass.forEach((owningClass, methodMappings) ->
				methodMappings.forEach((oldMethod, newMethod) ->
					acceptor.acceptMethod(new IMappingProvider.Member(owningClass, oldMethod.name, oldMethod.descriptor), newMethod.name)));
		};
	}
	
	//TODO: if a class is proguarded, does it never need to be repackaged. that would eliminate a lot of the annoying stuff lol
	public Srg repackage(Packages packages) {
		StringInterner mem = new StringInterner();
		
		Map<String, String> repackagedClasses = new LinkedHashMap<>();
		classMappings.forEach((prg, srg) -> repackagedClasses.put(
			mem.intern(packages.repackage(prg)),
			mem.intern(packages.repackage(srg))
		));
		
		Map<String, Map<String, String>> repackagedFields = new LinkedHashMap<>();
		fieldMappingsByOwningClass.forEach((prg, fields) -> repackagedFields.put(
			mem.intern(packages.repackage(prg)),
			fields
		));
		
		Map<String, Map<MethodEntry, MethodEntry>> repackagedMethods = new LinkedHashMap<>();
		methodMappingsByOwningClass.forEach((prg, methods) -> {
			Map<MethodEntry, MethodEntry> newMethods = new LinkedHashMap<>();
			methods.forEach((prgM, srgM) -> newMethods.put(
				prgM.repackage(packages, mem),
				srgM.repackage(packages, mem)
			));
			
			repackagedMethods.put(mem.intern(packages.repackageDescriptor(prg)), newMethods);
		});
		
		return new Srg(repackagedClasses, repackagedFields, repackagedMethods);
	}
	
	public void unmapClass(String classs) {
		classMappings.remove(classs);
		fieldMappingsByOwningClass.remove(classs);
		methodMappingsByOwningClass.remove(classs);
	}
	
	public static class MethodEntry {
		public MethodEntry(String name, String descriptor) {
			this.name = name;
			this.descriptor = descriptor;
		}
		
		public final String name;
		public final String descriptor;
		
		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			MethodEntry that = (MethodEntry) o;
			return name.equals(that.name) && descriptor.equals(that.descriptor);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(name, descriptor);
		}
		
		@Override
		public String toString() {
			return name + " " + descriptor;
		}
		
		public MethodEntry repackage(Packages packages, StringInterner mem) {
			return new MethodEntry(name, mem.intern(packages.repackageDescriptor(descriptor)));
		}
	}
}

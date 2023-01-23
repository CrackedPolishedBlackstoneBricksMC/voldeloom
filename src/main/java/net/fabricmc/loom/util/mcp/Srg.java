package net.fabricmc.loom.util.mcp;

import java.io.IOException;
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
	public final Map<String, String> classMappings = new LinkedHashMap<>();
	public final Map<String, Map<FieldEntry, FieldEntry>> fieldMappingsByOwningClass = new LinkedHashMap<>();
	public final Map<String, Map<MethodEntry, MethodEntry>> methodMappingsByOwningClass = new LinkedHashMap<>();
	
	public Srg read(Path path) throws IOException {
		List<String> lines = Files.readAllLines(path);
		for(int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if(line.isEmpty()) continue;
			int lineNo = i + 1;
			
			String[] split = line.split(" ");
			if(split.length < 3) {
				System.err.println("line " + lineNo + " is too short: " + line);
			} else if("CL:".equals(split[0])) {
				//Example class line:
				// CL: abk net/minecraft/src/WorldGenDeadBush
				classMappings.put(split[1], split[2]);
			} else if("FD:".equals(split[0])) {
				//Example field line:
				// FD: abk/a net/minecraft/src/WorldGenDeadBush/field_76516_a
				FieldEntry from = FieldEntry.parse(split[1]);
				FieldEntry to = FieldEntry.parse(split[2]);
				fieldMappingsByOwningClass.computeIfAbsent(from.owningClass, __ -> new LinkedHashMap<>())
					.put(from, to);
			} else if("MD:".equals(split[0])) {
				//Example method line:
				// MD: acn/b (Lacn;)V net/minecraft/src/StructureBoundingBox/func_78888_b (Lnet/minecraft/src/StructureBoundingBox;)V
				if(split.length < 5) {
					System.err.println("line " + lineNo + " is too short for method descriptor: " + line);
					continue;
				}
				MethodEntry from = MethodEntry.parse(split[1], split[2]);
				MethodEntry to = MethodEntry.parse(split[3], split[4]);
				methodMappingsByOwningClass.computeIfAbsent(from.owningClass, __ -> new LinkedHashMap<>())
					.put(from, to);
			} else if(!"PK:".equals(split[0])) { //We acknowledge PK lines but they're useless to us, more to do with the source-based toolchain i think
				System.err.println("line " + lineNo + " has unknown type (not CL/FD/MD/PK): " + line);
			}
		}
		
		return this;
	}
	
	public Srg mergeWith(Srg other) {
		other.classMappings.forEach(classMappings::putIfAbsent);
		
		other.fieldMappingsByOwningClass.forEach((owningClass, otherFieldMaps) -> 
			fieldMappingsByOwningClass.computeIfAbsent(owningClass, __ -> new LinkedHashMap<>())
				.putAll(otherFieldMaps));
		
		other.methodMappingsByOwningClass.forEach((owningClass, otherMethodMaps) ->
			methodMappingsByOwningClass.computeIfAbsent(owningClass, __ -> new LinkedHashMap<>())
				.putAll(otherMethodMaps));
		
		return this;
	}
	
	public static class FieldEntry {
		public FieldEntry(String owningClass, String name) {
			this.owningClass = owningClass;
			this.name = name;
		}
		
		public final String owningClass; //"internal name" format, with slashes
		public final String name;
		
		public static FieldEntry parse(String unsplit) {
			//SRGs store field entries like this:
			//net/minecraft/src/PathPoint/field_75839_a
			//The name of the field is attached to the owning class with a `/`.
			int i = unsplit.lastIndexOf('/');
			return new FieldEntry(unsplit.substring(0, i), unsplit.substring(i + 1));
		}
		
		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			FieldEntry that = (FieldEntry) o;
			return owningClass.equals(that.owningClass) && name.equals(that.name);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(owningClass, name);
		}
	}
	
	public static class MethodEntry {
		public MethodEntry(String owningClass, String name, String descriptor) {
			this.owningClass = owningClass;
			this.name = name;
			this.descriptor = descriptor;
		}
		
		public final String owningClass;
		public final String name;
		public final String descriptor;
		
		public static MethodEntry parse(String unsplit, String descriptor) {
			//Method names are stored the same way as field names, attached with a `/`
			int i = unsplit.lastIndexOf('/');
			return new MethodEntry(unsplit.substring(0, i), unsplit.substring(i + 1), descriptor);
		}
		
		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			MethodEntry that = (MethodEntry) o;
			return owningClass.equals(that.owningClass) && name.equals(that.name) && descriptor.equals(that.descriptor);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(owningClass, name, descriptor);
		}
	}
}

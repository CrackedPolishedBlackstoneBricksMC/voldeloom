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
				//(note/todo: field 4 does not exist in the TSRG format)
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
			} else if(!"PK:".equals(split[0])) { //We acknowledge PK lines but they're useless to us, retroguard stuff
				System.err.println("line " + lineNo + " has unknown type (not CL/FD/MD/PK): " + line);
			}
		}
		
		return this;
	}
	
	public void writeTo(Path path) throws IOException {
		try(OutputStream o = new BufferedOutputStream(Files.newOutputStream(path)); OutputStreamWriter w = new OutputStreamWriter(o)) {
			write(w);
			o.flush(); //(SFX: toilet flushing)
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
	
	public void augment(JarScanData data) {
		int[] thinAirId = new int[] { 0 }; //FINAL OR EFFECTIVELY FINAL
		
		data.innerClassData.forEach((outerClass, innerClasses) ->
			innerClasses.forEach(innerClass -> {
				if(classMappings.containsKey(outerClass) && !classMappings.containsKey(innerClass)) {
					//`innerClass` is an inner class of `outerClass`, and there's a mapping for `outerClass`,
					//but none for `innerClass`. If we remap now, `innerClass` will be left where it is,
					//but `outerClass` will be moved. This will probably lead to runtime crashes; the JVM
					//really really wants outer classes and their inner classes to stick together.
					//We could fix this by augmenting the remapper, but currently it's off-the-shelf tiny-remapper.
					//Instead, let's invent a mapping for this class.
					
					//If there's a mapping "amq -> net/minecraft/block/Block", and I'm inventing a mapping for the
					//class "amq$1", I want to map it the same way "amq" is mapped, but keep the $1 suffix.
					//So ideally I want net/minecraft/block/Block$1.
					
					String suffix;
					
					int dollarIndex = innerClass.indexOf('$');
					if(dollarIndex == -1) {
						//Well that sucks. Let's just make something up
						suffix = "$voldeloom_invented$" + thinAirId[0]++; 
					} else {
						suffix = innerClass.substring(dollarIndex); //the part after/including the dollar sig
					}
					
					classMappings.put(innerClass, classMappings.get(outerClass) + suffix);
				}
			}));
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
	
	/**
	 * Applies the "packaging transformation"; this renames most of the fully-qualified class names.
	 * Class names, owners of fields, owners of methods, and method descriptors are affected.
	 */
	public Srg repackage(Packages packages) {
		StringInterner mem = new StringInterner();
		
		Map<String, String> repackagedClasses = new LinkedHashMap<>();
		classMappings.forEach((proguardClass, srgClass) -> repackagedClasses.put(
			mem.intern(packages.repackage(proguardClass)),
			mem.intern(packages.repackage(srgClass))
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
			
			repackagedMethods.put(mem.intern(packages.repackage(prg)), newMethods);
		});
		
		return new Srg(repackagedClasses, repackagedFields, repackagedMethods);
	}
	
	/**
	 * emulates the "rename the SRG using find-and-replace" step of official tools.
	 * Anywhere an SRG name could appear, it is fed through the fields/methods csvs.
	 * 
	 * @param srgAsFallback if {@code true} and an MCP field name doesn't exist, the srg name will be used,
	 *                      if not, the proguarded name will be used. same for method names.
	 *                      mainly because this method is for reobf, so it should match what's in the dev workspace
	 */
	public Srg named(Members fields, Members methods, boolean srgAsFallback) {
		Map<String, Map<String, String>> renamedFieldMappings = new LinkedHashMap<>();
		fieldMappingsByOwningClass.forEach((proguardClass, fieldMappings) -> {
			Map<String, String> namedFields = new LinkedHashMap<>();
			fieldMappings.forEach((proguard, srg) -> {
				Members.Entry entry = fields.remapSrg(srg);
				
				String name;
				if(entry != null) name = entry.remappedName;
				else if(srgAsFallback) name = srg;
				else name = proguard;
				
				namedFields.put(proguard, name);
			});
			
			renamedFieldMappings.put(proguardClass, namedFields);
		});
		
		Map<String, Map<MethodEntry, MethodEntry>> renamedMethodMappings = new LinkedHashMap<>();
		methodMappingsByOwningClass.forEach((proguardClass, methodMappings) -> {
			Map<MethodEntry, MethodEntry> namedMethods = new LinkedHashMap<>();
			methodMappings.forEach((proguard, srg) -> {
				Members.Entry entry = methods.remapSrg(srg.name);
				
				String name;
				if(entry != null) name = entry.remappedName;
				else if(srgAsFallback) name = srg.name;
				else name = proguard.name;
				
				namedMethods.put(proguard, new MethodEntry(name, srg.descriptor));
			});
			
			renamedMethodMappings.put(proguardClass, namedMethods);
		});
		
		return new Srg(new LinkedHashMap<>(classMappings), renamedFieldMappings, renamedMethodMappings);
	}
	
	/**
	 * Puts the thing down, flips it, and reverses it.
	 */
	public Srg inverted() {
		Map<String, String> flipClasses = invert(classMappings);
		
		Map<String, Map<String, String>> flipFields = new LinkedHashMap<>();
		fieldMappingsByOwningClass.forEach((proguardClass, fieldMappings) ->
			flipFields.put(classMappings.getOrDefault(proguardClass, proguardClass), invert(fieldMappings)));
		
		Map<String, Map<MethodEntry, MethodEntry>> flipMethods = new LinkedHashMap<>();
		methodMappingsByOwningClass.forEach((proguardClass, methodMappings) ->
			flipMethods.put(classMappings.getOrDefault(proguardClass, proguardClass), invert(methodMappings)));
		
		return new Srg(flipClasses, flipFields, flipMethods);
	}
	
	private <T> Map<T, T> invert(Map<T, T> in) {
		Map<T, T> inverted = new LinkedHashMap<>();
		in.forEach((key, value) -> inverted.put(value, key));
		return inverted;
	}
	
	/**
	 * Simply deletes a class from the mappings.
	 * This is maiiinly here to cheat with Ears, because it looks up a class at runtime using its proguarded name.
	 * Making it work in a dev workspace involves making sure that class exists under its proguarded name.
	 */
	public void unmapClass(String classs) {
		classMappings.remove(classs);
		fieldMappingsByOwningClass.remove(classs);
		methodMappingsByOwningClass.remove(classs);
	}
	
	public boolean isEmpty() {
		//handles the pathological case, where a mapping from X -> Y exists, but Y is itself an empty collection
		//probably Point less:tm:
		boolean fieldEmpty = fieldMappingsByOwningClass.isEmpty() || fieldMappingsByOwningClass.values().stream().allMatch(Map::isEmpty);
		boolean methodEmpty = methodMappingsByOwningClass.isEmpty() || methodMappingsByOwningClass.values().stream().allMatch(Map::isEmpty);
		return classMappings.isEmpty() && fieldEmpty && methodEmpty;
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

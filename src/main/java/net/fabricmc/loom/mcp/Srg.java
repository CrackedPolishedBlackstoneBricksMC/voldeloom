package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.tinyremapper.IMappingProvider;

import java.io.BufferedOutputStream;
import java.io.IOException;
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
	
	/// importing ///
	
	@SuppressWarnings("StatementWithEmptyBody")
	public Srg read(Path path, StringInterner mem) throws IOException {
		List<String> lines = Files.readAllLines(path);
		for(int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if(line.isEmpty()) continue;
			int lineNo = i + 1;
			
			String[] split = line.split(" ");
			if(split.length < 2) {
				System.err.println("srg/csrg line " + lineNo + " is too short: " + line);
				continue;
			}
			
			if("CL:".equals(split[0])) {
				//Example class line:
				// CL: abk net/minecraft/src/WorldGenDeadBush
				String fromClass = mem.intern(split[1]);
				String toClass = mem.intern(split[2]);
				putClassMapping(fromClass, toClass);
			} else if("FD:".equals(split[0])) {
				//Example field line:
				// FD: abk/a net/minecraft/src/WorldGenDeadBush/field_76516_a
				//
				//In fields 1 and 2, the name of the field is attached to the owning class with a `/`.
				int fstSlash = split[1].lastIndexOf('/');
				String fromOwningClass = mem.intern(split[1].substring(0, fstSlash));
				String fromName = mem.intern(split[1].substring(fstSlash + 1));
				
				int sndSlash = split[2].lastIndexOf('/');
				String toName = mem.intern(split[2].substring(sndSlash + 1));
				
				putFieldMapping(fromOwningClass, fromName, toName);
			} else if("MD:".equals(split[0])) {
				//Example method line:
				// MD: acn/b (Lacn;)V net/minecraft/src/StructureBoundingBox/func_78888_b (Lnet/minecraft/src/StructureBoundingBox;)V
				//
				//In fields 1 and 3, the name of the method is also attached to the owning class with a `/`.
				//Fields 2 and 4 are the descriptors of the method. Field 4 is simply field 2 with the class names remapped.
				if(split.length < 5) {
					System.err.println("line " + lineNo + " is too short for method descriptor: " + line);
					continue;
				}
				
				int fstSlash = split[1].lastIndexOf('/');
				String fromOwningClass = mem.intern(split[1].substring(0, fstSlash));
				String fromName = mem.intern(split[1].substring(fstSlash + 1));
				String fromDesc = mem.intern(split[2]);
				
				int trdSlash = split[3].lastIndexOf('/');
				String toName = mem.intern(split[3].substring(trdSlash + 1));
				String toDesc = mem.intern(split[4]);
				
				putMethodMapping(fromOwningClass, fromName, fromDesc, toName, toDesc);
			} else if("PK:".equals(split[0])) {
				//Ignore PK lines, they're retroguard junk.
			} else {
				//Ok, so this might be a line from a CSRG file, which is a slightly more terse format.
				//PK lines look like this (two entries where the first ends in `/`)
				// net/minecraft/ net/minecraft
				//CL lines look like this (class name, remapped class name)
				// a net/minecraft/util/EnumChatFormatting
				//FD lines look like this (owning class, field name, remapped field name)
				// aaa a field_75226_a
				//MD lines look like this (owning class, method name, method desc, remapped method name).)
				// ao d ()[Ljava/lang/String; func_90022_d
				switch(split.length) {
					case 2: {
						//Either a PK line or a CL line, tell them apart with the `/` character.
						if(!split[0].endsWith("/")) {
							String fromClass = mem.intern(split[0]);
							String toClass = mem.intern(split[1]);
							putClassMapping(fromClass, toClass);
						}
						break;
					}
					case 3: {
						//FD line. Note how the class name isn't attached to the field name with `/` anymore.
						String fromOwningClass = mem.intern(split[0]);
						String fromName = mem.intern(split[1]);
						String toName = mem.intern(split[2]);
						putFieldMapping(fromOwningClass, fromName, toName);
						break;
					}
					case 4: {
						//MD line. Also doesn't attach the name using a `/` character anymore.
						//The line also doesn't include a remapped method descriptor, so we need to puzzle that out.
						//(Note: this code assumes all relevant class mappings appear before method mappings in the csrg. This
						//allows the parser to remain single-pass. I haven't checked if this is accurate to Forgestuff.)
						String fromOwningClass = mem.intern(split[0]);
						String fromName = mem.intern(split[1]);
						String fromDesc = mem.intern(split[2]);
						String toName = mem.intern(split[3]);
						String toDesc = mem.intern(DescriptorMapper.map(fromDesc, c -> classMappings.getOrDefault(c, c)));
						
						putMethodMapping(fromOwningClass, fromName, fromDesc, toName, toDesc);
						break;
					}
					default: System.err.println("srg/csrg line " + lineNo + " is weird: " + line);
				}
			}
		}
		
		//TODO: Temp fixes for remapping weirdness.
		// These are only relevant to one specific Minecraft version, so including stuff like the owning class name
		// and SRG name are attempts to make sure we don't accidentally target irrelevant mappings.
		//1.6.4 - duplicated mappings, cause tiny-remapper to report "unfixable conflicts"
		kludge("bga", "a", "(Lof;DDDFF)V", "func_130000_a");
		kludge("bhb", "a", "(Lof;DDDFF)V", "func_130000_a");
		kludge("bhj", "a", "(Lof;DDDFF)V", "func_130000_a");
		kludge("bhb", "c", "(Lof;IF)V", "func_82408_c");
		kludge("bhj", "c", "(Lof;IF)V", "func_82408_c");
		
		//1.2.5 client - tiny-remapper accepts it, but ClassFormatErrors about duplicate method names happen at runtime
		kludge("uj", "i", "(Lxd;III)V", "func_319_i");
		kludge("yw", "c", "(Laan;I)V", "func_35199_b");
		
		return this;
	}
	
	private void kludge(String owningClass, String fromName, String fromDesc, String toName) {
		Map<MethodEntry, MethodEntry> methodMappings = methodMappingsByOwningClass.get(owningClass);
		if(methodMappings != null) {
			MethodEntry unmapped = new MethodEntry(fromName, fromDesc);
			MethodEntry mapped = methodMappings.get(unmapped);
			if(mapped != null && mapped.name.equals(toName)) {
				methodMappings.remove(unmapped);
				System.err.printf("TEMP FIX for voldeloom bug - Dropping method mapping for %s/%s %s (%s)%n", owningClass, fromName, fromDesc, toName);
			}
		}
	}
	
	/// exporting ///
	
	public void writeTo(Path path) throws IOException {
		//cant use cool loops because of IOException :(
		try(OutputStreamWriter w = new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(path)))) {
			for(Map.Entry<String, String> classMapping : classMappings.entrySet()) {
				w.write("CL: ");
				w.write(classMapping.getKey());
				w.write(' ');
				w.write(classMapping.getValue());
				w.write('\n');
			}
			
			for(Map.Entry<String, Map<String, String>> bleh : fieldMappingsByOwningClass.entrySet()) {
				String fromOwningClass = bleh.getKey();
				String toOwningClass = classMappings.getOrDefault(fromOwningClass, fromOwningClass);
				for(Map.Entry<String, String> fieldMapping : bleh.getValue().entrySet()) {
					w.write("FD: ");
					w.write(fromOwningClass); w.write('/'); w.write(fieldMapping.getKey());
					w.write(' ');
					w.write(toOwningClass); w.write('/'); w.write(fieldMapping.getValue());
					w.write('\n');
				}
			}
			
			for(Map.Entry<String, Map<MethodEntry, MethodEntry>> bleh : methodMappingsByOwningClass.entrySet()) {
				String fromOwningClass = bleh.getKey();
				String toOwningClass = classMappings.getOrDefault(fromOwningClass, fromOwningClass);
				for(Map.Entry<MethodEntry, MethodEntry> methodMapping : bleh.getValue().entrySet()) {
					w.write("MD: ");
					w.write(fromOwningClass); w.write('/'); w.write(methodMapping.getKey().name); w.write(' '); w.write(methodMapping.getKey().descriptor);
					w.write(' ');
					w.write(toOwningClass); w.write('/'); w.write(methodMapping.getValue().name); w.write(' '); w.write(methodMapping.getValue().descriptor);
					w.write('\n');
				}
			}
			w.flush(); //(SFX: toilet flushing)
		}
	}
	
	public IMappingProvider toMappingProvider() {
		return acceptor -> {
			classMappings.forEach(acceptor::acceptClass);
			
			fieldMappingsByOwningClass.forEach((owningClass, fieldMappings) ->
				fieldMappings.forEach((oldName, newName) ->
					//make up a field desc; tiny-remapper is put into a mode that ignores field descriptors anyway
					acceptor.acceptField(new IMappingProvider.Member(owningClass, oldName, "Ljava/lang/Void;"), newName)));
			
			methodMappingsByOwningClass.forEach((owningClass, methodMappings) ->
				methodMappings.forEach((oldMethod, newMethod) ->
					acceptor.acceptMethod(new IMappingProvider.Member(owningClass, oldMethod.name, oldMethod.descriptor), newMethod.name)));
		};
	}
	
	/**
	 * Returns a new srg with the "packaging transformation" applied; this renames most of the class names, field owners,
	 * method owners, and method descriptors. Field/method names and class simple names are unchanged.
	 */
	public Srg repackage(Packages packages) {
		StringInterner mem = new StringInterner();
		Srg repackaged = new Srg();
		
		classMappings.forEach((fromClass, toClass) ->
			repackaged.putClassMapping(
				mem.intern(packages.repackage(fromClass)),
				mem.intern(packages.repackage(toClass))
			));
		
		fieldMappingsByOwningClass.forEach((owningClass, fieldMappings) ->
			repackaged.putAllFieldMappings(
				mem.intern(packages.repackage(owningClass)),
				fieldMappings
			));
		
		methodMappingsByOwningClass.forEach((owningClass, methodMappings) ->
			methodMappings.forEach((fromEntry, toEntry) ->
				repackaged.putMethodMapping(
					mem.intern(owningClass),
					fromEntry.name,
					mem.intern(packages.repackageDescriptor(fromEntry.descriptor)),
					toEntry.name,
					mem.intern(packages.repackageDescriptor(toEntry.descriptor))
				)));
		
		return repackaged;
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
		//StringInterner mem = new StringInterner(); //Not necessary since i only make selections from existing String objects
		Srg named = new Srg();
		
		named.putAllClassMappings(classMappings);
		
		fieldMappingsByOwningClass.forEach((owningClass, fieldMappings) ->
			fieldMappings.forEach((proguard, srg) -> {
				Members.Entry namedEntry = fields.remapSrg(srg);
				
				named.putFieldMapping(
					owningClass,
					proguard,
					namedEntry != null ? namedEntry.remappedName : (srgAsFallback ? srg : proguard)
				);
			}));
		
		methodMappingsByOwningClass.forEach((owningClass, methodMappings) ->
			methodMappings.forEach((proguardEntry, srgEntry) -> {
				Members.Entry namedEntry = methods.remapSrg(srgEntry.name);
				
				named.putMethodMapping(
					owningClass,
					proguardEntry.name,
					proguardEntry.descriptor,
					namedEntry != null ? namedEntry.remappedName : (srgAsFallback ? srgEntry.name : proguardEntry.name),
					srgEntry.descriptor
				);
			}));
		
		return named;
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
	
	/// mutating ///
	
	public void putClassMapping(String from, String to) {
		classMappings.put(from, to);
	}
	
	public void putAllClassMappings(Map<String, String> bulk) {
		classMappings.putAll(bulk);
	}
	
	public void putFieldMapping(String owningClass, String from, String to) { //MCP doesn't handle field descriptors
		fieldMappingsByOwningClass.computeIfAbsent(owningClass, __ -> new LinkedHashMap<>())
			.put(from, to);
	}
	
	public void putAllFieldMappings(String owningClass, Map<String, String> bulk) {
		fieldMappingsByOwningClass.computeIfAbsent(owningClass, __ -> new LinkedHashMap<>())
			.putAll(bulk);
	}
	
	public void putMethodMapping(String owningClass, String fromName, String fromDesc, String toName, String toDesc) {
		methodMappingsByOwningClass.computeIfAbsent(owningClass, __ -> new LinkedHashMap<>())
			.put(new MethodEntry(fromName, fromDesc), new MethodEntry(toName, toDesc));
	}
	
	public void putAllMethodMappings(String owningClass, Map<MethodEntry, MethodEntry> bulk) {
		methodMappingsByOwningClass.computeIfAbsent(owningClass, __ -> new LinkedHashMap<>())
			.putAll(bulk);
	}
	
	public void removeClassMapping(String classs) {
		classMappings.remove(classs);
		fieldMappingsByOwningClass.remove(classs);
		methodMappingsByOwningClass.remove(classs);
	}
	
	public void augment(JarScanData data) {
		int[] thinAirId = new int[]{0}; //FINAL OR EFFECTIVELY FINAL
		
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
					//So ideally I want net/minecraft/block/Block$1. We don't really have a choice; Forge binpatches
					//will refer to the class by this name.
					
					String suffix;
					
					int dollarIndex = innerClass.indexOf('$');
					if(dollarIndex != -1) {
						//Grab the part after/including the dollar sign.
						suffix = innerClass.substring(dollarIndex);
					} else {
						//Well that sucks. Let's just make something up.
						suffix = "$voldeloom_invented$" + thinAirId[0]++;
					}
					
					classMappings.put(innerClass, classMappings.get(outerClass) + suffix);
				}
			}));
	}
	
	public void mergeWith(Srg other) {
		classMappings.putAll(other.classMappings);
		//not using putAll since i want to zip the maps together
		other.fieldMappingsByOwningClass.forEach(this::putAllFieldMappings);
		other.methodMappingsByOwningClass.forEach(this::putAllMethodMappings);
	}
	
	/// introspecting ///
	
	public boolean isEmpty() {
		return classMappings.isEmpty() &&
			(fieldMappingsByOwningClass.isEmpty() || fieldMappingsByOwningClass.values().stream().allMatch(Map::isEmpty)) &&
			(methodMappingsByOwningClass.isEmpty() || methodMappingsByOwningClass.values().stream().allMatch(Map::isEmpty));
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

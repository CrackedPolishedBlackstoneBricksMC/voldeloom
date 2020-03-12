package net.fabricmc.loom.forge;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.IMappingProvider.Member;

public class ForgeATConfig implements IMappingProvider.MappingAcceptor {

	private final Map<String, AccessTransformation> unmappedAccessTransformers = new HashMap<>();
	private final Set<String> unmappedAffectedClasses = new HashSet<>();
	private final Map<String, String> typeMappings = new HashMap<>();
	
	public final Map<String, AccessTransformation> accessTransformers = new HashMap<>();
	public final Set<String> affectedClasses = new HashSet<>();
	
	public void load(InputStream from) {
		try(Scanner scan = new Scanner(from)) {
			while(scan.hasNextLine()) {
				String line = scan.nextLine();
				line = line.split("#", 2)[0].trim(); // strip comments, extraneous whitespace
				if(line.length() == 0 || line.startsWith("#")) { // skip empty lines
					continue;
				}
				String[] split = line.split(" ");
				unmappedAccessTransformers.put(split[1], AccessTransformation.parse(split[0]));
				/*if(!split[1].contains(".")) {
					// type AT
					unmappedAccessTransformers.put(mappedOwner, AccessTransformation.parse(split[0]));
				} else if(split[1].contains("(")) {
					// method AT
					String[] mSplit = split2[1].split("\\(");
					String desc = "(" + mSplit;
					unmappedAccessTransformers.put(mappedOwner + "." + remapper.mapMethodName(split2[0], mSplit[0], desc) + remapper.mapMethodDesc(desc), AccessTransformation.parse(split[0]));
				} else {
					// field AT
					unmappedAccessTransformers.put(mappedOwner + "." + remapper.mapFieldName(split2[0], split2[1], "Ljava/lang/Object;"), AccessTransformation.parse(split[0]));
				}*/
				unmappedAffectedClasses.add(split[1].split("\\.")[0]);
			}
		}
	}
	
	public boolean affects(Path p) {
		if(!p.toString().endsWith(".class")) {
			return false;
		}
		String fileName = p.getFileName().toString();
		return unmappedAffectedClasses.contains(fileName.substring(0, fileName.length() - 6));
	}

	@Override
	public void acceptClass(String srcName, String dstName) {
		typeMappings.put(srcName, dstName);
		AccessTransformation at;
		if((at = unmappedAccessTransformers.remove(srcName)) != null) {
			accessTransformers.put(dstName, at); // type transformers are very easy to map.
		}
	}

	@Override
	public void acceptMethod(Member method, String dstName) {
		AccessTransformation at;
		if((at = unmappedAccessTransformers.remove(method.owner + "." + method.name + method.desc)) != null) {
			unmappedAccessTransformers.put(method.owner + "." + dstName + method.desc, at);
		}
	}

	@Override
	public void acceptMethodArg(Member method, int lvIndex, String dstName) { }

	@Override
	public void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) { }

	@Override
	public void acceptField(Member field, String dstName) {
		AccessTransformation at;
		if((at = unmappedAccessTransformers.remove(field.owner + "." + field.name)) != null) {
			unmappedAccessTransformers.put(field.owner + "." + dstName, at);
		}
	}
	
	public void finishRemapping() {
		for(Map.Entry<String, AccessTransformation> e : unmappedAccessTransformers.entrySet()) {
			//System.out.println(e.getKey());
			String[] split = e.getKey().split("\\.");
			String mappedOwner = typeMappings.get(split[0]);
			String mappedDesc = split[1];
			String[] split2 = mappedDesc.split(";");
			int offset = mappedDesc.endsWith(";") ? 0 : 1;
			for(int i = 0; i < split2.length - offset; i++) {
				String[] split3 = split2[i].split("L", 2);
				//System.out.println(Arrays.toString(split2));
				mappedDesc = mappedDesc.replace("L" + split3[1] + ";", "L" + typeMappings.getOrDefault(split3[1], split3[1]) + ";");
			}
			accessTransformers.put(mappedOwner + "." + mappedDesc, e.getValue());
		}
		for(String s : unmappedAffectedClasses) {
			affectedClasses.add(typeMappings.get(s));
		}
	}

}

package net.fabricmc.loom.util.mcp;

import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import net.fabricmc.tinyremapper.IMappingProvider.Member;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes MCP-format {@code .csv} files and forwards them to a MappingAcceptor.
 * Select which columns of the CSV to read using the unnamedIdx and namedIdx constructor parameters.
 * 
 * @author TwilightFlower
 */
public class CsvApplierAcceptor implements MappingAcceptor {
	public static final int NEWNAME_CLIENT_IN = 0;
	public static final int NEWNAME_SERVER_IN = 1;
	public static final int GENERIC_IN = 0;
	public static final int GENERIC_OUT = 1;
	public static final int NEWNAME_OUT = 2;
	public static final int PACKAGES_IN = -5;
	public static final int PACKAGES_OUT = 1;
	
	private final Map<String, String> map = new HashMap<>();
	private final MappingAcceptor underlying;
	
	private boolean classes = false;
	
	public CsvApplierAcceptor(MappingAcceptor underlying, Path mcpCsv, int unnamedIdx, int namedIdx) throws IOException {
		this.underlying = underlying;
		boolean packages = unnamedIdx == PACKAGES_IN;
		if (packages) {
			unnamedIdx = 0;
			classes = true;
		}
		
		List<String> lines = Files.readAllLines(mcpCsv);
		for(int i = packages ? 1 : 0; i < lines.size(); i++) {
			String[] lineSplit = lines.get(i).split(",");
			if(!lineSplit[unnamedIdx].equals("*")) {
				map.put((packages ? "net/minecraft/src/" : "")+lineSplit[unnamedIdx], lineSplit[namedIdx]+(packages ? "/"+lineSplit[unnamedIdx] : ""));
			}
		}
	}
	
	@Override
	public void acceptClass(String srcName, String dstName) {
		underlying.acceptClass(srcName, classes ? map.getOrDefault(dstName, dstName) : dstName);
	}

	@Override
	public void acceptMethod(Member method, String dstName) {
		underlying.acceptMethod(method, classes ? dstName : map.getOrDefault(dstName, dstName));
	}

	@Override
	public void acceptMethodArg(Member method, int lvIndex, String dstName) {
		underlying.acceptMethodArg(method, lvIndex, dstName);
	}

	@Override
	public void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
		underlying.acceptMethodVar(method, lvIndex, startOpIdx, asmIndex, dstName);
	}

	@Override
	public void acceptField(Member field, String dstName) {
		underlying.acceptField(field, classes ? dstName : map.getOrDefault(dstName, dstName));
	}

}

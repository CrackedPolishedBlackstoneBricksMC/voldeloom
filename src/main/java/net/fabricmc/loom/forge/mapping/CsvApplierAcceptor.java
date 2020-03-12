package net.fabricmc.loom.forge.mapping;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import net.fabricmc.tinyremapper.IMappingProvider.Member;

public class CsvApplierAcceptor implements MappingAcceptor {

	public static final int NEWNAME_CLIENT_IN = 0;
	public static final int NEWNAME_SERVER_IN = 1;
	public static final int GENERIC_IN = 0;
	public static final int GENERIC_OUT = 1;
	public static final int NEWNAME_OUT = 2;
	
	private final Map<String, String> map = new HashMap<>();
	private MappingAcceptor underlying;
	
	public CsvApplierAcceptor(MappingAcceptor underlying, Path mcpCsv, int unnamedIdx, int namedIdx) throws IOException {
		this.underlying = underlying;
		try(Scanner csvScanner = new Scanner(new BufferedInputStream(Files.newInputStream(mcpCsv)))) {
			while(csvScanner.hasNextLine()) {
				String[] line = csvScanner.nextLine().split(",");
				if(!line[unnamedIdx].equals("*")) {
					map.put(line[unnamedIdx], line[namedIdx]);
				}
			}
		}
	}
	
	@Override
	public void acceptClass(String srcName, String dstName) {
		underlying.acceptClass(srcName, dstName);
	}

	@Override
	public void acceptMethod(Member method, String dstName) {
		underlying.acceptMethod(method, map.getOrDefault(dstName, dstName));
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
		underlying.acceptField(field, map.getOrDefault(dstName, dstName));
	}

}

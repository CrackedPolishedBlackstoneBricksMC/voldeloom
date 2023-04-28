package net.fabricmc.loom.mcp;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Walks a filesystem for "interesting", mappings-relevant files. The idea here is that it's wasteful to do 6 passes
 * over a filesystem to find 6 files, especially when the filesystem is large (e.g. when sourcing mappings from a Forge
 * -sources zip).
 * <br>
 * Paths stored here remain valid only as long as the filesystem is.
 */
public class MappingScanner {
	public MappingScanner(FileSystem fs) throws IOException {
		Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
				String filename = path.getFileName().toString();
				if(INTERESTING_FILENAMES.contains(filename)) {
					interestingFiles.put(filename.intern(), path);
				}
				
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	private final Map<String, Path> interestingFiles = new HashMap<>();
	
	public @Nullable Path get(String name) {
		return interestingFiles.get(name);
	}
	
	public static final String JOINED_SRG = "joined.srg";
	public static final String JOINED_CSRG = "joined.csrg";
	public static final String PACKAGES_CSV = "packages.csv";
	
	public static final String CLIENT_SRG = "client.srg";
	public static final String SERVER_SRG = "server.srg";
	
	public static final String FIELDS_CSV = "fields.csv";
	public static final String METHODS_CSV = "methods.csv";
	
	public static final Set<String> INTERESTING_FILENAMES = new HashSet<>(Arrays.asList(
		JOINED_SRG, JOINED_CSRG, PACKAGES_CSV, CLIENT_SRG, SERVER_SRG, FIELDS_CSV, METHODS_CSV
	));
}

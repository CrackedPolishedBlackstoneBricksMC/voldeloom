package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.BinpatchesPack;
import org.gradle.api.Project;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Loads a set of binpatches out of a jar.
 */
public class BinpatchLoader extends NewProvider<BinpatchLoader> {
	public BinpatchLoader(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private Path forgeJar;
	
	public BinpatchLoader forgeJar(Path forgeJar) {
		this.forgeJar = forgeJar;
		return this;
	}
	
	//outputs
	private BinpatchesPack binpatchesPack = null;
	
	public boolean hasBinpatches() {
		return binpatchesPack != null;
	}
	
	public BinpatchesPack getBinpatches() {
		return binpatchesPack;
	}
	
	//procedure
	public BinpatchLoader load() throws Exception {
		Preconditions.checkNotNull(forgeJar, "forge jar");
		
		log.info("|-> Examining {} for binpatches.", forgeJar);
		
		try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forgeJar.toUri()), Collections.emptyMap())) {
			Path binpatchesPath = forgeFs.getPath("binpatches.pack.lzma");
			if(Files.exists(binpatchesPath)) {
				log.info("\\-> Yes, it contains binpatches. Parsing.");
				binpatchesPack = new BinpatchesPack().read(log, binpatchesPath);
				log.info("\\-> Binpatches parsed.");
			} else {
				log.info("\\-> No, it doesn't contain any binpatches.");
			}
		}
		
		return this;
	}
}

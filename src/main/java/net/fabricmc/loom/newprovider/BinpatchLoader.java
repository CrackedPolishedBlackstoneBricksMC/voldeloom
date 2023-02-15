package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.BinpatchesPack;
import org.gradle.api.Project;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class BinpatchLoader extends NewProvider<BinpatchLoader> {
	public BinpatchLoader(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//stage 1:
	//inputs
	private ResolvedConfigElementWrapper forge;
	
	public BinpatchLoader forge(ResolvedConfigElementWrapper forge) {
		this.forge = forge;
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
		try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getPath().toUri()), Collections.emptyMap())) {
			Path binpatchesPath = forgeFs.getPath("binpatches.pack.lzma");
			if(Files.exists(binpatchesPath)) {
				binpatchesPack = new BinpatchesPack().read(project, binpatchesPath);
			}
		}
		
		return this;
	}
}

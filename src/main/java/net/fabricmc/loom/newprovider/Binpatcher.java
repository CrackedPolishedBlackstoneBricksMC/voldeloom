package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.mcp.Binpatch;
import org.gradle.api.Project;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;

/**
 * Applies a set of Forge 1.6+ gdiff binary-patches to an input jar.
 */
public class Binpatcher extends NewProvider<Binpatcher> {
	public Binpatcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private Path input;
	private Map<String, Binpatch> binpatches;
	
	public Binpatcher input(Path input) {
		this.input = input;
		return this;
	}
	
	public Binpatcher binpatches(Map<String, Binpatch> binpatches) {
		this.binpatches = binpatches;
		return this;
	}
	
	//outputs
	private Path output;
	
	public Path getOutput() {
		return output;
	}
	
	//procedure
	public Binpatcher patch() throws Exception {
		Preconditions.checkNotNull(input, "binpatch input");
		Preconditions.checkNotNull(binpatches, "binpatches");
		
		output = getCacheDir().resolve(LoomGradlePlugin.replaceExtension(input, "-binpatched.jar").getFileName().toString()); //meh
		cleanOnRefreshDependencies(output);
		
		log.info("] binpatch input: {}", input);
		log.info("] binpatch output: {}", output);
		log.info("] number of binpatches: {}", binpatches.size());
		
		if(Files.notExists(output)) {
			log.info("|-> Output does not exist, performing binpatch...");
			
			try(FileSystem inputFs  = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()),  Collections.emptyMap()); 
			    FileSystem outputFs = FileSystems.newFileSystem(URI.create("jar:" + output.toUri()), Collections.singletonMap("create", "true"))) {
				Files.walkFileTree(inputFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
						Files.createDirectories(outputFs.getPath(vanillaPath.toString()));
						return FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult visitFile(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
						Path patchedPath = outputFs.getPath(vanillaPath.toString());
						String filename = vanillaPath.toString().substring(1); //remove leading slash
						
						if(filename.endsWith(".class")) {
							Binpatch binpatch = binpatches.get(filename.substring(0, filename.length() - ".class".length()));
							if(binpatch != null) {
								log.debug("Binpatching {}...", filename);
								Files.write(patchedPath, binpatch.apply(Files.readAllBytes(vanillaPath)));
								return FileVisitResult.CONTINUE;
							}
						}
						
						Files.copy(vanillaPath, patchedPath);
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			log.info("|-> Binpatch success.");
		}
		
		return this;
	}
}

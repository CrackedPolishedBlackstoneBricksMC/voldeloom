package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.Binpatch;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
	
	//outputs
	private Path output;
	
	public Binpatcher input(Path input) {
		this.input = input;
		return this;
	}
	
	public Binpatcher binpatches(Map<String, Binpatch> binpatches) {
		this.binpatches = binpatches;
		return this;
	}
	
	public Binpatcher outputFilename(String outputFilename) {
		this.output = getCacheDir().resolve(outputFilename);
		return this;
	}
	
	public Path getOutput() {
		return output;
	}
	
	//procedure
	public Binpatcher patch() throws Exception {
		Preconditions.checkNotNull(input, "binpatch input");
		Preconditions.checkNotNull(binpatches, "binpatches");
		
		cleanOnRefreshDependencies(output);
		
		log.info("] binpatch input: {}", input);
		log.lifecycle("] binpatch output: {}", output);
		log.info("] number of binpatches: {}", binpatches.size());
		
		if(Files.notExists(output)) {
			log.info("|-> Output does not exist, performing binpatch...");
			Files.createDirectories(output.getParent());
			
			try(FileSystem inputFs  = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()),  Collections.emptyMap()); 
			    FileSystem outputFs = FileSystems.newFileSystem(URI.create("jar:" + output.toUri()), Collections.singletonMap("create", "true"))) {
				
				Set<Binpatch> unusedBinpatches = new HashSet<>(binpatches.values());
				
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
								unusedBinpatches.remove(binpatch);
								return FileVisitResult.CONTINUE;
							}
						}
						
						Files.copy(vanillaPath, patchedPath);
						return FileVisitResult.CONTINUE;
					}
				});
				
				for(Binpatch unusedPatch : unusedBinpatches) {
					if(unusedPatch.existsAtTarget) {
						log.warn("Unused binpatch with 'existsAtTarget = true', {}", unusedPatch.originalEntryName);
					} else {
						log.debug("Binpatching (!existsAtTarget) {}...", unusedPatch.sourceClassName);
						
						String[] split = unusedPatch.sourceClassName.split("\\.");
						split[split.length - 1] += ".class";
						Path path = outputFs.getPath("/", split);
						
						if(Files.exists(path)) {
							log.warn("Unused binpatch with 'existsAtTarget = false' for a file that already exists, {}", unusedPatch.originalEntryName);
						} else {
							if(path.getParent() != null) Files.createDirectories(path.getParent());
							Files.write(path, unusedPatch.apply(new byte[0]));
						}
					}
				}
			}
			
			log.info("|-> Binpatch success.");
		}
		
		return this;
	}
}

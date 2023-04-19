package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class RemapperMcp extends NewProvider<RemapperMcp> {
	public RemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private Path input;
	private Srg srg;
	private String mappedDirectory, mappedFilename;
	private Set<String> deletedPrefixes;
	private final Set<Path> remapClasspath = new HashSet<>();
	
	public RemapperMcp inputJar(Path inputJar) {
		this.input = inputJar;
		return this;
	}
	
	public RemapperMcp srg(Srg srg) {
		this.srg = srg;
		return this;
	}
	
	public RemapperMcp outputSrgJar(String mappingsDepString, String outputName) {
		this.mappedDirectory = mappingsDepString;
		this.mappedFilename = outputName;
		return this;
	}
	
	public RemapperMcp deletedPrefixes(Set<String> deletedPrefixes) {
		this.deletedPrefixes = deletedPrefixes;
		return this;
	}
	
	public RemapperMcp addToRemapClasspath(Collection<Path> nonNativeLibs) {
		this.remapClasspath.addAll(nonNativeLibs);
		return this;
	}
	
	//outputs
	private Path mappedJar;
	
	public Path getOutputSrgJar() {
		return mappedJar;
	}
	
	public RemapperMcp remap() throws Exception {
		log.lifecycle("] input jar: {}", input);
		log.lifecycle("] output jar: {}", mappedJar);
		
		mappedJar = getOrCreate(getCacheDir().resolve("mapped").resolve(mappedDirectory).resolve(props.subst(mappedFilename)), dest -> {
			doIt(input, dest, srg, log, deletedPrefixes, remapClasspath);
		});
		log.lifecycle("] mapped jar: {}", mappedJar);
		
		return this;
	}
	
	public static void doIt(Path input, Path mappedJar, Srg srg, Logger log, @Nullable Set<String> deletedPrefixes, @Nullable Set<Path> remapClasspath) throws Exception {
		log.lifecycle("\\-> Constructing TinyRemapper");
		TinyRemapper remapper = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.ignoreFieldDesc(true) //MCP doesn't have them
			.skipLocalVariableMapping(true)
			.withMappings(srg.toMappingProvider())
			.build();
		
		log.lifecycle("\\-> Performing remap");
		
		OutputConsumerPath.Builder buildybuild = new OutputConsumerPath.Builder(mappedJar);
		if(deletedPrefixes != null && !deletedPrefixes.isEmpty()) buildybuild.filter(s -> !deletedPrefixes.contains(s.split("/", 2)[0]));
		try(OutputConsumerPath oc = buildybuild.build()) {
			oc.addNonClassFiles(input);
			if(remapClasspath != null) remapper.readClassPath(remapClasspath.toArray(new Path[0]));
			remapper.readInputs(input);
			remapper.apply(oc);
		} finally {
			remapper.finish();
		}
		
		log.lifecycle("\\-> Remap success! :)");
	}
}

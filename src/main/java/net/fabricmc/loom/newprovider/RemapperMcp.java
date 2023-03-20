package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.loom.util.Check;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class RemapperMcp extends NewProvider<RemapperMcp> {
	public RemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path input;
	private Srg srg;
	private Path output;
	
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
		this.output = getCacheDir().resolve("mappedmcp").resolve(mappingsDepString).resolve(outputName);
		return this;
	}
	
	//TODO: this is used in places that don't care about NewProvider abstractions, which is probably a bad idea in the general case
	public RemapperMcp outputSrgJar_Generic(Path path) {
		this.output = path;
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
	
	public Path getOutputSrgJar() {
		return output;
	}
	
	public RemapperMcp remap() throws Exception {
		Check.notNull(input, "input jar");
		Check.notNull(output, "output jar");
		
		log.lifecycle("] input jar: {}", input);
		log.lifecycle("] output jar: {}", output);
		
		cleanOnRefreshDependencies(output);
		if(Files.exists(output)) return this;
		
		log.lifecycle("\\-> Constructing TinyRemapper");
		
		TinyRemapper remapper = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.ignoreFieldDesc(true) //MCP doesn't have them
			.skipLocalVariableMapping(true)
			.withMappings(srg.toMappingProvider())
			.build();
		
		log.lifecycle("\\-> Performing remap");
		
		OutputConsumerPath.Builder buildybuild = new OutputConsumerPath.Builder(output);
		if(deletedPrefixes != null && !deletedPrefixes.isEmpty()) buildybuild.filter(s -> !deletedPrefixes.contains(s.split("/", 2)[0]));
		
		try(OutputConsumerPath oc = buildybuild.build()) {
			oc.addNonClassFiles(input);
			remapper.readClassPath(remapClasspath.toArray(new Path[0]));
			remapper.readInputs(input);
			remapper.apply(oc);
		} finally {
			remapper.finish();
		}
		
		log.lifecycle("\\-> Remap success! :)");
		
		return this;
	}
}

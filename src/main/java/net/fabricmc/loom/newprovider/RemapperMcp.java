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
import java.util.Set;

public class RemapperMcp extends NewProvider<RemapperMcp> {
	public RemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path input;
	private Srg srg;
	private Path output;
	
	private String mappingsDepString;
	
	private Set<String> deletedPrefixes;
	private Collection<Path> nonNativeLibs;
	
	public RemapperMcp inputJar(Path inputJar) {
		this.input = inputJar;
		return this;
	}
	
	public RemapperMcp srg(Srg srg) {
		this.srg = srg;
		return this;
	}
	
	public RemapperMcp outputSrgJar(String outputIntermediaryName) {
		this.output = getCacheDir().resolve("mappedmcp").resolve(mappingsDepString).resolve(outputIntermediaryName);
		return this;
	}
	
	public RemapperMcp mappingsDepString(String mappingsDepString) {
		this.mappingsDepString = mappingsDepString;
		return this;
	}
	
	public RemapperMcp deletedPrefixes(Set<String> deletedPrefixes) {
		this.deletedPrefixes = deletedPrefixes;
		return this;
	}
	
	public RemapperMcp nonNativeLibs(Collection<Path> nonNativeLibs) {
		this.nonNativeLibs = nonNativeLibs;
		return this;
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
			.skipLocalVariableMapping(false)
			.withMappings(srg.toMappingProvider())
			.build();
		
		log.lifecycle("\\-> Performing remap");
		
		try(OutputConsumerPath oc = new OutputConsumerPath.Builder(output).filter(s -> !deletedPrefixes.contains(s.split("/", 2)[0])).build()) {
			oc.addNonClassFiles(input);
			remapper.readClassPath(nonNativeLibs.toArray(new Path[0]));
			remapper.readInputs(input);
			remapper.apply(oc);
		} finally {
			remapper.finish();
		}
		
		log.lifecycle("\\-> Remap success! :)");
		
		return this;
	}
}

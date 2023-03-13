package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RemapperMcp extends NewProvider<RemapperMcp> {
	public RemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path inputJar;
	
	private String mappingsDepString;
	private Path outputIntermediary;
	private Path outputNamed;
	
	private McpMappings mcpMappings;
	
	private Set<String> deletedPrefixes;
	private Collection<Path> nonNativeLibs;
	
	public RemapperMcp inputJar(Path inputJar) {
		this.inputJar = inputJar;
		return this;
	}
	
	public RemapperMcp outputIntermediary(Path outputIntermediary) {
		this.outputIntermediary = outputIntermediary;
		return this;
	}
	
	public RemapperMcp outputIntermediary(String outputIntermediaryName) {
		this.outputIntermediary = getCacheDir().resolve("mappedmcp").resolve(mappingsDepString).resolve(outputIntermediaryName);
		return this;
	}
	
	public RemapperMcp outputNamed(String outputNamedName) {
		this.outputNamed = getCacheDir().resolve("mappedmcp").resolve(mappingsDepString).resolve(outputNamedName);
		return this;
	}
	
	public RemapperMcp mcpMappings(McpMappings mcpMappings) {
		this.mcpMappings = mcpMappings;
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
		boolean allExist = true;
		if(outputIntermediary != null) {
			log.lifecycle("] intermediary jar: {}", outputIntermediary);
			
			cleanOnRefreshDependencies(outputIntermediary);
			if(Files.notExists(outputIntermediary)) allExist = false;
		}
		if(outputNamed != null) {
			log.lifecycle("] named jar: {}", outputIntermediary);
			
			cleanOnRefreshDependencies(outputNamed);
			if(Files.notExists(outputNamed)) allExist = false;
		}
		
		if(allExist) return this; //No more work to do
		
		Predicate<String> classFilter = s -> !deletedPrefixes.contains(s.split("/", 2)[0]);
		Supplier<TinyRemapper.Builder> remapperMaker = () -> TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.ignoreFieldDesc(true) //MCP doesn't have them
			.skipLocalVariableMapping(false);
		
		if(outputIntermediary != null && Files.notExists(outputIntermediary)) {
			TinyRemapper remapper = remapperMaker.get()
				.withMappings(mcpMappings.toTinyRemapper(m -> m.joined, false))
				.build();
			
			try(OutputConsumerPath oc = new OutputConsumerPath.Builder(outputIntermediary).filter(classFilter).build()) {
				oc.addNonClassFiles(inputJar);
				remapper.readClassPath(nonNativeLibs.toArray(new Path[0]));
				remapper.readInputs(inputJar);
				remapper.apply(oc);
			} finally {
				remapper.finish();
			}
		}
		
		if(outputNamed != null && Files.notExists(outputNamed)) {
			TinyRemapper remapper = remapperMaker.get()
				.withMappings(mcpMappings.toTinyRemapper(m -> m.joined, true))
				.build();
			
			try(OutputConsumerPath oc = new OutputConsumerPath.Builder(outputNamed).filter(classFilter).build()) {
				oc.addNonClassFiles(inputJar);
				remapper.readClassPath(nonNativeLibs.toArray(new Path[0]));
				remapper.readInputs(inputJar);
				remapper.apply(oc);
			} finally {
				remapper.finish();
			}
		}
		
		log.lifecycle("\\-> Remap success! :)");
		
		return this;
	}
}

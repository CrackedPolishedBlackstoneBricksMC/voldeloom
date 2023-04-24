package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.mcp.McpMappingsBuilder;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LayeredMcpMappings {
	public LayeredMcpMappings(Project project, LoomGradleExtension ext) {
		this.project = project;
		this.ext = ext;
	}
	
	private final Project project;
	private final LoomGradleExtension ext;
	
	public final List<Layer> layers = new ArrayList<>();
	
	@SuppressWarnings("unused") //gradle api
	public LayeredMcpMappings baseZip(Object thing) {
		layers.add(new BaseZipLayer(realizeToPath(thing)));
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public LayeredMcpMappings classes(Object thing) {
		layers.add(new ClassesLayer(realizeToPath(thing)));
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public LayeredMcpMappings fieldsMethods(Object thing) {
		layers.add(new FieldsMethodsLayer(realizeToPath(thing)));
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public LayeredMcpMappings unmapClass(String unmapOne) {
		layers.add(new ClassUnmappingLayer(Collections.singleton(unmapOne)));
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public LayeredMcpMappings unmapClass(Collection<String> unmap) {
		layers.add(new ClassUnmappingLayer(unmap));
		return this;
	}
	
	private Path resolveOne(Dependency dep) {
		Configuration detatched = project.getConfigurations().detachedConfiguration(dep);
		//detatched.resolutionStrategy(ResolutionStrategy::failOnNonReproducibleResolution); //TODO: missing Gradle 4
		return detatched.getSingleFile().toPath();
	}
	
	private Path realizeToPath(Object thing) {
		project.getLogger().info("\t-- (realizeToPath) wonder what this '{}' ({}) is? --", thing, thing.getClass().getName());
		
		if(thing instanceof Path) {
			project.getLogger().info("\t-- looks like a Path --");
			return (Path) thing;
		} else if(thing instanceof File) {
			project.getLogger().info("\t-- looks like a File --");
			return ((File) thing).toPath();
		} else if(thing instanceof Dependency) {
			project.getLogger().info("\t-- looks like a Dependency --");
			return resolveOne((Dependency) thing);
		}
		
		//just blindly assume toString makes sense (catches things like groovy GStringImpl)
		String s = thing.toString();
		
		if(s.startsWith("http:/") || s.startsWith("https:/")) {
			project.getLogger().info("\t-- looks like a stringified URL --");
			try {
				Path cache = WellKnownLocations.getLayeredMappingsCache(project).resolve("downloads");
				Files.createDirectories(cache);
				
				String hashedUrl = Checksum.stringHexHash(s, Checksum.SHA256.get()).substring(0, 16);
				
				//note the URL in a user-readable sidecar file, because the filename is a meaningless hash
				Path infoPath = cache.resolve(hashedUrl + ".info");
				List<String> info = new ArrayList<>();
				info.add("Downloaded from " + s);
				info.add("Used by '" + project.getRootProject().getName() + "'.");
				Files.write(infoPath, info, StandardCharsets.UTF_8);
				
				Path dest = cache.resolve(hashedUrl);
				new DownloadSession(s, project)
					.dest(dest)
					.skipIfExists()
					.gzip(true)
					.download();
				
				return dest;
			} catch (Exception e) {
				throw new RuntimeException("Failed to download from URL " + s + ": " + e.getMessage(), e);
			}
		} else {
			project.getLogger().info("\t-- looks like a Maven coordinate (or unknown) --");
			return resolveOne(project.getDependencies().create(thing));
		}
	}
	
	public Dependency createDependency() {
		try {
			if(layers.isEmpty()) {
				throw new RuntimeException("LayeredMcpMappings: No layers!");
			}
			
			Path cache = WellKnownLocations.getLayeredMappingsCache(project);
			
			//hash the configuration and pick the filename
			MessageDigest readersDigest = Checksum.SHA256.get();
			for(Layer layer : layers) {
				readersDigest.update(layer.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
				readersDigest.update((byte) 0);
				layer.updateHasher(readersDigest);
				readersDigest.update((byte) 0);
			}
			readersDigest.update((byte) 0); //<-- bump this when making a big change to mapping layer system !!!
			String hash = Checksum.toHexStringPrefix(readersDigest.digest(), 8);
			Path mappingsPath = cache.resolve(hash + ".zip");
			Path infoPath = cache.resolve(hash + ".info");
			
			project.getLogger().lifecycle("] Using layered mappings, output: {}", mappingsPath);
			
			if(ext.refreshDependencies || Files.notExists(mappingsPath)) {
				//fun part:
				Files.createDirectories(cache);
				
				//resolve the layers
				project.getLogger().lifecycle("|-> Computing layered mappings ({} layer{})...", layers.size(), layers.size() == 1 ? "" : "s");
				McpMappingsBuilder builder = new McpMappingsBuilder();
				StringInterner mem = new StringInterner();
				for(Layer layer : layers) layer.visit(project.getLogger(), builder, mem);
				
				project.getLogger().lifecycle("|-> Building...");
				McpMappings mappings = builder.build();
				
				//and create the file. everything goes into the root of the zip
				project.getLogger().lifecycle("|-> Writing...");
				try(FileSystem outFs = ZipUtil.createFs(mappingsPath)) {
					if(!mappings.joined.isEmpty()) mappings.joined.writeTo(outFs.getPath("joined.srg"));
					if(!mappings.client.isEmpty()) mappings.client.writeTo(outFs.getPath("client.srg"));
					if(!mappings.server.isEmpty()) mappings.server.writeTo(outFs.getPath("server.srg"));
					if(!mappings.fields.isEmpty()) mappings.fields.writeTo(outFs.getPath("fields.csv"));
					if(!mappings.methods.isEmpty()) mappings.methods.writeTo(outFs.getPath("methods.csv"));
				}
				project.getLogger().lifecycle("|-> Done.");
				
				//write info file pointing out why this file is in the gradle cache
				Files.write(infoPath, ("Used by '" + project.getRootProject().getName() + "'.").getBytes(StandardCharsets.UTF_8));
			}
			
			return new GradleDep(project, mappingsPath, hash);
		} catch (Exception e) {
			throw new RuntimeException("problem creating layered mappings ! " + e.getMessage(), e);
		}
	}
	
	//glue code to shove it into gradle, really
	//Some shit explodes if i don't use SelfResolvingDependencyInternal (gradle blind-casts) so fuckit whatever
	public static class GradleDep implements FileCollectionDependency, SelfResolvingDependencyInternal {
		public GradleDep(Project project, Path path, String hash) {
			this.fileCollection = project.files(path);
			this.path = path;
			this.version = "0.0+" + hash; //you're telling me a semantic versioned this dependency?
		}
		
		private final FileCollection fileCollection;
		private final Path path;
		private final String version;
		
		//SelfResolvingDependencyInternal (again idk why its used)
		@Nullable
		@Override
		public ComponentIdentifier getTargetComponentId() {
			return () -> "Voldeloom layered mappings";
		}
		
		//FileCollectionDependency
		@Override
		public FileCollection getFiles() {
			return fileCollection;
		}
		
		//SelfResolvingDependency
		@Override
		public Set<File> resolve() {
			return Collections.singleton(path.toFile());
		}
		
		@Override
		public Set<File> resolve(boolean transitive) {
			return resolve(); //dont care about transitive deps here
		}
		
		//Buildable
		@Override
		public TaskDependency getBuildDependencies() {
			return null;
		}
		
		//Dependency
		@Nullable
		@Override
		public String getGroup() {
			return "volde";
		}
		
		@Override
		public String getName() {
			return "layered";
		}
		
		@Nullable
		@Override
		public String getVersion() {
			return version;
		}
		
		@Override
		public boolean contentEquals(Dependency dependency) {
			return dependency.equals(this); //shush
		}
		
		@Override
		public Dependency copy() {
			return this; //SHHHHHHHHHH
		}
		
		@Nullable
		@Override
		public String getReason() {
			return "Voldeloom `layered` mappings";
		}
		
		@Override
		public void because(@Nullable String s) {
			//youer mom
		}
	}
}

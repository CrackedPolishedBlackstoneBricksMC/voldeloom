package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.DownloadSession;
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
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
	
	public List<Layer> layers = new ArrayList<>();
	
	public LayeredMcpMappings baseZip(Object thing) {
		layers.add(new BaseZipLayer(realizeToPath(thing)));
		return this;
	}
	
	public LayeredMcpMappings unmapClass(String unmapOne) {
		layers.add(new ClassUnmappingLayer(Collections.singleton(unmapOne)));
		return this;
	}
	
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
				
				String hashedUrl = Checksum.hexSha256Digest(s.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
				
				//for debugging purposes, note the URL in a user-readable sidecar file, because the filename is
				//hashed from the url (max_path, weird characters, etc)
				Path info = cache.resolve(hashedUrl + ".info");
				Files.write(info, ("Downloaded from " + s + "\n").getBytes(StandardCharsets.UTF_8));
				
				Path dest = cache.resolve(hashedUrl);
				new DownloadSession(s, project)
					.dest(dest)
					.skipIfExists()
					.gzip(true)
					.download();
				
				return dest;
			} catch (Exception e) { throw new RuntimeException(e); }
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
			Path filename = cache.resolve(hash() + ".zip");
			
			project.getLogger().lifecycle("] Using layered mappings, output: {}", filename);
			
			if(ext.refreshDependencies || Files.notExists(filename)) {
				//fun part:
				Files.createDirectories(cache);
				
				//resolve the layers
				project.getLogger().lifecycle("|-> Computing layered mappings ({} layer{})...", layers.size(), layers.size() == 1 ? "" : "s");
				McpMappings mappings = new McpMappings();
				for(Layer layer : layers) layer.visit(project.getLogger(), mappings);
				
				//and create the file. everything goes into the root of the zip
				project.getLogger().lifecycle("|-> Writing mappings...");
				try(FileSystem outFs = FileSystems.newFileSystem(URI.create("jar:" + filename.toUri()), Collections.singletonMap("create", "true"))) {
					if(!mappings.joined.isEmpty()) mappings.joined.writeTo(outFs.getPath("joined.srg"));
					if(!mappings.client.isEmpty()) mappings.client.writeTo(outFs.getPath("client.srg"));
					if(!mappings.server.isEmpty()) mappings.server.writeTo(outFs.getPath("server.srg"));
					if(!mappings.fields.isEmpty()) mappings.fields.writeTo(outFs.getPath("fields.csv"));
					if(!mappings.methods.isEmpty()) mappings.methods.writeTo(outFs.getPath("methods.csv"));
				}
				project.getLogger().lifecycle("|-> Done.");
			}
			
			return new GradleDep(project, filename);
		} catch (Exception e) {
			throw new RuntimeException("problem creating layered mappings ! " + e.getMessage(), e);
		}
	}
	
	private String hash() throws Exception {
		//create messagedigest
		MessageDigest readersDigest;
		try {
			readersDigest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException javaMoment) {
			//Truly a bummer that type safety here is simply impossible ! Ah well, Nevertheless,
			throw new RuntimeException("Apparently your JVM violates the Java Security Standard Algorithm Names document", javaMoment);
		}
		
		//visit its way through the mapping layers
		for(Layer layer : layers) {
			layer.updateHasher(readersDigest);
			readersDigest.update((byte) 0); //so two layers outputting [AB][C] doesn't hash to the same thing as [A][BC]
		}
		
		//format in hex
		return String.format("%08x", new BigInteger(1, readersDigest.digest())).substring(0, 8); //magical incantation
	}
	
	//glue code to shove it into gradle, really
	//Some shit explodes if i don't use SelfResolvingDependencyInternal (gradle blind-casts) so fuckit whatever
	public static class GradleDep implements FileCollectionDependency, SelfResolvingDependencyInternal {
		public GradleDep(Project project, Path path) {
			this.fileCollection = project.files(path);
			this.path = path;
		}
		
		private final FileCollection fileCollection;
		private final Path path;
		
		@Nullable
		@Override
		public ComponentIdentifier getTargetComponentId() {
			return () -> "Voldeloom layered mappings";
		}
		
		@Override
		public FileCollection getFiles() {
			return fileCollection;
		}
		
		@Override
		public Set<File> resolve() {
			return Collections.singleton(path.toFile());
		}
		
		@Override
		public Set<File> resolve(boolean transitive) {
			return resolve(); //dont care about transitive deps here
		}
		
		@Override
		public TaskDependency getBuildDependencies() {
			return null;
		}
		
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
			return path.getFileName().toString(); //i guess?
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

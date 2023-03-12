package net.fabricmc.loom.mcp.layer;

import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.DownloadSession;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public interface LayerInput extends Supplier<Path> {
	static LayerInput fromPath(Path path) {
		return () -> path;
	}
	
	/* private */ static Path resolveOne(Project project, Dependency dep) {
		Configuration detatched = project.getConfigurations().detachedConfiguration(dep);
		//detatched.resolutionStrategy(ResolutionStrategy::failOnNonReproducibleResolution); //TODO: missing Gradle 4
		return detatched.getSingleFile().toPath();
	}
	
	static LayerInput create(Project project, Object thing) {
		project.getLogger().info("\t-- (LayerInput) wonder what this '{}' is? --", thing);
		
		if(thing instanceof Path) {
			project.getLogger().info("\t-- (LayerInput) looks like a Path --");
			return fromPath((Path) thing);
		} else if(thing instanceof File) {
			project.getLogger().info("\t-- (LayerInput) looks like a File --");
			return fromPath(((File) thing).toPath());
		} else if(thing instanceof Dependency) {
			project.getLogger().info("\t-- (LayerInput) looks like a Dependency --");
			return fromPath(resolveOne(project, (Dependency) thing));
		}
		
		//just blindly assume toString makes sense (catches things like groovy GStringImpl)
		String s = thing.toString();
		
		if(s.startsWith("http:/") || s.startsWith("https:/")) {
			project.getLogger().info("\t-- (LayerInput) looks like a stringified URL --");
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
				
				return fromPath(dest);
			} catch (Exception e) { throw new RuntimeException(e); }
		} else {
			project.getLogger().info("\t-- (LayerInput) looks like a Maven coordinate (or unknown) --");
			return fromPath(resolveOne(project, project.getDependencies().create(thing)));
		}
	}
}

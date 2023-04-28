package net.fabricmc.loom.newprovider;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

/**
 * Like {@code ConfigElementWrapper}, but additionally gets the (singular) file that the (singular) element in the configuration resolves to. 
 */
public class ResolvedConfigElementWrapper extends ConfigElementWrapper {
	public ResolvedConfigElementWrapper(Project project, Configuration config) {
		super(config);
		
		Set<File> files = config.files(getDep());
		
		if(files.size() == 0) {
			throw new IllegalStateException("Expected configuration '" + config.getName() + "' to resolve to one file, but found zero.");
		} else if(files.size() != 1) {
			StringBuilder builder = new StringBuilder("Expected configuration '");
			builder.append(config.getName());
			builder.append("' to resolve to one file, but found ");
			builder.append(files.size());
			builder.append(":");
			
			for (File f : files) {
				builder.append("\n\t- ").append(f.getAbsolutePath());
			}
			
			throw new IllegalStateException(builder.toString());
		}
		
		path = files.iterator().next().toPath();
	}
	
	private final Path path;
	
	public Path getPath() {
		return path;
	}
}

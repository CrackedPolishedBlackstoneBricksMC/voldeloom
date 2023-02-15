package net.fabricmc.loom.newprovider;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

public class ResolvedConfigElementWrapper extends ConfigElementWrapper {
	public ResolvedConfigElementWrapper(Project project, Configuration config) {
		super(project, config);
		path = expectSinglePath(config);
	}
	
	private final Path path;
	
	public Path getPath() {
		return path;
	}
	
	private Path expectSinglePath(Configuration config) {
		Set<File> files = config.files(getDep());
		
		if(files.size() == 0) {
			throw new IllegalStateException("Expected configuration '" + config.getName() + "' to resolve to one file, but found zero.");
		} else if(files.size() == 1) {
			return files.iterator().next().toPath();
		} else {
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
	}
}

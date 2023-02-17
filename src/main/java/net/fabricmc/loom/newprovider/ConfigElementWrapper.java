package net.fabricmc.loom.newprovider;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;

/**
 * This is a convenience for dealing with Gradle dependency configurations that are supposed to contain only one file.
 * 
 * @see ResolvedConfigElementWrapper for one that also ensures the dependency resolves to something
 *                                   "why have this at all, if it doesn't resolve the dependency?", i hear you ask
 *                                   well it's because the {@code minecraft} dependency doesn't actually resolve ;)
 */
public class ConfigElementWrapper {
	public ConfigElementWrapper(Project project, Configuration config) {
		DependencySet set = config.getDependencies();
		
		if(set.size() == 0) {
			throw new IllegalStateException("Expected configuration '" + config.getName() + "' to contain one dependency, but found zero.");
		} else if(set.size() == 1) {
			//there is one dependency. let's resolve it
			dep = set.iterator().next();
			depString = String.format("%s:%s:%s", dep.getGroup(), dep.getName(), dep.getVersion());
			version = dep.getVersion();
		} else {
			StringBuilder builder = new StringBuilder("Expected configuration '");
			builder.append(config.getName());
			builder.append("' to contain one dependency, but found ");
			builder.append(set.size());
			builder.append(":");
			
			for (Dependency f : set) {
				builder.append("\n\t- ").append(f.toString());
			}
			
			throw new IllegalStateException(builder.toString());
		}
	}
	
	private final Dependency dep;
	private final String depString;
	private final String version;
	
	public Dependency getDep() {
		return dep;
	}
	
	public String getDepString() {
		return depString;
	}
	
	public String getVersion() {
		return version;
	}
	
	public String getFilenameSafeDepString() {
		return depString.replaceAll("[^A-Za-z0-9.+-]", "_");
	}
	
	public String getFilenameSafeVersion() {
		return version.replaceAll("[^A-Za-z0-9.+-]", "_");
	}
}

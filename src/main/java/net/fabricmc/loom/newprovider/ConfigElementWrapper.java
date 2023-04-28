package net.fabricmc.loom.newprovider;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;

import javax.annotation.Nullable;

/**
 * This is a convenience for dealing with Gradle dependency configurations that are supposed to contain only one dependency.
 * 
 * @see ResolvedConfigElementWrapper ConfigElementWrapper doesn't resolve any files belonging to the dependency.
 *                                   ResolvedConfigElementWrapper additionally ensures that the dependency resolves to one file.
 */
public class ConfigElementWrapper {
	public ConfigElementWrapper(Configuration config) {
		DependencySet set = config.getDependencies();
		
		if(set.size() == 0) {
			throw new IllegalStateException("Expected configuration '" + config.getName() + "' to contain one dependency, but found zero.");
		} else if(set.size() != 1) {
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
		
		//there is one dependency. let's resolve it
		this.dep = set.iterator().next();
		
		this.depString = String.format("%s:%s:%s", dep.getGroup(), dep.getName(), dep.getVersion());
		this.version = dep.getVersion();
		
		this.filenameSafeDepString = sanitizeForFilenames(depString);
		this.filenameSafeVersion = sanitizeForFilenames(version);
	}
	
	private final Dependency dep;
	private final String depString, filenameSafeDepString;
	private final String version, filenameSafeVersion;
	
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
		return filenameSafeDepString;
	}
	
	public String getFilenameSafeVersion() {
		return filenameSafeVersion;
	}
	
	protected String sanitizeForFilenames(@Nullable String in) {
		if(in == null) return null; //hrm
		
		String out = in.replaceAll("[^A-Za-z0-9.+-]", "_");
		if(out.equals(in)) return in; //i love to save 0.0001 bytes of ram
		else return out;
	}
}

package net.fabricmc.loom;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import javax.annotation.Nullable;

/**
 * When working with artifacts that are themselves mods, it is nice to remap
 * them "on the way in", from the release namespace into the dev workspace's namespace.
 * This eliminates the need for "dev jars" and all the fuss those entail,
 * like ensuring whoever made the dev jar used the same version of the same mappings.
 * 
 * This class defines a relationship between an an "input" configuration (ex "modImplementation"),
 * where developers are free to pour modded artifacts, and an "output" configuration
 * (ex "modImplementationNamed"), which is where artifacts turn up after being remapped.
 * 
 * This class does not manage the relationship between the output configuration and any existing Gradle
 * configs (like "implementation"). Users of this class should set that relationship up themself.
 * 
 * Additionally, there's two other miscellaneous bits of functionality:
 * - `mavenScope`, if nonnull, applies the selected Maven scope to artifacts in the *input* configuration.
 *     This makes it so a "modRuntime" dependency turns up in your maven POM with "runtime" scope, for example.
 * 
 * - `copyToFolder`, if nonnull, will copy artifacts from the *output* configuration to a folder inside run configs.
 *     This is mainly to work around Forge being unable to find coremods on the classpath,
 *     so putting coremods in "runtime" or "implementation" doesn't work.
 */
public class RemappedConfigurationEntry implements Named {
	/** Creating with live Configuration objects */
	public RemappedConfigurationEntry(Configuration inputConfig, Configuration outputConfig) {
		this.inputConfig = inputConfig;
		this.outputConfig = outputConfig;
	}
	
	/** Convenience to create with Strings, that defines new configurations for you */
	public RemappedConfigurationEntry(Project project, String inputConfigName, String outputConfigName) {
		this.inputConfig = project.getConfigurations().maybeCreate(inputConfigName).setTransitive(true);
		this.outputConfig = project.getConfigurations().maybeCreate(outputConfigName).setTransitive(false);
	}
	
	/** And further conveniences for deriving the output config name by suffixing the input config name */
	public RemappedConfigurationEntry(Project project, String inputConfigName) {
		this(project, inputConfigName, inputConfigName + "Named");
	}
	
	public RemappedConfigurationEntry(Project project, Configuration inputConfig) {
		this(inputConfig, project.getConfigurations().maybeCreate(inputConfig.getName() + "Named").setTransitive(false));
	}
	
	public final Configuration inputConfig;
	public final Configuration outputConfig;
	
	private @Nullable String mavenScope;
	private @Nullable String copyToFolder;
	
	public Configuration getInputConfig() {
		return inputConfig;
	}
	
	public Configuration getOutputConfig() {
		return outputConfig;
	}
	
	@Nullable
	public String getMavenScope() {
		return mavenScope;
	}
	
	public RemappedConfigurationEntry mavenScope(@Nullable String mavenScope) {
		this.mavenScope = mavenScope;
		return this;
	}
	
	@Nullable
	public String getCopyToFolder() {
		return copyToFolder;
	}
	
	public RemappedConfigurationEntry copyToFolder(@Nullable String copyToFolder) {
		this.copyToFolder = copyToFolder;
		return this;
	}
	
	@Override
	public String getName() {
		return inputConfig.getName();
	}
	
	//Note that this doesn't copy any setup done relating to `outputConfig`, like setting an unrelated configuration to extend from it.
	public RemappedConfigurationEntry copy() {
		return new RemappedConfigurationEntry(inputConfig, outputConfig).copyToFolder(copyToFolder).mavenScope(mavenScope);
	}
}

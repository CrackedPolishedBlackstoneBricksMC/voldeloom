package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * Just wraps the Forge dependency.
 */
public class ForgeProvider extends DependencyProvider {
	public ForgeProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path forge;
	private String forgeVersion;
	
	public void decorateProject() throws Exception {
		DependencyInfo forgeDependency = getSingleDependency(Constants.FORGE);
		forge = forgeDependency.resolveSinglePath();
		forgeVersion = forgeDependency.getDependency().getVersion();
		
		project.getLogger().lifecycle("] forge jar is at: " + forge);
		
		installed = true;
	}
	
	public Path getJar() {
		return forge;
	}
	
	public String getVersion() {
		return forgeVersion;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.emptyList(); //It's a normal gradle dependency so gradle will refresh it on its own
	}
}

package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.nio.file.Path;

/**
 * Just wraps the Forge dependency.
 */
public class ForgeProvider extends DependencyProvider {
	@Inject
	public ForgeProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path forge;
	private String forgeVersion;
	
	@Override
	protected void performSetup() throws Exception {
		DependencyInfo forgeDependency = getSingleDependency(Constants.FORGE);
		forge = forgeDependency.resolveSinglePath();
		forgeVersion = forgeDependency.getDependency().getVersion();
		
		project.getLogger().lifecycle("] forge jar is at: " + forge);
		
		//No need to clean it manually on refreshDependencies, it's a normal gradle dependency, gradle will refresh it on its own
	}
	
	public void performInstall() throws Exception {
		//No processing to do
	}
	
	public Path getJar() {
		return forge;
	}
	
	public String getVersion() {
		return forgeVersion;
	}
}

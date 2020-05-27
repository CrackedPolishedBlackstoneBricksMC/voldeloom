package net.fabricmc.loom.providers;

import java.nio.file.Path;
import java.util.function.Consumer;
import org.gradle.api.Project;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;

public class ForgePatchProvider extends DependencyProvider {
	private String version;
	private Path installer;
	
	public ForgePatchProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		version = dependency.getResolvedVersion();
		installer = dependency.resolveFile().get().toPath();
	}

	@Override
	public String getTargetConfig() {
		return Constants.FORGE_PATCHES;
	}

	public String getForgeVersion() {
		return version;
	}
	
	public Path getInstaller() {
		return installer;
	}
}

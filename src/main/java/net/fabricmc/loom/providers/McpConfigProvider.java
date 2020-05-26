package net.fabricmc.loom.providers;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.gradle.api.Project;
import net.fabricmc.loom.util.DependencyProvider;

public class McpConfigProvider extends DependencyProvider {
	private Path extracted;
	
	public McpConfigProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws IOException {
		extracted = getExtension().getUserCache().toPath().resolve("mappings").resolve(dependency.getDepString().split(":")[2] + ".tsrg");
		if(!Files.exists(extracted)) {
			try(FileSystem mcpFs = FileSystems.newFileSystem(extracted, null)) {
				Files.copy(mcpFs.getPath("config", "joined.tsrg"), extracted);
			}
		}
	}
	
	@Override
	public String getTargetConfig() {
		return "mcpconfig";
	}
	
	public Path getTsrgPath() {
		return extracted;
	}
}

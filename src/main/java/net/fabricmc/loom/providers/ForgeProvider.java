package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.forge.ForgeATConfig;
import net.fabricmc.loom.util.Constants;
import org.gradle.api.Project;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collections;

public class ForgeProvider extends DependencyProvider {
	public ForgeProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private File forge;
	private String forgeVersion;
	private ForgeATConfig unmappedAts;
	
	@Override
	public void decorateProject() throws Exception {
		DependencyInfo forgeDependency = getSingleDependency(Constants.FORGE);
		forge = forgeDependency.resolveSingleFile().orElseThrow(() -> new RuntimeException("No forge dep!"));
		forgeVersion = forgeDependency.getDependency().getVersion();
		
		project.getLogger().lifecycle("|-> Parsing Forge and FML's access transformers...");
		unmappedAts = new ForgeATConfig();
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toURI()), Collections.emptyMap())) {
			unmappedAts.load(Files.newInputStream(zipFs.getPath("fml_at.cfg")));
			unmappedAts.load(Files.newInputStream(zipFs.getPath("forge_at.cfg")));
		}
		project.getLogger().lifecycle("|-> AT parse success! :)");
	}
	
	public File getForge() {
		return forge;
	}
	
	public String getForgeVersion() {
		return forgeVersion;
	}
	
	public ForgeATConfig getUnmappedAts() {
		return unmappedAts;
	}
}

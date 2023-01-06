package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.ForgeAccessTransformerSet;
import org.gradle.api.Project;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public class ForgeProvider extends DependencyProvider {
	public ForgeProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path forge;
	private String forgeVersion;
	private ForgeAccessTransformerSet unmappedAts;
	
	public void decorateProject() throws Exception {
		DependencyInfo forgeDependency = getSingleDependency(Constants.FORGE);
		forge = forgeDependency.resolveSinglePath().orElseThrow(() -> new RuntimeException("No forge dep!"));
		forgeVersion = forgeDependency.getDependency().getVersion();
		
		project.getLogger().lifecycle("|-> Parsing Forge and FML's access transformers...");
		unmappedAts = new ForgeAccessTransformerSet();
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toUri()), Collections.emptyMap());
		    InputStream fmlAt = new BufferedInputStream(Files.newInputStream(zipFs.getPath("fml_at.cfg")));
		    InputStream forgeAt = new BufferedInputStream(Files.newInputStream(zipFs.getPath("forge_at.cfg"))))
		{
			unmappedAts.load(fmlAt);
			unmappedAts.load(forgeAt);
		}
		project.getLogger().lifecycle("|-> AT parse success! :)");
		
		installed = true;
	}
	
	public Path getJar() {
		return forge;
	}
	
	public String getVersion() {
		return forgeVersion;
	}
	
	public ForgeAccessTransformerSet getUnmappedAccessTransformers() {
		return unmappedAts;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.singleton(forge);
	}
}

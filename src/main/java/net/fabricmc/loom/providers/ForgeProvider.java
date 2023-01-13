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

/**
 * Just wraps the Forge dependency.
 * 
 * Why are access transformers read here? Good question!
 */
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
		
		project.getLogger().info("] Forge: " + forge);
		
		//TODO: move to the access txd provider lol
		
		project.getLogger().lifecycle("|-> Parsing Forge and FML's access transformers...");
		unmappedAts = new ForgeAccessTransformerSet();
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toUri()), Collections.emptyMap())) {
			//TODO: where do these names come from, can they be read from the jar?
			// 1.2.5 does not have these files
			
			Path fmlAtPath = zipFs.getPath("fml_at.cfg");
			if(Files.exists(fmlAtPath)) {
				project.getLogger().info("|-> Loading fml_at.cfg");
				try(InputStream fmlAt = new BufferedInputStream(Files.newInputStream(fmlAtPath))) {
					unmappedAts.load(fmlAt);
				}
			} else {
				project.getLogger().info("|-> No fml_at.cfg in this jar.");
			}
			
			Path forgeAtPath = zipFs.getPath("forge_at.cfg");
			if(Files.exists(forgeAtPath)) {
				project.getLogger().info("|-> Loading forge_at.cfg");
				try(InputStream forgeAt = new BufferedInputStream(Files.newInputStream(forgeAtPath))) {
					unmappedAts.load(forgeAt);
				}
			} else {
				project.getLogger().info("|-> No forge_at.cfg in this jar.");
			}
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

package net.fabricmc.loom.forge;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.tinyremapper.IMappingProvider;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collections;

public class ForgeProvider extends DependencyProvider {
	private File forge;
	private ForgeATConfig atConfig;
	private String forgeVersion;
	
	public ForgeProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}

	public File getForge() {
		return forge;
	}
	
	public String getForgeVersion() {
		return forgeVersion;
	}
	
	public ForgeATConfig getATs() {
		return atConfig;
	}
	
	@Override
	public void decorateProject() throws Exception {
		DependencyInfo forgeDependency = getSingleDependency(Constants.FORGE);
		
		atConfig = new ForgeATConfig();
		forgeVersion = forgeDependency.getDependency().getVersion();
		forge = forgeDependency.resolveSingleFile().orElseThrow(() -> new RuntimeException("No forge dep!"));
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toURI()), Collections.emptyMap())) {
			atConfig.load(Files.newInputStream(zipFs.getPath("fml_at.cfg")));
			atConfig.load(Files.newInputStream(zipFs.getPath("forge_at.cfg")));
		}
	}
	
	public void mapForge() throws IOException {
		String fromM = "official";
		String toM = "named";

		MappingsProvider mappingsProvider = extension.getDependencyManager().getMappingsProvider();

		IMappingProvider mappings = TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false);
		mappings.load(atConfig);
		atConfig.finishRemapping();
	}
}

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
import java.util.Map;

public class ForgeProvider extends DependencyProvider {
	
	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	
	private File forge;
	private ForgeATConfig atConfig;
	private String forgeVersion;
	
	public ForgeProvider(Project project) {
		super(project);
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
	public void provide(DependencyInfo dependency) throws Exception {
		atConfig = new ForgeATConfig();
		forgeVersion = dependency.getDependency().getVersion();
		forge = dependency.resolveFile().orElseThrow(() -> new RuntimeException("No forge dep!"));
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toURI()), FS_ENV)) {
			atConfig.load(Files.newInputStream(zipFs.getPath("fml_at.cfg")));
			atConfig.load(Files.newInputStream(zipFs.getPath("forge_at.cfg")));
		}
	}
	
	public void mapForge() throws IOException {
		LoomGradleExtension extension = getExtension();
		String fromM = "official";
		String toM = "named";

		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		IMappingProvider mappings = TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false);
		mappings.load(atConfig);
		atConfig.finishRemapping();
	}
	
	@Override
	public String getTargetConfig() {
		return Constants.FORGE;
	}
}

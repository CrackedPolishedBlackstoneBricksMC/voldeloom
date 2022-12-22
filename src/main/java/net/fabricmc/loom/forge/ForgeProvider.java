package net.fabricmc.loom.forge;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
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
import java.util.function.Consumer;

public class ForgeProvider extends DependencyProvider {
	
	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	
	private File forge;
	private File mappedForge;
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
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		atConfig = new ForgeATConfig();
		forgeVersion = dependency.getDependency().getVersion();
		forge = dependency.resolveFile().orElseThrow(() -> new RuntimeException("No forge dep!"));
		try(FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + forge.toURI()), FS_ENV)) {
			atConfig.load(Files.newInputStream(zipFs.getPath("fml_at.cfg")));
			atConfig.load(Files.newInputStream(zipFs.getPath("forge_at.cfg")));
			zipFs.close();
		}
		//remap(dependency.resolveFile().orElseThrow(() -> new RuntimeException("No forge dep!")), mappedForge);
		
		
		//addDependency(mappedForge, Constants.FORGE_MAPPED);
	}
	
	public void mapForge() throws IOException {
		LoomGradleExtension extension = getExtension();
		String fromM = "official";
		String toM = "named";

		MinecraftProvider mcProvider = extension.getMinecraftProvider();
		MinecraftMappedProvider mappedProvider = extension.getMinecraftMappedProvider();
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		
		//Path inputPath = forge.toPath();
		//Path mc = mcProvider.getMergedJar().toPath();
		//Path[] mcDeps = mappedProvider.getMapperPaths().stream().map(File::toPath).toArray(Path[]::new);

		//getProject().getLogger().lifecycle(":remapping forge (TinyRemapper, " + fromM + " -> " + toM + ")");

		IMappingProvider mappings = TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false);
		
		/*TinyRemapper remapper = TinyRemapper.newRemapper()
						.withMappings(mappings)
						.renameInvalidLocals(true)
						.ignoreFieldDesc(true)
						.build();
		OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(mappedForge.toPath()).build();
		try {
			outputConsumer.addNonClassFiles(inputPath);
			remapper.readClassPath(mc);
			remapper.readClassPath(mcDeps);
			remapper.readInputs(inputPath);
			remapper.apply(outputConsumer);
		} finally {
			outputConsumer.close();
			remapper.finish();
		}*/
		mappings.load(atConfig);
		atConfig.finishRemapping();
	}

	/*private void remap(File input, File output) throws IOException {
		LoomGradleExtension extension = getExtension();
		String fromM = "official";
		String toM = "mcp";

		MinecraftMappedProvider mappedProvider = extension.getMinecraftMappedProvider();
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		Path inputPath = input.getAbsoluteFile().toPath();
		Path mc = mappedProvider.getIntermediaryJar().toPath();
		Path[] mcDeps = mappedProvider.getMapperPaths().stream().map(File::toPath).toArray(Path[]::new);

		getProject().getLogger().lifecycle(":remapping forge (TinyRemapper, " + fromM + " -> " + toM + ")");

		TinyRemapper remapper = TinyRemapper.newRemapper()
						.withMappings(TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false))
						.renameInvalidLocals(true)
						.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(Paths.get(output.getAbsolutePath())).build()) {
			outputConsumer.addNonClassFiles(inputPath);
			remapper.readClassPath(mc);
			remapper.readClassPath(mcDeps);
			remapper.readInputs(inputPath);
			remapper.apply(outputConsumer);
		} finally {
			remapper.finish();
		}

		if (!output.exists()) {
			throw new RuntimeException("Failed to remap JAR to " + toM + " file not found: " + output.getAbsolutePath());
		}
	}*/
	
	@Override
	public String getTargetConfig() {
		return Constants.FORGE;
	}

}

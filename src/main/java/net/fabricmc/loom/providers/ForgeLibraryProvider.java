package net.fabricmc.loom.providers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.cadixdev.atlas.Atlas;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgMappingFormat;
import org.gradle.api.Project;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;

public class ForgeLibraryProvider extends DependencyProvider {
	
	public ForgeLibraryProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws IOException {
		/*McpConfigProvider mcp = getExtension().getDependencyManager().getProvider(McpConfigProvider.class);
		MappingsProvider mappings = getExtension().getMappingsProvider();
		MinecraftProvider mc = getExtension().getMinecraftProvider();
		Path forge = dependency.resolveFile().get().toPath();
		
		Path obf = getJar("official", dependency.getResolvedVersion());
		Path mapped = getJar(mappings.mappingsName + "-" + mappings.mappingsVersion, dependency.getResolvedVersion());
		dependency.getDependency()
		
		if(!Files.exists(mapped)) {
			if(!Files.exists(obf)) {
				try(Atlas atlas = new Atlas()) {
					MappingSet srgObf = new TSrgMappingFormat().read(mcp.getTsrgPath()).reverse();
					atlas.use(mc.getSrgForgeJar());
					atlas.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(srgObf, ctx.inheritanceProvider())));
					atlas.run(forge, obf);
				}
			}
			
			
		}*/
	}
	
	private Path getJar(String mappingStage, String version) {
		return getExtension().getUserCache().toPath().resolve(String.format("forgelib-%s-%s.jar", version, mappingStage));
	}

	@Override
	public String getTargetConfig() {
		return null;//return Constants.FORGE_LIBRARY;
	}

}

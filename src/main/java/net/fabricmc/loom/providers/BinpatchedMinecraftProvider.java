package net.fabricmc.loom.providers;

import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.Binpatch;
import net.fabricmc.loom.util.mcp.BinpatchesPack;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;

public class BinpatchedMinecraftProvider extends DependencyProvider {
	@Inject
	public BinpatchedMinecraftProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc, ForgeProvider forge) {
		super(project, extension);
		
		this.mc = mc;
		this.forge = forge;
		
		dependsOn(mc, forge);
	}
	
	private final MinecraftProvider mc;
	private final ForgeProvider forge;
	
	private Path binpatchedClient;
	private Path binpatchedServer;
	
	private boolean binpatchesExist = false;
	
	@Override
	protected void performSetup() throws Exception {
		binpatchedClient = getCacheDir().resolve("minecraft-" + mc.getVersion() + "-client-binpatched.jar");
		binpatchedServer = getCacheDir().resolve("minecraft-" + mc.getVersion() + "-server-binpatched.jar");
		
		cleanOnRefreshDependencies(binpatchedClient, binpatchedServer);
	}
	
	@Override
	protected void performInstall() throws Exception {
		if(extension.refreshDependencies || Files.notExists(binpatchedClient) || Files.notExists(binpatchedServer)) {
			Files.deleteIfExists(binpatchedClient);
			Files.deleteIfExists(binpatchedServer);
			
			try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getJar().toUri()), Collections.emptyMap())) {
				Path binpatchesPath = forgeFs.getPath("binpatches.pack.lzma");
				if(Files.exists(binpatchesPath)) {
					project.getLogger().lifecycle("|-> Found binpatches.pack.lzma. Reading...");
					
					binpatchesExist = true;
					
					BinpatchesPack binpatches = new BinpatchesPack().read(project, binpatchesPath);
					
					project.getLogger().lifecycle("|-> Found {} client and {} server binpatches.", binpatches.clientBinpatches.size(), binpatches.serverBinpatches.size());
					
					try(
						FileSystem vanillaClientFs = FileSystems.newFileSystem(URI.create("jar:" + mc.getClientJar().toUri()), Collections.emptyMap());
						FileSystem vanillaServerFs = FileSystems.newFileSystem(URI.create("jar:" + mc.getServerJar().toUri()), Collections.emptyMap());
						FileSystem patchedClientFs = FileSystems.newFileSystem(URI.create("jar:" + binpatchedClient.toUri()), Collections.singletonMap("create", "true"));
						FileSystem patchedServerFs = FileSystems.newFileSystem(URI.create("jar:" + binpatchedServer.toUri()), Collections.singletonMap("create", "true"))
					) {
						project.getLogger().lifecycle("|-> Patching client...");
						patch(vanillaClientFs, patchedClientFs, binpatches.clientBinpatches);
						project.getLogger().lifecycle("|-> Patching server...");
						patch(vanillaServerFs, patchedServerFs, binpatches.serverBinpatches);
						project.getLogger().lifecycle("|-> Somehow, it worked!");
					}
				} else {
					project.getLogger().lifecycle("|-> No binpatches.pack.lzma found, will patch like a jarmod later.");
					Files.copy(mc.getClientJar(), binpatchedClient, StandardCopyOption.REPLACE_EXISTING);
					Files.copy(mc.getServerJar(), binpatchedServer, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}
	
	private void patch(FileSystem vanillaFs, FileSystem patchedFs, Map<String, Binpatch> binpatches) throws Exception {
		Files.walkFileTree(vanillaFs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(patchedFs.getPath(vanillaPath.toString()));
				return FileVisitResult.CONTINUE;
			}
			
			@Override
			public FileVisitResult visitFile(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
				Path patchedPath = patchedFs.getPath(vanillaPath.toString());
				String filename = vanillaPath.toString().substring(1); //remove leading slash
				
				if(filename.endsWith(".class")) {
					Binpatch binpatch = binpatches.get(filename.substring(0, filename.length() - ".class".length()));
					if(binpatch != null) {
						project.getLogger().info("Binpatching {}...", filename);
						Files.write(patchedPath, binpatch.apply(Files.readAllBytes(vanillaPath)));
						return FileVisitResult.CONTINUE;
					}
				}
				
				Files.copy(vanillaPath, patchedPath);
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	public Path getBinpatchedClient() {
		return binpatchedClient;
	}
	
	public Path getBinpatchedServer() {
		return binpatchedServer;
	}
	
	public boolean usesBinpatches() {
		return binpatchesExist;
	}
}

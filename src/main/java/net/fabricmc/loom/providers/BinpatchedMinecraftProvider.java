package net.fabricmc.loom.providers;

import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.BinpatchesPack;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

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
			try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getJar().toUri()), Collections.emptyMap())) {
				Path binpatchesPath = forgeFs.getPath("binpatches.pack.lzma");
				if(Files.exists(binpatchesPath)) {
					project.getLogger().info("|-> Found binpatches.pack.lzma.");
					
					binpatchesExist = true;
					
					//read binpatches
					BinpatchesPack binpatches = new BinpatchesPack().read(project, binpatchesPath);
					
					try(
						FileSystem vanillaClientFs = FileSystems.newFileSystem(URI.create("jar:" + mc.getClientJar().toUri()), Collections.emptyMap());
						FileSystem vanillaServerFs = FileSystems.newFileSystem(URI.create("jar:" + mc.getServerJar().toUri()), Collections.emptyMap());
						FileSystem patchedClientFs = FileSystems.newFileSystem(URI.create("jar:" + binpatchedClient.toUri()), Collections.singletonMap("create", "true"));
						FileSystem patchedServerFs = FileSystems.newFileSystem(URI.create("jar:" + binpatchedServer.toUri()), Collections.singletonMap("create", "true"))
					) {
						//(perform binpatching)
					}
				} else {
					project.getLogger().info("|-> No binpatches.pack.lzma found, will patch like a jarmod later.");
					Files.copy(mc.getClientJar(), binpatchedClient, StandardCopyOption.REPLACE_EXISTING);
					Files.copy(mc.getServerJar(), binpatchedServer, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		
		//todo delete
		Files.copy(mc.getClientJar(), binpatchedClient, StandardCopyOption.REPLACE_EXISTING);
		Files.copy(mc.getServerJar(), binpatchedServer, StandardCopyOption.REPLACE_EXISTING);
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

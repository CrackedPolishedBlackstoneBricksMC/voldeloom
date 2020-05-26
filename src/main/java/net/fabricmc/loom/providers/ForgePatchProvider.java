package net.fabricmc.loom.providers;

import java.nio.file.Path;
import java.util.function.Consumer;
import org.gradle.api.Project;

import net.fabricmc.loom.util.DependencyProvider;

public class ForgePatchProvider extends DependencyProvider {
	private String version;
	private Path installer;
	
	public ForgePatchProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		version = dependency.getResolvedVersion();
		installer = dependency.resolveFile().get().toPath();
		/*clientPatches = getExtension().getUserCache().toPath().resolve("forge-patches").resolve(version + "-client.lzma");
		serverPatches = getExtension().getUserCache().toPath().resolve("forge-patches").resolve(version + "-server.lzma");
		
		if(!Files.exists(clientPatches) || !Files.exists(serverPatches)) {
			Files.createDirectories(clientPatches.getParent());
			try(FileSystem forgeInstallerFs = FileSystems.newFileSystem(dependency.resolveFile().get().toPath(), null)) {
				if(!Files.exists(clientPatches)) {
					Files.copy(forgeInstallerFs.getPath("data", "client.lzma"), clientPatches);
				}
				if(!Files.exists(serverPatches)) {
					Files.copy(forgeInstallerFs.getPath("data", "server.lzma"), serverPatches);
				}
			}
			/*Set<Patch> patches = new PatchSet();
			try(FileSystem forgeInstallerFs = FileSystems.newFileSystem(dependency.resolveFile().get().toPath(), null)) {
				loadPatches(patches, forgeInstallerFs.getPath("data", "client.lzma"));
				loadPatches(patches, forgeInstallerFs.getPath("data", "server.lzma"));
			}
			
			Files.createFile(mergedPatches);
			try(OutputStream rawOut = Files.newOutputStream(mergedPatches)) {
				ZipOutputStream zipOut = new ZipOutputStream(new LzmaOutputStream.Builder(new BufferedOutputStream(rawOut)).useEndMarkerMode(true).build());
				for(Patch p : patches) {
					byte[] bytes = p.toBytes();
					ZipEntry zEntry = new ZipEntry(p.srg.replace('/', '.'));
					zEntry.setSize(bytes.length);
					zipOut.putNextEntry(zEntry);
					IOUtils.write(bytes, zipOut);
				}
			} finally {
				Files.deleteIfExists(mergedPatches);
			}*/
		//}
	}
	
	/*private void loadPatches(Set<Patch> patches, Path from) throws IOException {
		try(InputStream rawIn = Files.newInputStream(from)) {
			JarInputStream in = new JarInputStream(new LzmaInputStream(new BufferedInputStream(rawIn), new Decoder()));
			while(in.getNextJarEntry() != null) {
				patches.add(Patch.from(in));
			}
		}
	}*/

	@Override
	public String getTargetConfig() {
		return "forge";
	}

	public String getForgeVersion() {
		return version;
	}
	
	public Path getInstaller() {
		return installer;
	}
	
	/*public Path getPatches(String type) {
		if(type.equals("client")) {
			return clientPatches;
		} else if(type.equals("server")) {
			return serverPatches;
		}
		throw new IllegalArgumentException(type + " is not a forge patch side");
	}*/
}

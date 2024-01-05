package net.fabricmc.loom.newprovider;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.Binpatch;
import net.fabricmc.loom.util.Suppliers;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;

public class Binpatcher extends NewProvider<Binpatcher> {
	public Binpatcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}

	private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
	
	//inputs
	private Path client, server, forge;
	private String binpatchedClientName, binpatchedServerName;
	
	public Binpatcher client(Path client) {
		this.client = client;
		props.put("client-path", client.toAbsolutePath().toString());
		return this;
	}
	
	public Binpatcher server(Path server) {
		this.server = server;
		props.put("server-path", server.toAbsolutePath().toString());
		return this;
	}
	
	public Binpatcher forge(Path forge) {
		this.forge = forge;
		props.put("forge-path", forge.toAbsolutePath().toString());
		return this;
	}
	
	public Binpatcher binpatchedClientName(String binpatchedClientName) {
		this.binpatchedClientName = binpatchedClientName;
		return this;
	}
	
	public Binpatcher binpatchedServerName(String binpatchedServerName) {
		this.binpatchedServerName = binpatchedServerName;
		return this;
	}
	
	//outputs
	private Path binpatchedClient, binpatchedServer;
	
	public Path getBinpatchedClient() {
		return binpatchedClient;
	}
	
	public Path getBinpatchedServer() {
		return binpatchedServer;
	}
	
	//process
	public Binpatcher binpatch() throws Exception {
		try(FileSystem forgeFs = ZipUtil.openFs(forge)) {
			//There's three cases:
			//1. This version of Forge does not use binpatches. (supplier == null).
			//2. Forge uses binpatches, but that's all I care to know because the binpatched files already exist. (supplier != null, goes uncalled)
			//3. Forge uses binpatches, but the binpatched files don't exist yet, so I actually need to parse the binpatches too. (supplier != null, called)
			@Nullable Supplier<Binpatch.Pack> binpatchesSupplier;
			
			Path binpatchesPath = forgeFs.getPath("binpatches.pack.lzma");
			if(Files.exists(binpatchesPath)) {
				log.lifecycle("|-> Yes, this version of Forge does contain binpatches.");
				props.put("has-binpatches", "yes");
				props.put("binpatch-algo", "2"); //i changed a decent bit of binpatch code, want to make sure it gets tested
				binpatchesSupplier = Suppliers.memoize(() -> { //<- memoized!
					log.lifecycle("\\-> Parsing binpatches...");
					return new Binpatch.Pack().read(binpatchesPath);
				});
			} else {
				log.lifecycle("|-> No, this version of Forge does not contain binpatches.");
				props.put("has-binpatches", "no");
				binpatchesSupplier = null;
			}
			
			if(binpatchesSupplier == null) {
				binpatchedClient = client;
				binpatchedServer = server;
			} else {
				binpatchedClient = getOrCreate(getCacheDir().resolve(props.subst(binpatchedClientName)), dest -> doPatch(dest, true, binpatchesSupplier));
				binpatchedServer = getOrCreate(getCacheDir().resolve(props.subst(binpatchedServerName)), dest -> doPatch(dest, false, binpatchesSupplier));
			}
		}
		
		return this;
	}
	
	private void doPatch(Path output, boolean client, @Nonnull Supplier<Binpatch.Pack> binpatchesPackSupplier) throws Exception {
		Binpatch.Pack binpatchesPack = binpatchesPackSupplier.get();

		Binpatch.Patchset patchset = client ? binpatchesPack.client : binpatchesPack.server;
		Path input = client ? this.client : this.server;

		//populate outputFs with
		// 1. files that don't have any corresponding patches
		// 2. patched versions of files that do have a corresponding patch
		log.lifecycle("\\-> Applying {} modifications to {} (and copying unpatched data)...", patchset.modificationCount, output);
		try(FileSystem inputFs = ZipUtil.openFs(input); FileSystem outputFs = ZipUtil.createFs(output)) {
			Files.walkFileTree(inputFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
					Files.createDirectories(outputFs.getPath(vanillaPath.toString()));
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
					String filename = vanillaPath.toString();
					Path patchedPath = outputFs.getPath(filename);

					if(filename.endsWith(".class")) {
						//just guess the vanilla class internal name from its filename (maybe a little lazy)
						String vanillaClassInternalName = filename.substring(
							1, //remove leading slash
							filename.length() - ".class".length() //remove file extension
						);

						List<Binpatch> patches = patchset.getPatchesFor(vanillaClassInternalName);
						if(!patches.isEmpty()) {
							if(patches.size() != 1) {
								log.lifecycle("Found multiple patches ({}) for '{}'. Huh! That's interesting!", patches.size(), vanillaClassInternalName);
							}

							log.info("Binpatching {}...", filename);
							Files.write(patchedPath, applyPatchSequence(Files.readAllBytes(vanillaPath), patches));
							return FileVisitResult.CONTINUE;
						}
					}

					Files.copy(vanillaPath, patchedPath);
					return FileVisitResult.CONTINUE;
				}
			});

			//3. files that don't exist in the source jar at all. forge patches them in from thin air.
			log.lifecycle("\\-> Applying {} additions to {}...", patchset.getAdditions().size(), output);
			for(Binpatch addPatch : patchset.getAdditions()) {
				log.info("Binpatching (!existsAtTarget) {}...", addPatch.sourceClassName);

				//guess the destination filename.
				// (usage of .sourceClassName instead of .targetClassName is correct, we're keeping things unmapped for now.)
				String[] split = addPatch.sourceClassName.split("\\.");
				split[split.length - 1] += ".class";
				Path patchedPath = outputFs.getPath("/", split);

				if(Files.exists(patchedPath)) {
					log.warn("Binpatch with 'existsAtTarget = false' for a file that does indeed exist at the target, {}", addPatch.originalFilename);
				} else {
					if(patchedPath.getParent() != null) Files.createDirectories(patchedPath.getParent());
					Files.write(patchedPath, addPatch.apply(EMPTY_BYTE_ARRAY));
				}
			}
		}
		
		log.lifecycle("|-> Binpatch success.");
	}

	private static byte[] applyPatchSequence(byte[] input, List<Binpatch> patches) {
		for(Binpatch patch : patches) {
			input = patch.apply(input);
		}
		return input;
	}
}

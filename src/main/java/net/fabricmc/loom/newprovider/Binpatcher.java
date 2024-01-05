package net.fabricmc.loom.newprovider;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.Binpatch;
import net.fabricmc.loom.mcp.BinpatchesPack;
import net.fabricmc.loom.util.Suppliers;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;

public class Binpatcher extends NewProvider<Binpatcher> {
	public Binpatcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
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
			@Nullable Supplier<BinpatchesPack> binpatchesSupplier;
			
			Path binpatchesPath = forgeFs.getPath("binpatches.pack.lzma");
			if(Files.exists(binpatchesPath)) {
				log.lifecycle("|-> Yes, this version of Forge does contain binpatches.");
				props.put("has-binpatches", "yes");
				binpatchesSupplier = Suppliers.memoize(() -> { //<- memoized!
					log.lifecycle("\\-> Parsing binpatches...");
					return new BinpatchesPack().read(log, binpatchesPath);
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
	
	private void doPatch(Path output, boolean client, @Nonnull Supplier<BinpatchesPack> binpatchesPackSupplier) throws Exception {
		BinpatchesPack binpatchesPack = binpatchesPackSupplier.get();
		Map<String, Binpatch> binpatches = client ? binpatchesPack.clientBinpatches : binpatchesPack.serverBinpatches;
		Path input = client ? this.client : server;
		
		try(FileSystem inputFs = ZipUtil.openFs(input); FileSystem outputFs = ZipUtil.createFs(output)) {
			Set<Binpatch> unusedBinpatches = new HashSet<>(binpatches.values());
			
			Files.walkFileTree(inputFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
					Files.createDirectories(outputFs.getPath(vanillaPath.toString()));
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path vanillaPath, BasicFileAttributes attrs) throws IOException {
					Path patchedPath = outputFs.getPath(vanillaPath.toString());
					String filename = vanillaPath.toString().substring(1); //remove leading slash
					
					if(filename.endsWith(".class")) {
						Binpatch binpatch = binpatches.get(filename.substring(0, filename.length() - ".class".length()));
						if(binpatch != null) {
							log.debug("Binpatching {}...", filename);
							Files.write(patchedPath, binpatch.apply(Files.readAllBytes(vanillaPath)));
							unusedBinpatches.remove(binpatch);
							return FileVisitResult.CONTINUE;
						}
					}
					
					Files.copy(vanillaPath, patchedPath);
					return FileVisitResult.CONTINUE;
				}
			});
			
			for(Binpatch unusedPatch : unusedBinpatches) {
				if(unusedPatch.existsAtTarget) {
					log.warn("Unused binpatch with 'existsAtTarget = true', {}", unusedPatch.originalFilename);
				} else {
					log.debug("Binpatching (!existsAtTarget) {}...", unusedPatch.sourceClassName);
					
					String[] split = unusedPatch.sourceClassName.split("\\.");
					split[split.length - 1] += ".class";
					Path path = outputFs.getPath("/", split);
					
					if(Files.exists(path)) {
						log.warn("Unused binpatch with 'existsAtTarget = false' for a file that already exists, {}", unusedPatch.originalFilename);
					} else {
						if(path.getParent() != null) Files.createDirectories(path.getParent());
						Files.write(path, unusedPatch.apply(new byte[0]));
					}
				}
			}
		}
		
		log.info("|-> Binpatch success.");
	}
}

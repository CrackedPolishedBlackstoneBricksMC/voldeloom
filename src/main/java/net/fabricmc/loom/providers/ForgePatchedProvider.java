package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
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
import java.util.Collection;
import java.util.Collections;

/**
 * Installs Forge inside of the Minecraft merged jar. The post-installation jar is available with {@code getPatchedJar()}.
 * <p>
 * Also performs the, uh, critically important task of gluing the Minecraft version number onto the Forge version number.
 * This version tag is used in a few places.
 * <p>
 * During this period, Forge was installable as a jarmod. This class is simply a programattic version of "deleting META-INF". 
 */
public class ForgePatchedProvider extends DependencyProvider {
	@Inject
	public ForgePatchedProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc, MergedProvider merged, ForgeProvider forge) {
		super(project, extension);
		this.mc = mc;
		this.merged = merged;
		this.forge = forge;
	}
	
	private final MinecraftProvider mc;
	private final MergedProvider merged;
	private final ForgeProvider forge;
	
	private String patchedVersionTag;
	private Path patched;
	
	@Override
	protected void performSetup() throws Exception {
		mc.tryReach(Stage.SETUP);
		merged.tryReach(Stage.SETUP);
		forge.tryReach(Stage.SETUP);
		
		patchedVersionTag = mc.getVersion() + "-forge-" + forge.getVersion();
		patched = WellKnownLocations.getUserCache(project).resolve("minecraft-" + patchedVersionTag + "-merged.jar");
		
		cleanIfRefreshDependencies();
	}
	
	public void performInstall() throws Exception {
		mc.tryReach(Stage.INSTALLED);
		merged.tryReach(Stage.INSTALLED);
		forge.tryReach(Stage.INSTALLED);
		
		project.getLogger().lifecycle("] patched jar is at: " + patched);
		if(Files.notExists(patched)) {
			project.getLogger().lifecycle("|-> Does not exist, performing patch...");
			
			try(
				FileSystem mergedFs = FileSystems.newFileSystem(URI.create("jar:" + merged.getMergedJar().toUri()), Collections.emptyMap());
				FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getJar().toUri()), Collections.emptyMap());
				FileSystem patchedFs = FileSystems.newFileSystem(URI.create("jar:" + patched.toUri()), Collections.singletonMap("create", "true")))
			{
				project.getLogger().lifecycle("|-> Copying vanilla into patched jar...");
				Files.walkFileTree(mergedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path sourceDir, BasicFileAttributes attrs) throws IOException {
						if(sourceDir.endsWith("META-INF")) {
							return FileVisitResult.SKIP_SUBTREE;
						} else {
							Path destDir = patchedFs.getPath(sourceDir.toString());
							Files.createDirectories(destDir);
							return FileVisitResult.CONTINUE;
						}
					}
					
					@Override
					public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
						Path destPath = patchedFs.getPath(sourcePath.toString());
						Files.copy(sourcePath, destPath);
						return FileVisitResult.CONTINUE;
					}
				});
				
				Path binpatchesPackLzma = forgeFs.getPath("binpatches.pack.lzma");
				if(Files.exists(binpatchesPackLzma)) {
					project.getLogger().lifecycle("|-> Found binpatches.pack.lzma.");
					performBinpatchesPatch(mergedFs, forgeFs, patchedFs, binpatchesPackLzma);
				} else {
					project.getLogger().lifecycle("|-> No binpatches.pack.lzma found, patching like a jarmod.");
					performJarmodPatch(forgeFs, patchedFs);
				}
			}
			
			project.getLogger().lifecycle("|-> Patch success!");
		}
	}
	
	private void performBinpatchesPatch(FileSystem mergedFs, FileSystem forgeFs, FileSystem patchedFs, Path binpatchesPackLzma) throws IOException {
		//TODO: This is the wrong place to do binpatches, they're client-only or server-only, not merged, I don't think
		// Need to study them more lol
		new BinpatchesPack().read(project, binpatchesPackLzma);
	}
	
	private void performJarmodPatch(FileSystem forgeFs, FileSystem patchedFs) throws IOException {
		project.getLogger().lifecycle("|-> Copying Forge over top of the patched jar...");
		Files.walkFileTree(forgeFs.getPath("/"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path sourceDir, BasicFileAttributes attrs) throws IOException {
				if(sourceDir.endsWith("META-INF")) {
					return FileVisitResult.SKIP_SUBTREE;
				} else {
					Path destDir = patchedFs.getPath(sourceDir.toString());
					Files.createDirectories(destDir);
					return FileVisitResult.CONTINUE;
				}
			}
			
			@Override
			public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
				Path destPath = patchedFs.getPath(sourcePath.toString());
				Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
		
		//No need to delete META-INF cause i didn't copy it in the first place ;)
	}
	
	public String getPatchedVersionTag() {
		return patchedVersionTag;
	}
	
	public Path getPatchedJar() {
		return patched;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.singleton(patched);
	}
}

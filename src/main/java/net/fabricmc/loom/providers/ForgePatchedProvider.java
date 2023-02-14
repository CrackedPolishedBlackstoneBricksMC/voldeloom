package net.fabricmc.loom.providers;

import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
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
	public ForgePatchedProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc, MergedProvider merged, ForgeProvider forge, BinpatchedMinecraftProvider binpatched) {
		super(project, extension);
		this.mc = mc;
		this.merged = merged;
		this.forge = forge;
		this.binpatched = binpatched;
		
		dependsOn(mc, merged, forge);
	}
	
	private final MinecraftProvider mc;
	private final MergedProvider merged;
	private final ForgeProvider forge;
	private final BinpatchedMinecraftProvider binpatched;
	
	private String patchedVersionTag;
	private Path patched;
	
	@Override
	protected void performSetup() throws Exception {
		patchedVersionTag = mc.getVersion() + "-forge-" + forge.getVersion();
		patched = getCacheDir().resolve("minecraft-" + patchedVersionTag + "-merged.jar");
		
		project.getLogger().lifecycle("] patched jar: {}", patched);
		
		cleanOnRefreshDependencies(patched);
	}
	
	public void performInstall() throws Exception {
		if(Files.notExists(patched)) {
			if(binpatched.usesBinpatches()) {
				project.getLogger().lifecycle("|-> Patched jar does not exist, but this Forge version used binpatches. Copying...");
				Files.copy(merged.getMergedJar(), patched);
			} else {
				project.getLogger().lifecycle("|-> Patched jar does not exist, performing jarmod-style patch...");
				
				try(FileSystem mergedFs  = FileSystems.newFileSystem(URI.create("jar:" + merged.getMergedJar().toUri()), Collections.emptyMap());
					  FileSystem forgeFs   = FileSystems.newFileSystem(URI.create("jar:" + forge.getJar().toUri()), Collections.emptyMap());
					  FileSystem patchedFs = FileSystems.newFileSystem(URI.create("jar:" + patched.toUri()), Collections.singletonMap("create", "true"))) {
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
					
					project.getLogger().lifecycle("|-> Copying Forge on top...");
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
				}
				
				project.getLogger().lifecycle("|-> Deleting META-INF... (just kidding, i didn't copy it in the first place)");
				project.getLogger().lifecycle("|-> Patch success!");
			}
		}
	}
	
	public String getPatchedVersionTag() {
		return patchedVersionTag;
	}
	
	public Path getPatchedJar() {
		return patched;
	}
}

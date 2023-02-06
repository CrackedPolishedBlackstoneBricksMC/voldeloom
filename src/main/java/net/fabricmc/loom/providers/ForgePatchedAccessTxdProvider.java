package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.mcp.ForgeAccessTransformerSet;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Applies Forge's access transformers on top of the patched Minecraft + Forge jar.
 * The access-transformed jar is accessible with {@code getTransformedJar()}.
 * <p>
 * Outside of development, this is normally done by Forge as it classloads Minecraft.
 */
public class ForgePatchedAccessTxdProvider extends DependencyProvider {
	@Inject
	public ForgePatchedAccessTxdProvider(Project project, LoomGradleExtension extension, ForgeProvider forge, ForgePatchedProvider forgePatched) {
		super(project, extension);
		this.forge = forge;
		this.forgePatched = forgePatched;
	}
	
	private final ForgeProvider forge;
	private final ForgePatchedProvider forgePatched;
	
	//setup
	private Path accessTransformedMc;
	
	@Override
	protected void performSetup() throws Exception {
		forge.tryReach(Stage.SETUP);
		forgePatched.tryReach(Stage.SETUP);
		
		accessTransformedMc = WellKnownLocations.getUserCache(project).resolve("minecraft-" + forgePatched.getPatchedVersionTag() + "-atd.jar");
		
		cleanIfRefreshDependencies();
	}
	
	public void performInstall() throws Exception {
		forge.tryReach(Stage.INSTALLED);
		forgePatched.tryReach(Stage.INSTALLED);
		
		project.getLogger().lifecycle("] access-transformed jar is at: {}", accessTransformedMc);
		if(Files.notExists(accessTransformedMc)) {
			project.getLogger().lifecycle("|-> Does not exist, parsing Forge's access transformers...");
			
			//Read forge ats
			ForgeAccessTransformerSet ats = new ForgeAccessTransformerSet();
			try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getJar().toUri()), Collections.emptyMap())) {
				//TODO: where do these names come from, can they be read from the jar?
				// 1.2.5 does not have these files
				for(String atFileName : Arrays.asList("forge_at.cfg", "fml_at.cfg")) {
					Path atFilePath = forgeFs.getPath(atFileName);
					if(Files.exists(atFilePath)) {
						project.getLogger().info("|-> Loading {}...", atFileName);
						ats.load(atFilePath);
					} else {
						project.getLogger().info("|-> No {} in the Forge jar.", atFileName);
					}
				}
			}
			
			project.getLogger().lifecycle("|-> Found {} access transformers affecting {} classes. Performing transform...", ats.getCount(), ats.getTouchedClassCount());
			
			try(
				FileSystem unAccessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + forgePatched.getPatchedJar().toUri()), Collections.emptyMap());
				FileSystem accessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + accessTransformedMc.toUri()), Collections.singletonMap("create", "true"))) {
				Files.walkFileTree(unAccessTransformedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path srcPath, BasicFileAttributes attrs) throws IOException {
						Path dstPath = accessTransformedFs.getPath(srcPath.toString());
						Files.createDirectories(dstPath.getParent());
						
						if(srcPath.toString().endsWith(".class")) {
							//Kludgey, but means we know the class data without reading the file
							String className = srcPath.toString()
								.replace("\\", "/")     //maybe windows does this idk?
								.substring(1)           //leading slash
								.replace(".class", ""); //funny extension
							
							project.getLogger().debug("Visiting class {}", className);
							
							if(ats.touchesClass(className)) {
								project.getLogger().debug("There's an access transformer for {}", className);
								
								try(InputStream srcReader = new BufferedInputStream((Files.newInputStream(srcPath)))) {
									ClassReader srcClassReader = new ClassReader(srcReader);
									ClassWriter dstClassWriter = new ClassWriter(0);
									srcClassReader.accept(ats.new AccessTransformingClassVisitor(dstClassWriter), 0);
									Files.write(dstPath, dstClassWriter.toByteArray());
								}
								
								return FileVisitResult.CONTINUE;
							}
						}
						
						project.getLogger().debug("Copying {} without changing it (not a class/no AT for it)", srcPath);
						
						Files.copy(srcPath, dstPath);
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			project.getLogger().lifecycle("|-> Access transformation success! :)");
			
			List<String> unusedAtsReport = ats.reportUnusedTransformers();
			if(unusedAtsReport.isEmpty()) {
				project.getLogger().warn("|-> Found {} unused access transformers.", unusedAtsReport.size());
				unusedAtsReport.forEach(project.getLogger()::warn);
			}
		}
	}
	
	public Path getTransformedJar() {
		return accessTransformedMc;
	}
	
	@Override
	protected Collection<Path> pathsToClean() {
		return Collections.singleton(accessTransformedMc);
	}
}

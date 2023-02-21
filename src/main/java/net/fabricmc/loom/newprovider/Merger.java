package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.yoinked.stitch.ClassMergerCooler;
import net.fabricmc.loom.yoinked.stitch.JarMergerCooler;
import org.gradle.api.Project;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Merges two jars, such as a Minecraft client jar and server jar.
 * When a member is available in only one source jar, a {@code @SideOnly} annotation is attached to the corresponding member in the merged jar.
 */
public class Merger extends NewProvider<Merger> {
	public Merger(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private Path clientJar, serverJar;
	
	//outputs
	private Path mergedJar;
	
	public Merger client(Path clientJar) {
		this.clientJar = clientJar;
		return this;
	}
	
	public Merger server(Path serverJar) {
		this.serverJar = serverJar;
		return this;
	}
	
	public Merger mergedFilename(String mergedFilename) {
		this.mergedJar = getCacheDir().resolve(mergedFilename);
		return this;
	}
	
	public Path getMergedJar() {
		return mergedJar;
	}
	
	//procedure
	public Merger merge() throws Exception {
		Preconditions.checkNotNull(clientJar, "client jar");
		Preconditions.checkNotNull(serverJar, "server jar");
		
		cleanOnRefreshDependencies(mergedJar);
		
		log.info("] merge client: {}", clientJar);
		log.info("] merge server: {}", serverJar);
		log.lifecycle("] merge target: {}", mergedJar);
		
		if(Files.notExists(mergedJar)) {
			Files.createDirectories(mergedJar.getParent());
			
			log.lifecycle("|-> Target does not exist. Merging with JarMergerCooler to {}", mergedJar);
			
			try(FileSystem clientFs = FileSystems.newFileSystem(URI.create("jar:" + clientJar.toUri()), Collections.emptyMap());
			    FileSystem serverFs = FileSystems.newFileSystem(URI.create("jar:" + serverJar.toUri()), Collections.emptyMap());
			    FileSystem mergedFs = FileSystems.newFileSystem(URI.create("jar:" + mergedJar.toUri()), Collections.singletonMap("create", "true"));
			    JarMergerCooler jm = new JarMergerCooler(clientFs, serverFs, mergedFs)) {
				//jm.enableSyntheticParamsOffset();
				jm.merge(new ClassMergerCooler()
					.sideEnum("Lcpw/mods/fml/relauncher/Side;")
					.sideDescriptorAnnotation("Lcpw/mods/fml/relauncher/SideOnly;"));
			}
			
			log.lifecycle("|-> Merged.");
		}
		
		return this;
	}
}

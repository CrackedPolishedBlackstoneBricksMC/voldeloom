package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Check;
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
	private Path client, server;
	private String mergedFilename;
	
	public Merger client(Path clientJar) {
		this.client = clientJar;
		return this;
	}
	
	public Merger server(Path serverJar) {
		this.server = serverJar;
		return this;
	}
	
	public Merger mergedFilename(String mergedFilename) {
		this.mergedFilename = mergedFilename;
		return this;
	}
	
	//outputs
	private Path merged;
	
	public Path getMergedJar() {
		return merged;
	}
	
	//procedure
	public Merger merge() throws Exception {
		Check.notNull(client, "client jar");
		Check.notNull(server, "server jar");
		
		merged = getOrCreate(props.substFilename(getCacheDir().resolve(mergedFilename)), dest -> {
			Files.createDirectories(dest.getParent());
			
			log.lifecycle("|-> Target does not exist. Merging with JarMergerCooler to {}", dest);
			
			try(FileSystem clientFs = FileSystems.newFileSystem(URI.create("jar:" + client.toUri()), Collections.emptyMap());
			    FileSystem serverFs = FileSystems.newFileSystem(URI.create("jar:" + server.toUri()), Collections.emptyMap());
			    FileSystem destFs = FileSystems.newFileSystem(URI.create("jar:" + dest.toUri()), Collections.singletonMap("create", "true"));
			    JarMergerCooler jm = new JarMergerCooler(clientFs, serverFs, destFs)) {
				//jm.enableSyntheticParamsOffset();
				jm.merge(new ClassMergerCooler()
					.sideEnum("Lcpw/mods/fml/relauncher/Side;")
					.sideDescriptorAnnotation("Lcpw/mods/fml/relauncher/SideOnly;"));
			}
			
			log.lifecycle("|-> Merged.");
		});
		log.lifecycle("] merged jar: {}", merged);
		
		return this;
	}
}

package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.ZipUtil;
import net.fabricmc.loom.yoinked.stitch.ClassMergerCooler;
import net.fabricmc.loom.yoinked.stitch.JarMergerCooler;
import org.gradle.api.Project;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

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
		
		merged = getOrCreate(getCacheDir().resolve(props.subst(mergedFilename)), dest -> {
			Files.createDirectories(dest.getParent());
			
			log.lifecycle("|-> Target does not exist. Merging with JarMergerCooler to {}", dest);
			
			try(FileSystem clientFs = ZipUtil.openFs(client); FileSystem serverFs = ZipUtil.openFs(server); FileSystem destFs = ZipUtil.createFs(dest);
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

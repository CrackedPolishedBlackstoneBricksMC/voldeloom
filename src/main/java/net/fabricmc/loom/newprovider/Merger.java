package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.yoinked.stitch.ClassMergerCooler;
import net.fabricmc.loom.yoinked.stitch.JarMergerCooler;
import org.gradle.api.Project;

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
	private Path clientJar, serverJar;
	
	//outputs
	private Path merged;
	
	public Merger client(Path clientJar) {
		this.clientJar = clientJar;
		return this;
	}
	
	public Merger server(Path serverJar) {
		this.serverJar = serverJar;
		return this;
	}
	
	public Merger mergedFilename(String mergedFilename) {
		this.merged = getCacheDir().resolve(mergedFilename);
		return this;
	}
	
	public Path getMerged() {
		return merged;
	}
	
	//procedure
	public Merger merge() throws Exception {
		Preconditions.checkNotNull(clientJar, "client jar");
		Preconditions.checkNotNull(serverJar, "server jar");
		
		cleanOnRefreshDependencies(merged);
		
		log.info("] merge client: {}", clientJar);
		log.info("] merge server: {}", serverJar);
		log.lifecycle("] merge target: {}", merged);
		
		if(Files.notExists(merged)) {
			Files.createDirectories(merged.getParent());
			
			log.lifecycle("|-> Target does not exist. Merging with JarMergerCooler to {}", merged);
			
			try(JarMergerCooler jm = new JarMergerCooler(clientJar.toFile(), serverJar.toFile(), merged.toFile())) {
				jm.classMergerCooler = new ClassMergerCooler()
					.sideEnum("Lcpw/mods/fml/relauncher/Side;")
					.sideDescriptorAnnotation("Lcpw/mods/fml/relauncher/SideOnly;");
				
				jm.enableSyntheticParamsOffset();
				jm.merge();
			}
			
			log.lifecycle("|-> Merged.");
		}
		
		return this;
	}
}

package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ShimForgeLibrariesTask extends DefaultTask implements LoomTaskExt {
	public ShimForgeLibrariesTask() {
		setGroup("fabric");
		
		getOutputs().upToDateWhen(__ -> {
			for(Path libDir : getLibraryDirectories()) {
				for(ForgeLibrary lib : getLibs()) {
					if(Files.notExists(libDir.resolve(lib.targetFilename))) return false;
				}
			}
			return true;
		});
	}
	
	@OutputDirectories
	public Collection<Path> getLibraryDirectories() {
		return getLoomGradleExtension().runConfigs.stream().map(cfg -> cfg.resolveRunDir().resolve("lib")).collect(Collectors.toList());
	}
	
	private List<ForgeLibrary> getLibs() {
		//see CoreFMLLibraries
		//TODO parse the class with asm LOLLL
		List<ForgeLibrary> libraries = new ArrayList<>();
		libraries.add(new ForgeLibrary("argo-2.25.jar", Constants.FML_LIBRARIES_BASE + "argo-2.25.jar"));
		libraries.add(new ForgeLibrary("guava-12.0.1.jar", Constants.FML_LIBRARIES_BASE + "guava-12.0.1.jar"));
		libraries.add(new ForgeLibrary("asm-all-4.0.jar", Constants.FML_LIBRARIES_BASE + "asm-all-4.0.jar"));
		libraries.add(new ForgeLibrary("bcprov-jdk15on-147.jar", Constants.FML_LIBRARIES_BASE + "bcprov-jdk15on-147.jar"));
		return libraries;
	}
	
	@TaskAction
	public void shimLibraries() throws IOException {
		//TODO: save these to gradle user-local cache to avoid downloading multiple times when there's multiple source sets
		for(Path forgeLibsDir : getLibraryDirectories()) {
			Files.createDirectories(forgeLibsDir);
			for(ForgeLibrary lib : getLibs()) lib.download(getProject(), forgeLibsDir);
		}
	}
	
	private static class ForgeLibrary {
		public ForgeLibrary(String targetFilename, String sourceURL) {
			this.targetFilename = targetFilename;
			this.sourceURL = sourceURL;
		}
		
		final String targetFilename;
		final String sourceURL;
		
		void download(Project project, Path libsDir) throws IOException {
			new DownloadSession(sourceURL, project)
				.dest(libsDir.resolve(targetFilename))
				.etag(true)
				.gzip(false)
				.skipIfExists()
				.download();
		}
		
		//and the following is because java url makes a Fucking Http Request in hashCode()
		//fortunately toString is safe, i believe
		
		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			
			ForgeLibrary other = (ForgeLibrary) o;
			
			if(!sourceURL.toString().equals(other.sourceURL.toString())) return false;
			return targetFilename.equals(other.targetFilename);
		}
		
		@Override
		public int hashCode() {
			int result = sourceURL.toString().hashCode();
			result = 31 * result + targetFilename.hashCode();
			return result;
		}
	}
}

package net.fabricmc.loom.forge;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

//(VOLDELOOM-DISASTER) CLIENT LIBRARY SHIM
public class ShimForgeClientLibraries extends DefaultTask implements LoomTaskExt {
	public ShimForgeClientLibraries() {
		setGroup("fabric");
	}
	
	//TODO: Will need adjusting if I ever backport multiple run configs. There is an `@OutputDirectories` in gradle
	@OutputDirectory
	public Path getForgeLibrariesDirectory() {
		LoomGradleExtension ext = getLoomGradleExtension();
		File runDir = new File(getProject().getRootDir(), ext.runDir); //see AbstractRunTask, TODO factor this out
		
		//Client run configs set this directory as the .minecraft folder (see RunConfig). Forge additionally adds a `lib` suffix when searching for libs
		return runDir.toPath().resolve(".minecraft").resolve("lib");
	}
	
	@TaskAction
	public void shimLibraries() throws IOException {
		Path forgeLibsDir = getForgeLibrariesDirectory();
		Files.createDirectories(forgeLibsDir);
		
		//source: CoreFMLLibraries
		//Have not verified sha1s yet!!! forge requires them to match
		List<ForgeLibrary> libraries = new ArrayList<>();
		
		//libraries.add(new ForgeLibrary("guava-12.0.1.jar", "https://repo1.maven.org/maven2/com/google/guava/guava/12.0.1/guava-12.0.1.jar")); //Wrong version
		//libraries.add(new ForgeLibrary("asm-all-4.0.jar", "https://repo1.maven.org/maven2/org/ow2/asm/asm/4.0/asm-4.0.jar")); //Forge is picky about the checksum!
		libraries.add(new ForgeLibrary("argo-2.25.jar", "https://repo1.maven.org/maven2/net/sourceforge/argo/argo/2.25/argo-2.25.jar"));
		libraries.add(new ForgeLibrary("guava-12.0.1.jar", "https://files.prismlauncher.org/fmllibs/guava-12.0.1.jar"));
		libraries.add(new ForgeLibrary("asm-all-4.0.jar", "https://files.prismlauncher.org/fmllibs/asm-all-4.0.jar"));
		libraries.add(new ForgeLibrary("bcprov-jdk15on-147.jar", "https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.47/bcprov-jdk15on-1.47.jar"));
		
		for(ForgeLibrary lib : libraries) {
			lib.download(getProject(), forgeLibsDir);
		}
	}
	
	private static class ForgeLibrary {
		public ForgeLibrary(String targetFilename, String sourceURL) {
			this.targetFilename = targetFilename;
			try {
				this.sourceURL = new URL(sourceURL);
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException(e);
			}
		}
		
		final String targetFilename;
		final URL sourceURL;
		
		void download(Project project, Path libsDir) throws IOException {
			Path targetPath = libsDir.resolve(targetFilename);
			if(!Files.exists(targetPath)) {
				DownloadUtil.downloadIfChanged(sourceURL, targetPath.toFile(), project.getLogger(), false);
			}
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

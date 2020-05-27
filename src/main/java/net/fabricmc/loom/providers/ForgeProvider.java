package net.fabricmc.loom.providers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;

public class ForgeProvider extends DependencyProvider {
	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	
	private String version;
	private Path installer;
	private Path userdev;
	
	public ForgeProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws IOException {
		version = dependency.getResolvedVersion();
		userdev = getExtension().getUserCache().toPath().resolve("forge-" + version + "-userdev-transformed.jar");
		String depString = dependency.getResolvedDepString();
		addDependency(depString + ":launcher", "implementation");
		addDependency(depString + ":universal", "modImplementation");
		addDependency(depString + ":userdev", Constants.FORGE_RESOLUTION_INTERNAL);
		addDependency(depString + ":installer", Constants.FORGE_RESOLUTION_INTERNAL);
		Configuration conf = getProject().getConfigurations().getByName(Constants.FORGE_RESOLUTION_INTERNAL);
		Path userdevArtifact = null;
		// Gradle gets REALLY funky when qualifiers are involved.
		// Resolving files for the installer dep specifically gives BOTH userdev and installer.
		// So... we do this instead.
		for(File f : conf.getFiles()) {
			if(f.toString().contains("userdev")) {
				userdevArtifact = f.toPath();
			} else if(f.toString().contains("installer")) {
				installer = f.toPath();
			}
		}
		
		if(!Files.exists(userdev)) {
			System.out.println("Downloaded userdev artifact: " + userdevArtifact);
			try(FileSystem transformedFs = FileSystems.newFileSystem(URI.create("jar:" + userdev.toUri()), FS_ENV)) {
				try(FileSystem userdevFs = FileSystems.newFileSystem(userdevArtifact, null)) {
					Path inject = userdevFs.getPath("inject");
					Files.walk(inject).forEach(p -> {
						if(!p.equals(inject)) {
							try {
								Files.copy(p, transformedFs.getPath(p.toString().substring("/inject/".length())));
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						}
					});
				}
			}
		}
		addDependency(userdev.toFile(), "implementation");
	}

	@Override
	public String getTargetConfig() {
		return Constants.FORGE;
	}

	public String getForgeVersion() {
		return version;
	}
	
	public Path getInstaller() {
		return installer;
	}
}

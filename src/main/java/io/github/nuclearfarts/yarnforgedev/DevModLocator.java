package io.github.nuclearfarts.yarnforgedev;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.Manifest;

import net.minecraftforge.fml.loading.LibraryFinder;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

public final class DevModLocator implements IModLocator {
	private IModFile thisFile = null;
	private Path root;

	@Override
	public List<IModFile> scanMods() {
		System.out.println("yarn forge dev scanner");
		try {
			Enumeration<URL> tomls = ClassLoader.getSystemResources("META-INF/mods.toml");
			while(tomls.hasMoreElements()) {
				URL toml = tomls.nextElement();
				Path path = LibraryFinder.findJarPathFor("META-INF/mods.toml", "classpath_mod", toml);
				if(Files.isDirectory(path)) {
					root = path;
					List<IModFile> list = new ArrayList<>();
					list.add((thisFile = new ModFile(path, this)));
					return list;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new ArrayList<>();
	}

	@Override
	public String name() {
		return "yarnforge dev";
	}

	@Override
	public Path findPath(IModFile modFile, String... path) {
		Path p = root;
		for(String s : path) {
			p = p.resolve(s);
		}
		return p;
	}

	@Override
	public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {
		String separator = root.getFileSystem().getSeparator();
		try {
			Files.walk(root).forEach(p -> {
				if(p.toString().endsWith(".class")) {
					String relative = root.relativize(p).toString();
					if(relative.startsWith(separator)) {
						relative = relative.substring(separator.length());
					}
					if(ClassLoader.getSystemResource(relative) != null) {
						pathConsumer.accept(p);
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Optional<Manifest> findManifest(Path file) {
		if(Files.isDirectory(file)) {
			Path possibleManifest = file.resolve("META-INF").resolve("MANIFEST.MF");
			if(Files.isReadable(possibleManifest)) {
				try(InputStream is = Files.newInputStream(possibleManifest)) {
					return Optional.of(new Manifest(is));
				} catch (IOException e) {
					return Optional.empty();
				}
			} 
		}
		return Optional.empty();
	}

	@Override
	public void initArguments(Map<String, ?> arguments) {
		
	}

	@Override
	public boolean isValid(IModFile modFile) {
		return modFile == thisFile;
	}

	
}

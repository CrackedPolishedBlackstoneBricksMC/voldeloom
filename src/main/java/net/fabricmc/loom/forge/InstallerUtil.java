package net.fabricmc.loom.forge;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class InstallerUtil {
	private static final ResourceRedirectClassLoader OH_GOD_LOADER = new ResourceRedirectClassLoader(InstallerUtil.class.getClassLoader());
	private static final Method CLIENT_INSTALL;
	
	static {
		try {
			Class<?> onLoader = OH_GOD_LOADER.loadClass("net.fabricmc.loom.forge.loaderhack.LoaderStatics");
			CLIENT_INSTALL = onLoader.getMethod("install", Path.class, Path.class);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void clientInstall(Path forgeLibs, Path vanilla, Path installer) throws IOException {
		OH_GOD_LOADER.setResourceJar(installer);
		try {
			CLIENT_INSTALL.invoke(null, forgeLibs, vanilla);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		} finally {
			OH_GOD_LOADER.closeFs();
		}
	}
}

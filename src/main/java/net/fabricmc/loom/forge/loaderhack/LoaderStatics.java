package net.fabricmc.loom.forge.loaderhack;

import java.nio.file.Path;
import java.util.ArrayList;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version.Library;

public class LoaderStatics {

	public static void install(Path forgeLibs, Path vanilla) {
		Install profile = Util.loadInstallProfile();
		for(Library lib : profile.getLibraries()) {
			if(!lib.getName().getLocalPath(forgeLibs.toFile()).exists()) {
				DownloadUtils.downloadLibrary((m, p) -> {if(p == ProgressCallback.MessagePriority.NORMAL) System.out.println(m);}, profile.getMirror(), lib, forgeLibs.toFile(), t -> true, new ArrayList<>());
			}
		}
		new LoomClientInstall(Util.loadInstallProfile(), (m, p) -> {if(p == ProgressCallback.MessagePriority.NORMAL) System.out.println(m);}, forgeLibs, vanilla).run(null, i -> true);
	}

}

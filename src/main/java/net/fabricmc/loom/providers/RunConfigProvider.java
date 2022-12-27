package net.fabricmc.loom.providers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.OperatingSystem;
import net.fabricmc.loom.util.RunConfig;
import org.gradle.api.Project;

public class RunConfigProvider extends DependencyProvider {
	public RunConfigProvider(Project project, LoomGradleExtension extension, MinecraftProvider mc, LibraryProvider libs) {
		super(project, extension);
		this.mc = mc;
		this.libs = libs;
		
		String llm = extension.loaderLaunchMethod;
		if(llm == null || llm.isEmpty()) {
			this.loaderLaunchMethod = "direct";
		} else {
			this.loaderLaunchMethod = llm;
		}
	}
	
	private final MinecraftProvider mc;
	private final LibraryProvider libs;
	private final String loaderLaunchMethod;
	
	private RunConfig client;
	private RunConfig server;
	
	@Override
	public void decorateProject() throws Exception {
		if(!loaderLaunchMethod.equals("direct")) {
			throw new IllegalStateException("Loader launch methods other than 'direct' are not implemented");
		}
		
		client = new RunConfig();
		client.projectName = project.getName();
		client.runDir = "file://$PROJECT_DIR$/" + extension.runDir;
		client.configName = "Minecraft Client";
		
		client.mainClass = "net.minecraft.client.Minecraft";
		//what the game treats as the .minecraft folder
		client.systemProperties.put("minecraft.applet.TargetDirectory", project.getRootDir().toPath().resolve("run").toAbsolutePath().toString());
		//native libs
		client.systemProperties.put("java.library.path", libs.getNativesDir().toAbsolutePath().toString());
		client.systemProperties.put("org.lwjgl.librarypath", libs.getNativesDir().toAbsolutePath().toString());
		//the fml relauncher always takes arg 0 as player name and arg 1 as session key (or -), see Minecraft#fmlReentry
		client.programArgs = "Player - ";
		//todo make this configurable cause, i dunno man, this arg is weird
		// and i dont have a mac to test it on
		if(OperatingSystem.getOS().equalsIgnoreCase("osx")) client.vmArgs += "-XstartOnFirstThread";
		
		server = new RunConfig();
		server.projectName = project.getName();
		//todo this has got to be made configurable
		// if changed, it will need another copy of the forge libraries...!
		server.runDir = "file://$PROJECT_DIR$/" + extension.runDir + "Server";
		server.configName = "Minecraft Server";
		
		server.mainClass = "net.minecraft.server.MinecraftServer";
		//the server uses the PWD as its folder for libraries and stuff
	}
	
	public RunConfig getClient() {
		return client;
	}
	
	public RunConfig getServer() {
		return server;
	}
}

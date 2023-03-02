package agency.highlysuspect;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;

import java.util.logging.Logger;

@Mod(name = "Sample Mod 1.2.5", version = "0.125")
public class Sample125 {
	public static final Logger LOGGER = Logger.getLogger("sample125");
	
	//@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample125() {
		LOGGER.setParent(Loader.log);
		LOGGER.info("Hello, constructor!");
		
		//i dont kno what side safety is but i herd it alot
		try {
			Class.forName("net.minecraft.client.Minecraft");
			proxy = new SampleClientProxy();
		} catch (ClassNotFoundException e) {
			proxy = new SampleCommonProxy();
		}
	}
	
	@Mod.PreInit
	public void preinit() {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

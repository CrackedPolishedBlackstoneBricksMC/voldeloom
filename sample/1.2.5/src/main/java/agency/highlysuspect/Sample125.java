package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.util.logging.Logger;

@Mod(modid = "sample125", name = "Sample Mod 1.2.5", version = "0.125")
public class Sample125 {
	public static final Logger LOGGER = Logger.getLogger("sample125");
	
	@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample125() {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.info("Hello, constructor!");
	}
	
	@Mod.PreInit
	public void preinit(FMLPreInitializationEvent e) {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.util.logging.Logger;

@Mod(modid = "sample1710", name = "Sample Mod 1.7.10", version = "0.1710")
public class Sample1710 {
	public static final Logger LOGGER = Logger.getLogger("sample164");
	
	@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample1710() {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.info("Hello, constructor!");
	}
	
	@Mod.PreInit
	public void preinit(FMLPreInitializationEvent e) {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

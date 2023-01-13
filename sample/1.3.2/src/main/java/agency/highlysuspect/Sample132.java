package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.util.logging.Logger;

@Mod(modid = "sample132", name = "Sample Mod 1.3.2", version = "0.132")
public class Sample132 {
	public static final Logger LOGGER = Logger.getLogger("sample132");
	
	@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample132() {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.info("Hello, constructor!");
	}
	
	@Mod.PreInit
	public void preinit(FMLPreInitializationEvent e) {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.util.logging.Logger;

@Mod(modid = "sample152", name = "Sample Mod 1.5.2", version = "0.00000001")
public class Sample152 {
	public static final Logger LOGGER = Logger.getLogger("sample152");
	
	@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample152() {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.info("Hello, constructor!");
	}
	
	@Mod.PreInit
	public void preinit(FMLPreInitializationEvent e) {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

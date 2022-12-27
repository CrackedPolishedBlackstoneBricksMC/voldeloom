package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;

import java.util.logging.Logger;

@Mod(modid = "sample147", name = "Sample Mod 1.4.7", version = "0.99999999999945646554684999")
public class Sample147 {
	public static final Logger LOGGER = Logger.getLogger("sample147");
	
	@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample147() {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.info("Hello, constructor!");
	}
	
	@Mod.PreInit
	public void preinit() {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

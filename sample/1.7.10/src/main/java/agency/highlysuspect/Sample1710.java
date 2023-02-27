package agency.highlysuspect;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "sample1710", name = "Sample Mod 1.7.10", version = "0.1710")
public class Sample1710 {
	public static final Logger LOGGER = LogManager.getLogger("sample1710");
	
	@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample1710() {
		LOGGER.info("Hello, constructor!");
		
		MinecraftForge.EVENT_BUS.register(this);
	}
	
	@Mod.EventHandler
	public void preinit(FMLPreInitializationEvent e) {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

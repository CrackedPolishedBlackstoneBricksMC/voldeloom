package agency.highlysuspect;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.logging.Logger;

@Mod(modid = "sample1710", name = "Sample Mod 1.7.10", version = "0.1710")
public class Sample1710 {
	public static final Logger LOGGER = Logger.getLogger("sample164");
	
	@SidedProxy(clientSide = "agency.highlysuspect.SampleClientProxy", serverSide = "agency.highlysuspect.SampleCommonProxy")
	public static SampleCommonProxy proxy;
	
	public Sample1710() {
		LOGGER.info("Hello, constructor!");
		
		MinecraftForge.EVENT_BUS.register(this);
	}
	
	@SubscribeEvent
	public void preinit(FMLPreInitializationEvent e) {
		LOGGER.info("Hello, preinit!");
		proxy.hi();
	}
}

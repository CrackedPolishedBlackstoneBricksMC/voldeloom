package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.util.logging.Logger;

@Mod(
	modid = "sample147_gradle4",
	name = "Sample Mod 1.4.7 (Gradle 4)",
	version = "0.123456789123456789123456789123456789"
	
)
public class Sample147Gradle4 {
	public static final Logger LOGGER = Logger.getLogger("sample147");
	
	public Sample147Gradle4() {
		LOGGER.setParent(FMLLog.getLogger());
		
		LOGGER.info("Hello, world! Gradle 4 project");
	}
	
	@Mod.PreInit
	public void preinit(FMLPreInitializationEvent e) {
		LOGGER.info("Hello, preinit! Gradle 4 project");
	}
}

package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;

import java.util.logging.Logger;

@Mod(
	modid = "sample147"
)
public class Sample147 {
	public static final Logger LOGGER = Logger.getLogger("sample147");
	
	public Sample147() {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.info("Hello, world!");
		LOGGER.info("Hello, debugger!");
	}
}

package agency.highlysuspect;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.EnumOptions;

import java.util.logging.Logger;

@Mod(modid = "sample147", name = "Sample Mod 1.4.7", version = "0.999999999999999")
public class Sample147 {
	public static final Logger LOGGER = Logger.getLogger("sample147");
	public static final EnumOptions[] target;
	
	public Sample147() {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.info("Hello, world!");
		LOGGER.info("Minecraft's class name is: " + Minecraft.class.getName());
		LOGGER.info("Minecraft mcDataDir: " + Minecraft.getMinecraft().mcDataDir);
		
		LOGGER.info("first option " + target[0]);
	}
	
	static {
		target = EnumOptions.values();
	}
}

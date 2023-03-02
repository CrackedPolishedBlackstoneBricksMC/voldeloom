package agency.highlysuspect;

import net.minecraft.client.Minecraft;

public class SampleClientProxy extends SampleCommonProxy {
	@Override
	public void hi() {
		Sample125.LOGGER.info("Hello from ClientProxy");
		
		Sample125.LOGGER.info("Minecraft's class name is: " + Minecraft.class.getName());
		Sample125.LOGGER.info("Minecraft mcDataDir: " + Minecraft.getMinecraftDir());
	}
}

package agency.highlysuspect;

import net.minecraft.client.Minecraft;

public class SampleClientProxy extends SampleCommonProxy {
	@Override
	public void hi() {
		Sample132.LOGGER.info("Hello from ClientProxy");
		
		Sample132.LOGGER.info("Minecraft's class name is: " + Minecraft.class.getName());
	}
}

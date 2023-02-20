package agency.highlysuspect;

import net.minecraft.client.Minecraft;

public class SampleClientProxy extends SampleCommonProxy {
	@Override
	public void hi() {
		Sample1710.LOGGER.info("Hello from ClientProxy");
		
		Sample1710.LOGGER.info("Minecraft's class name is: " + Minecraft.class.getName());
	}
}

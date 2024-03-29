package agency.highlysuspect;

import com.unascribed.ears.Ears;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.EnumOptions;

public class SampleClientProxy extends SampleCommonProxy {
	public static final EnumOptions[] target;
	
	static {
		target = EnumOptions.values();
	}
	
	@Override
	public void hi() {
		Sample147.LOGGER.info("Hello from ClientProxy");
		
		Sample147.LOGGER.info("Minecraft's class name is: " + Minecraft.class.getName());
		Sample147.LOGGER.info("Minecraft mcDataDir: " + Minecraft.getMinecraft().mcDataDir);
		
		Sample147.LOGGER.info("Una's skin URL:" + Ears.amendSkinUrl("http://skins.minecraft.net/MinecraftSkins/unascribed.png"));
	}
}

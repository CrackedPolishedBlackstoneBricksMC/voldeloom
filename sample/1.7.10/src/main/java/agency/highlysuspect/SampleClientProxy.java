package agency.highlysuspect;

import net.minecraft.block.BlockSand;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

public class SampleClientProxy extends SampleCommonProxy {
	@Override
	public void hi() {
		Sample1710.LOGGER.info("Hello from ClientProxy");
		
		Sample1710.LOGGER.info("Minecraft's class name is: " + Minecraft.class.getName());
		Sample1710.LOGGER.info("BlockSand class name is: " + BlockSand.class.getName());
		Sample1710.LOGGER.info("Minecraft's mcDataDir: " + Minecraft.getMinecraft().mcDataDir);
	}
	
	@Override
	public void bye() {
		for(int i = 0; i < 10; i++) {
			Sample1710.LOGGER.info("BlockSand class name is: " + BlockSand.class.getName());
			Sample1710.LOGGER.info("Thing with block: " + Blocks.sand.stepSound.soundName);
			Sample1710.LOGGER.info("Thing with block: " + Blocks.sand.blockParticleGravity);
			Sample1710.LOGGER.info("Thing with block: " + Blocks.sand.damageDropped(0));
			Sample1710.LOGGER.info("Thing with block: " + Blocks.sand.getUnlocalizedName());
			Sample1710.LOGGER.info("Thing with block: " + Blocks.sand.getLocalizedName());
			
			try {
				Thread.sleep(400);
			} catch (Exception e) {}
		}
	}
}

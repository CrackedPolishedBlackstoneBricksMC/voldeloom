package agency.highlysuspect;

import net.minecraft.block.BlockSand;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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
				Thread.sleep(100);
			} catch (Exception e) {}
		}
		
		EntityClientPlayerMP hmm = Minecraft.getMinecraft().thePlayer;
		EntityPlayerSP hmm2 = Minecraft.getMinecraft().thePlayer;
		AbstractClientPlayer hmm3 = Minecraft.getMinecraft().thePlayer;
		EntityPlayer hmm4 = Minecraft.getMinecraft().thePlayer;
		EntityLivingBase hmm5 = Minecraft.getMinecraft().thePlayer;
		
		if(hmm != null) {
			System.out.println(hmm.isInsideOfMaterial(Material.water));
			System.out.println(hmm2.isInsideOfMaterial(Material.water));
			System.out.println(hmm3.isInsideOfMaterial(Material.water));
			System.out.println(hmm4.isInsideOfMaterial(Material.water));
			System.out.println(hmm5.isInsideOfMaterial(Material.water));
		}
	}
}

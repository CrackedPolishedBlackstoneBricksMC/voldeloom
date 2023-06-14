package agency.highlysuspect;

import net.minecraft.block.BlockSand;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.EnumOptions;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;

public class SampleClientProxy extends SampleCommonProxy {
	public static final EnumOptions[] target;
	
	static {
		target = EnumOptions.values();
	}
	
	@Override
	public void hi() {
		Sample147.LOGGER.info("Hello from ClientProxy");
		
		Sample147.LOGGER.info("Minecraft's class name is: " + Minecraft.class.getName());
		Sample147.LOGGER.info("BlockSand's class name is: " + BlockSand.class.getName());
		Sample147.LOGGER.info("Minecraft mcDataDir: " + Minecraft.getMinecraft().mcDataDir);
		
		//Sample147.LOGGER.info("Una's skin URL: " + Ears.amendSkinUrl("http://skins.minecraft.net/MinecraftSkins/unascribed.png"));
		
		EntityClientPlayerMP hmm1 = Minecraft.getMinecraft().thePlayer;
		EntityPlayerSP hmm2 = Minecraft.getMinecraft().thePlayer;
		EntityPlayer hmm3 = Minecraft.getMinecraft().thePlayer;
		EntityLiving hmm4 = Minecraft.getMinecraft().thePlayer;
		Minecraft client = Minecraft.getMinecraft();
		if(hmm1 != null) {
			//should all appear as hmmX.a(agi.h) or client.g.a(agi.h)
			System.out.println(hmm1.isInsideOfMaterial(Material.water));
			System.out.println(hmm2.isInsideOfMaterial(Material.water));
			System.out.println(hmm3.isInsideOfMaterial(Material.water));
			System.out.println(hmm4.isInsideOfMaterial(Material.water));
			System.out.println(client.thePlayer.isInsideOfMaterial(Material.water));
			System.out.println(Minecraft.getMinecraft().thePlayer.isInsideOfMaterial(Material.water));
			
			//should appear as client.v.b().b("gaming")
			client.ingameGUI.getChatGUI().addToSentMessages("gaming");
			
			//dud condition that intellij won't detect as always false
			if(hmm1 != client.thePlayer) {
				//doesnt have an MCP name at all
				//should appear as client.b.b
				client.playerController.func_78768_b(hmm1, hmm2);
			}
		}
	}
}

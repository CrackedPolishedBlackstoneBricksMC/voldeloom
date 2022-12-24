package agency.highlysuspect;

import net.minecraft.client.settings.EnumOptions;

public class GoofyAhhSwitch {
	public static void blah(EnumOptions wow) {
		int j;
		switch(wow) {
			case FOV: j = 1111;
			case GAMMA: j = 2222;
			case ADVANCED_OPENGL: j = 3333;
			default: j = 69420;
		}
		
		System.out.println(j);
	}
}

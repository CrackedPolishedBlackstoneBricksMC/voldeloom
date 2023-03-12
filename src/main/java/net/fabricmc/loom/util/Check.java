package net.fabricmc.loom.util;

import javax.annotation.Nullable;

/**
 * We have Guava Preconditions at home
 */
public class Check {
	public static <T> T notNull(@Nullable T thing, String why) {
		if(thing == null) throw new NullPointerException(why + " is null!");
		return thing;
	}
	
	public static void isTrue(boolean thing, String why) {
		if(!thing) throw new IllegalStateException(why + " is false, should be true!");
	}
	
	public static void isFalse(boolean thing, String why) {
		if(thing) throw new IllegalStateException(why + " is true, should be false!");
	}
}

package net.fabricmc.loom.util;

import javax.annotation.Nullable;

/**
 * We have Guava Preconditions at home
 */
public class Check {
	public static void notNull(@Nullable Object thing, String why) {
		if(thing == null) throw new NullPointerException(why + " is null!");
	}
}

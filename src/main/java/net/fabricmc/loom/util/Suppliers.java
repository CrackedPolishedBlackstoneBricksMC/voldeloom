package net.fabricmc.loom.util;

import java.util.function.Supplier;

/**
 * We have Guava at home
 */
public class Suppliers {
	public static <T> Supplier<T> memoize(Supplier<T> in) {
		return new Supplier<T>() {
			boolean init = false;
			T thing;
			
			@Override
			public T get() {
				if(!init) {
					thing = in.get();
					init = true;
				}
				return thing;
			}
		};
	}
}

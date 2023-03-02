package net.fabricmc.loom.util;

/**
 * {@code Function<T, R>} if it was good
 */
public interface ThrowyFunction<T, R, X extends Throwable> {
	R apply(T t) throws X;
	
	/**
	 * like {@code BiFunction<T, U, R>} obviously
	 */
	interface Bi<T, U, R, X extends Throwable> {
		R apply(T t, U u) throws X;
	}
	
	/**
	 * Hm. Having second thoughts
	 */
	interface Tri<T, U, V, R, X extends Throwable> {
		R apply(T t, U u, V v) throws X;
	}
	
	
	/**
	 * Hm. Having second thoughts
	 */
	interface Quad<T, U, V, W, R, X extends Throwable> {
		R apply(T t, U u, V v, W w) throws X;
	}
}

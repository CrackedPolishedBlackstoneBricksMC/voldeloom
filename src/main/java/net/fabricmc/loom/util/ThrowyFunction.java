package net.fabricmc.loom.util;

public interface ThrowyFunction<T, R, X extends Throwable> {
	R apply(T t) throws X;
	
	interface Bi<T, U, R, X extends Throwable> {
		R apply(T t, U u) throws X;
	}
	
	interface Tri<T, U, V, R, X extends Throwable> {
		R apply(T t, U u, V v) throws X;
	}
}

package br.hirozn.bauchefe;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/** Até quando a galinha de cada id bate as asas de propósito (carinho do Márcio). Preenchido no cliente. */
public final class Asas {
	private static final Int2LongMap ate = new Int2LongOpenHashMap();
	private static final Int2LongMap carinho = new Int2LongOpenHashMap();

	private Asas() {}

	public static void bater(int id, long ateTick) { ate.put(id, ateTick); }

	/** Começou a ganhar carinho nesse tick (a galinha levanta a cabeça para a mão). */
	public static void carinho(int id, long inicio) { carinho.put(id, inicio); }

	/** Tick em que começou o último carinho, ou 0. */
	public static long carinhoDesde(int id) { return carinho.getOrDefault(id, 0L); }

	public static boolean batendo(int id, long agora) {
		long a = ate.getOrDefault(id, 0L);
		if (a != 0 && a < agora) ate.remove(id);
		return a > agora;
	}
}

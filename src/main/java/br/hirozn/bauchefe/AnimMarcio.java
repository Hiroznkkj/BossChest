package br.hirozn.bauchefe;

import net.minecraft.util.Mth;

/**
 * Animações especiais do Márcio (fora da pesca). O servidor escolhe e manda o id ao cliente;
 * o cliente anima a partir do momento em que recebeu. Tempo em ticks, com fração.
 */
public final class AnimMarcio {
	public static final int NADA = 0, ESPREGUICAR = 1, COCAR = 2, ACENAR = 3, COMEMORAR = 4, CARINHO = 5;
	private static final int[] DURACAO = {0, 60, 50, 44, 40, 60};
	/** Ticks para entrar e sair da pose (mistura com a pose normal, sem saltos). */
	public static final float MISTURA = 8f;

	private AnimMarcio() {}

	public static int duracao(int anim) { return anim > 0 && anim < DURACAO.length ? DURACAO[anim] : 0; }

	/** Peso da animação: sobe nos primeiros ticks, fica em 1 e desce no fim. */
	public static float peso(int anim, float t) {
		int d = duracao(anim);
		if (d == 0 || t < 0 || t >= d) return 0;
		return AnimPesca.inOutSine(Math.min(t / MISTURA, (d - t) / MISTURA));
	}

	/** Fase aleatória fixa por entidade (como o random(id) dos packs de animação). */
	public static float fase(int id) {
		return (Mth.sin(id * 12.9898f) * 43758.547f) % 1f * Mth.TWO_PI;
	}
}

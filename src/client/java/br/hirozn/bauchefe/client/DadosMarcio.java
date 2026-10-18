package br.hirozn.bauchefe.client;

/** Dados extras do Márcio no render state (preenchidos no extractRenderState, já com partialTick). */
public class DadosMarcio {
	public boolean pescador, sentado, lutou;
	public int fase, anim; // fase da pesca (AnimPesca) e animação especial (AnimMarcio)
	public float tempo, animTempo, rFase; // ticks desde o início de cada uma; fase aleatória fixa dele
	public float nado; // 0 fora da água, 1 nadando (muda aos poucos)
	// balão: palavras, quando cada uma é dita (s; null = sem áudio) e quanto tempo já passou (s)
	public String[] fala;
	public float[] falaTempos;
	public float falaSeg, falaAlpha;
}

package br.hirozn.bauchefe.client;

/** Dados extras do Márcio no render state (preenchidos no extractRenderState, já com partialTick). */
public class DadosMarcio {
	public int id;
	public boolean pescador, sentado, lutou;
	public int fase, anim; // fase da pesca (AnimPesca) e animação especial (AnimMarcio)
	public float agora, tempo, animTempo, rFase; // tempo do mundo (ticks); desde o início de cada uma; fase aleatória dele
	public float nado; // 0 fora da água, 1 nadando (muda aos poucos)
	public float pe; // 0 sentado no barco, 1 em pé (levantando/sentando aos poucos)
	public double saltoX, saltoY, saltoZ; // deslocamento visual para não "teleportar" (embarcar, descer, testes)
	// olhos: deslocamento (pixels), pálpebra (0 aberto, 1 fechado), distância da câmera (para o z-fighting)
	public float olhoX, olhoY, palpebra, distancia;
	// balão: palavras, quando cada uma é dita (s; null = sem áudio) e quanto tempo já passou (s)
	public String[] fala;
	public float[] falaTempos;
	public float falaSeg, falaAlpha;
	// /marcioanim tocar: animação customizada tocando só neste PC e há quantos ticks
	public AnimCustom.Anim teste;
	public float testeTempo;
}

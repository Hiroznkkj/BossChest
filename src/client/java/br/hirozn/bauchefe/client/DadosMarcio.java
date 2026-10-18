package br.hirozn.bauchefe.client;

/** Dados extras do Márcio no VillagerRenderState (preenchidos no extractRenderState, já com partialTick). */
public class DadosMarcio {
	public boolean pescador, sentado;
	public int fase, anim; // fase da pesca (AnimPesca) e animação especial (AnimMarcio)
	public float tempo, animTempo, rFase; // ticks desde o início de cada uma; fase aleatória fixa do villager
	// olhos: deslocamento das pupilas (pixels) e pálpebra (0 aberto, 1 fechado)
	public float pupilaX, pupilaY, palpebra, distancia;
	// balão
	public String fala;
	public float falaAlpha;
}

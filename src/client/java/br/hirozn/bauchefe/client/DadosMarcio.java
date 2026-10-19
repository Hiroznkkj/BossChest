package br.hirozn.bauchefe.client;

/** Dados extras do Márcio no render state (preenchidos no extractRenderState, já com partialTick). */
public class DadosMarcio {
	public int id;
	public boolean pescador, sentado, lutou;
	public int fase, anim, animVar; // fase da pesca (AnimPesca), animação especial (AnimMarcio) e qual jeito dela
	public float agora, tempo, animTempo, rFase; // tempo do mundo (ticks); desde o início de cada uma; fase aleatória dele
	public float nado; // 0 fora da água, 1 nadando (muda aos poucos)
	public float agua; // quanto dos pés está debaixo d'água (blocos), para deitar na linha d'água
	// transição entre lugares (MarcioCliente.SUBIR_TERRA...), em que ponto dela (0..1) e a distância andada
	public int trans;
	public float transU, transDist;
	// fase da transição (MarcioCliente.ANDA, PASSA, ACOMODA, LEVANTA, VIRA), quanto dela já foi (0..1), passos andados
	public int transFase;
	public float transV, transPassos;
	public int transLado = 1; // lado do barco por onde sobe/desce (1 = esquerda do barco)
	public float pe; // 0 sentado no barco, 1 em pé (levantando/sentando aos poucos)
	public double saltoX, saltoY, saltoZ; // deslocamento visual para não "teleportar" (embarcar, descer, testes)
	// olhos: deslocamento (pixels), pálpebra (0 aberto, 1 fechado), distância da câmera (para o z-fighting)
	public float olhoX, olhoY, palpebra, distancia;
	// balão: palavras, quando cada uma é dita (s; null = sem áudio) e quanto tempo já passou (s)
	public String[] fala;
	public float[] falaTempos;
	public float falaSeg, falaAlpha, falaPulso; // falaPulso: 1 quando uma palavra começa, cai até a próxima
	public int humor; // jeito de falar (Falas.ALEGRE...)
	// --- pose procedural (IK): onde o corpo é desenhado no mundo, giro do corpo, e os alvos reais ---
	public double mx, my, mz;
	public float myaw;
	public net.minecraft.world.phys.Vec3 bordaEsq; // borda do barco ao alcance da mão esquerda (ou null)
	public net.minecraft.world.phys.Vec3 pinto; // cabeça do Pinto (ou null)
	public float chaoD = Float.NaN, chaoE = Float.NaN; // altura do chão embaixo de cada pé, relativa aos pés (blocos)
	public float beirada; // 0..1: quase caindo de uma beirada (braços girando, corpo para trás)
	public net.minecraft.world.phys.Vec3 susto; // de onde veio o susto
	public float sustoT = 999f, dorT = 999f; // ticks desde o susto / desde a última pancada
	public net.minecraft.world.phys.Vec3 dorDe; // de onde veio a pancada
	public net.minecraft.world.phys.Vec3 maoDe; // mão de quem vai bater o hi five (olhos do jogador)
	public float ofereceT = 999f, bateT = 999f; // ticks desde que ofereceu a mão / desde que bateram
	// sentado num Assento: tipo (Lugares.CADEIRA...), altura do assento acima do chão onde os pés ficam (blocos)
	public int assento;
	public float assentoAltura;
	public boolean livro; // com o livro na mão
	public net.minecraft.world.phys.Vec3 assentoPos; // superfície do assento (mundo) e para onde fica virado
	public float assentoYaw;
	// camadas novas: chuva na cabeça, medindo o quarto, fogueira para esquentar as mãos, armadura nova de alguém
	// (pés e cabeça dela), com quem conversa (olhos), calor de fogo perto, pegando fogo
	public boolean chuva, medindo, emChamas;
	public net.minecraft.world.phys.Vec3 aquecer, armaduraPes, armaduraCabeca, conversaCom, calor;
	public float armaduraT = 999f, calorT = 999f;
	public float andar; // velocidade da animação de andar (0 parado)
	public float livroT = 999f, guardaT = 999f; // ticks desde que foi tirar o livro do bolso / guardar
	// /marcioanim tocar: animação customizada tocando só neste PC e há quantos ticks
	public AnimCustom.Anim teste;
	public float testeTempo;
}

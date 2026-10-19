package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Falas;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pose do Márcio por cima da pose humanoide vanilla: pesca, briga com peixe difícil (em pé),
 * respiração, balanço no barco, subir e descer do barco, as animações especiais e o corpo acompanhando
 * o jeito de falar. Inspirado no estilo do Fresh Animations (FreshLX): movimentos pequenos e contínuos,
 * fase aleatória por entidade e tudo misturado com easing.
 * Braço direito segura a vara; o esquerdo fica livre. zRot positivo abre o braço direito; no esquerdo é negativo.
 * xRot negativo leva o braço para a frente/cima; positivo, para trás.
 */
public final class PoseMarcio {
	private PoseMarcio() {}

	/** Só para o /marcioanim exportar: sem respiração nem balanço, especiais com peso cheio e sem as customizadas. */
	static boolean exportando;

	public static void aplicar(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart body,
	                           ModelPart dir, ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float r = d.rFase;
		float resp = exportando ? 0f : Mth.sin(age * 0.09f + r); // respiração, ~3,5 s
		boolean pescando = d.fase != AnimPesca.NADA;

		// braço direito: pesca; fora dela, no barco, segura a vara apontada para a frente
		if (d.sentado || pescando) {
			dir.xRot = AnimPesca.braco(d.fase, d.tempo);
			dir.yRot = 0f;
			dir.zRot = 0.06f + 0.02f * resp;
			// esquerdo apoiado no joelho; ajuda a puxar na mordida e na puxada
			float puxa = d.fase == AnimPesca.PUXANDO || d.fase == AnimPesca.MORDIDA ? (dir.xRot + 0.35f) * 0.45f : 0f;
			esq.xRot = -0.55f + puxa + 0.04f * Mth.sin(age * 0.06f + r * 1.7f);
			esq.yRot = 0f;
			esq.zRot = -0.06f - 0.02f * resp;
		}

		// respiração: o peito enche de leve, ombros e cabeça acompanham
		body.zScale = 1f + 0.03f * resp;
		body.xScale = 1f + 0.012f * resp;
		dir.y -= 0.25f * resp;
		esq.y -= 0.25f * resp;
		head.y -= 0.12f * resp;
		if (!exportando) head.zRot += 0.02f * Mth.sin(age * 0.05f + r * 2f); // inclina a cabeça de leve, à toa

		if (d.sentado) levantar(d, age, r, root, head, body, dir, esq, pernaD, pernaE);

		emPe(d, root, head, body, esq, pernaD, pernaE);
		if (!exportando) {
			// customizadas (AnimCustom) no lugar das do código: a fase da pesca, ou "parado" sentado no barco
			AnimCustom.Anim c = pescando ? AnimCustom.get(AnimPesca.NOMES[d.fase]) : AnimCustom.get(AnimCustom.PARADO);
			if (c != null) AnimCustom.aplicar(c, pescando ? d.tempo : d.agora, pescando ? 1f : d.sentado ? 1f - d.pe : 0f,
					root, head, body, dir, esq, pernaD, pernaE);
		}
		transicao(d, age, head, body, dir, esq, pernaD, pernaE, root);
		especial(d, root, head, body, dir, esq, pernaD, pernaE);
		if (!exportando) expressao(d, age, root, head, body, dir, esq);
		// /marcioanim tocar: entra e sai em 8 ticks
		if (d.teste != null) {
			float dur = d.teste.duracao() * 20f, w = AnimPesca.inOutSine(Math.min(d.testeTempo / 8f, (dur - d.testeTempo) / 8f));
			AnimCustom.aplicar(d.teste, d.testeTempo, w, root, head, body, dir, esq, pernaD, pernaE);
		}
	}

	/**
	 * Sentar e levantar no barco. 'pe' vai de 0 (sentado) a 1 (em pé). As pernas giram da horizontal para a
	 * vertical e o quadril sobe junto com elas, de um jeito que os pés ficam no chão do barco (a altura sai do
	 * ângulo da perna, AnimPesca.alturaAssento). No meio do movimento o corpo vai para a frente pegando impulso
	 * e a mão esquerda apoia na borda do barco, como gente levantando (e sentando) de um lugar baixo.
	 */
	private static void levantar(DadosMarcio d, float age, float r, ModelPart root, ModelPart head, ModelPart body,
	                             ModelPart dir, ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float pe = d.pe, sentado = 1f - pe;
		float coxa = AnimPesca.COXA * sentado;
		pernaD.xRot = -coxa;
		pernaE.xRot = -coxa;
		pernaD.yRot = 0.314f * sentado;
		pernaE.yRot = -0.314f * sentado;
		root.y += AnimPesca.ASSENTO * 16f * AnimPesca.alturaAssento(pe);
		float impulso = Mth.sin(Mth.PI * pe); // 0 sentado e em pé, 1 no meio
		body.xRot += 0.45f * impulso;
		head.xRot += 0.25f * impulso; // olha para onde vai pisar
		// subindo da água as duas mãos já estão na borda (transicao cuida delas)
		// a mão faz força no começo de levantar (e no fim de sentar), quando o ombro ainda está baixo
		if (d.trans != MarcioCliente.SUBIR_AGUA || d.transU > 0.75f) apoio(esq, Mth.sin(Mth.PI * AnimPesca.clamp(pe * 1.5f)));
		if (d.fase == AnimPesca.NADA) dir.xRot -= 0.3f * impulso;

		// balanço junto com o barco, girando em torno do assento (12 px abaixo do pivô do root)
		if (exportando) return;
		float bz = 0.035f * sentado * Mth.sin(age * 0.07f + r), bx = 0.02f * sentado * Mth.sin(age * 0.05f + r * 1.3f);
		root.zRot += bz;
		root.xRot += bx;
		root.x += 12f * Mth.sin(bz);
		root.y += 12f * (1f - Mth.cos(bz)) + 12f * (1f - Mth.cos(bx));
		root.z -= 12f * Mth.sin(bx);
	}

	/** Mão esquerda apoiada na borda do barco (6 px acima do chão, 9 px para o lado), do lado de fora do joelho. */
	private static void apoio(ModelPart esq, float w) {
		if (w > 0) misturar(esq, -0.15f, 0.1f, -0.55f, w);
	}

	/**
	 * Peixe difícil: de pé (levantar), pernas abertas, corpo para trás puxando a vara com as duas mãos.
	 * As pernas trabalham junto: o corpo todo vira atrás do peixe (os pés ficam), o peso passa de um pé
	 * para o outro e os joelhos cedem a cada puxão.
	 */
	private static void emPe(DadosMarcio d, ModelPart root, ModelPart head, ModelPart body,
	                         ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float w = d.fase == AnimPesca.LUTA ? AnimPesca.inOutSine(d.tempo / AnimPesca.LEVANTA)
				: d.fase == AnimPesca.PUXANDO && d.lutou ? 1f - AnimPesca.inOutSine(d.tempo / AnimPesca.DUR_PUXANDO) : 0f;
		if (w <= 0) return;
		float luta = d.fase == AnimPesca.LUTA ? AnimPesca.luta(d.tempo) : 0f, t = d.tempo;
		pernaD.zRot = Mth.lerp(w, pernaD.zRot, 0.12f); // pernas abertas para se equilibrar
		pernaE.zRot = Mth.lerp(w, pernaE.zRot, -0.12f);
		float tranco = Mth.sin(t * 0.55f);
		if (luta > 0) {
			// vira atrás da boia (ela vai de um lado para o outro no mesmo ritmo), com os pés parados
			float lado = 0.14f * Mth.sin(t * 0.23f) * luta;
			root.yRot += lado;
			pernaD.yRot -= lado;
			pernaE.yRot -= lado;
			// o peso vai de um pé para o outro
			float peso = 0.07f * Mth.sin(t * 0.27f + 1f) * luta;
			pernaD.zRot += peso;
			pernaE.zRot += peso;
			// joelhos cedem a cada puxão: o quadril desce e as pernas inclinam para a frente (pés no chão)
			float cede = luta * (0.5f + 0.5f * tranco);
			root.y += 0.4f * cede;
			pernaD.xRot -= 0.25f * cede + 0.08f * Mth.sin(t * 0.27f) * luta;
			pernaE.xRot -= 0.25f * cede - 0.08f * Mth.sin(t * 0.27f) * luta;
		}
		// as duas mãos na vara, o corpo cede e puxa junto com o peixe
		body.xRot = Mth.lerp(luta, body.xRot, -0.12f - 0.08f * tranco);
		head.xRot = Mth.lerp(luta, head.xRot, 0.2f + 0.05f * Mth.sin(t * 1.1f));
		if (luta > 0) {
			esq.xRot = Mth.lerp(luta, esq.xRot, -1.5f + 0.35f * tranco);
			esq.yRot = Mth.lerp(luta, esq.yRot, 0.45f);
			esq.zRot = Mth.lerp(luta, esq.zRot, 0f);
		}
	}

	/**
	 * Subir e descer do barco (o caminho do corpo está em MarcioCliente.caminho; aqui ficam as pernas e braços).
	 * Da terra: anda até a borda, apoia a mão e passa uma perna e depois a outra, e senta. Da água: estica os
	 * braços, se agarra na borda, se puxa para cima com a perna vindo por último, e senta. Descendo: levanta
	 * apoiando a mão (levantar), passa as pernas por cima da borda e anda até a doca.
	 */
	private static void transicao(DadosMarcio d, float age, ModelPart head, ModelPart body, ModelPart dir, ModelPart esq,
	                              ModelPart pernaD, ModelPart pernaE, ModelPart root) {
		float u = d.transU;
		switch (d.trans) {
			case MarcioCliente.SUBIR_TERRA -> {
				andar(d, u, 0f, 0.45f, 0.55f, root, body, dir, esq, pernaD, pernaE);
				passarPerna(u, 0.45f, 0.75f, head, body, esq, pernaD, pernaE, root);
			}
			case MarcioCliente.SUBIR_AGUA -> {
				if (u < 0.35f) { // nada até a borda e estica os braços para agarrar
					float v = u / 0.35f, w = AnimPesca.inOutSine(v);
					misturar(dir, -2.6f, 0f, 0.25f, w);
					misturar(esq, -2.6f, 0f, -0.25f, w);
					pernaD.xRot += 0.35f * Mth.sin(age * 0.9f);
					pernaE.xRot -= 0.35f * Mth.sin(age * 0.9f);
					body.xRot += 0.3f * w;
				} else if (u < 0.75f) { // se puxa: os braços empurram a borda para baixo e o corpo sobe
					float v = (u - 0.35f) / 0.4f, e = AnimPesca.inOutSine(v);
					misturar(dir, Mth.lerp(e, -2.6f, -0.5f), 0f, Mth.lerp(e, 0.25f, 0.4f), 1f - AnimPesca.inOutSine((v - 0.85f) / 0.15f));
					misturar(esq, Mth.lerp(e, -2.6f, -0.5f), 0f, Mth.lerp(e, -0.25f, -0.4f), 1f);
					body.xRot += 0.2f + 0.4f * Mth.sin(Mth.PI * v);
					head.xRot += Mth.lerp(e, -0.25f, 0.2f); // olha para a borda e depois para onde vai sentar
					// a perna direita vem por cima da borda no fim, a esquerda logo depois
					pernaD.xRot -= 1.4f * Mth.sin(Mth.PI * AnimPesca.clamp((v - 0.3f) * 1.6f));
					pernaE.xRot -= 1.2f * Mth.sin(Mth.PI * AnimPesca.clamp((v - 0.5f) * 2f));
				}
			}
			case MarcioCliente.DESCER -> {
				passarPerna(u, 0.35f, 0.7f, head, body, esq, pernaD, pernaE, root);
				andar(d, u, 0.7f, 1f, 0.25f, root, body, dir, esq, pernaD, pernaE);
			}
			case MarcioCliente.PULO -> andar(d, u, 0f, 1f, 1f, root, body, dir, esq, pernaD, pernaE);
			default -> {}
		}
	}

	/** Passos entre 'a' e 'b' da transição, cobrindo 'parte' da distância (pernas e braço esquerdo balançando). */
	private static void andar(DadosMarcio d, float u, float a, float b, float parte, ModelPart root, ModelPart body,
	                          ModelPart dir, ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		if (u < a || u > b) return;
		float v = (u - a) / (b - a), w = AnimPesca.inOutSine(Math.min(1f, Math.min(v, 1f - v) * 5f));
		float passo = v * Math.max(1f, Math.round(d.transDist * parte * 2.2f)); // ~2 passos por bloco, inteiro
		float s = Mth.sin(passo * Mth.PI) * w;
		pernaD.xRot += 0.6f * s;
		pernaE.xRot -= 0.6f * s;
		esq.xRot -= 0.5f * s;
		dir.xRot += 0.15f * s; // com a vara, balança pouco
		body.xRot += 0.06f * w;
		root.y -= 0.5f * Math.abs(Mth.sin(passo * Mth.PI)) * w; // sobe e desce a cada passo
	}

	/** Passa uma perna e depois a outra por cima da borda, com a mão esquerda apoiada nela. */
	private static void passarPerna(float u, float a, float b, ModelPart head, ModelPart body, ModelPart esq,
	                                ModelPart pernaD, ModelPart pernaE, ModelPart root) {
		if (u < a || u > b) return;
		float v = (u - a) / (b - a), arco = Mth.sin(Mth.PI * v);
		float ld = Mth.sin(Mth.PI * AnimPesca.clamp(v * 1.6f)), le = Mth.sin(Mth.PI * AnimPesca.clamp((v - 0.38f) * 1.6f));
		pernaD.xRot -= 1.3f * ld;
		pernaD.zRot += 0.12f * ld; // joelho para fora para passar pela borda
		pernaE.xRot -= 1.2f * le;
		pernaE.zRot -= 0.12f * le;
		root.zRot += 0.05f * (ld - le); // o peso fica no pé que está no chão
		body.xRot += 0.3f * arco;
		head.xRot += 0.25f * arco; // olha onde pisa
		apoio(esq, arco);
	}

	private static void especial(DadosMarcio d, ModelPart root, ModelPart head, ModelPart body, ModelPart dir, ModelPart esq,
	                             ModelPart pernaD, ModelPart pernaE) {
		if (d.anim == AnimMarcio.NADA) return;
		float w = exportando ? 1f : AnimMarcio.peso(d.anim, d.animTempo);
		if (w <= 0) return;
		AnimCustom.Anim c = exportando ? null : AnimCustom.get(AnimMarcio.NOMES[d.anim]);
		if (c != null) { AnimCustom.aplicar(c, d.animTempo, w, root, head, body, dir, esq, pernaD, pernaE); return; }
		float t = d.animTempo, u = t / AnimMarcio.duracao(d.anim);
		switch (d.anim) {
			case AnimMarcio.ESPREGUICAR -> {
				// os dois braços para cima e abertos, tremendo; cabeça para trás no bocejo
				misturar(dir, -2.9f + 0.06f * Mth.sin(t * 1.3f), 0f, 0.3f, w);
				misturar(esq, -2.9f + 0.06f * Mth.sin(t * 1.3f + 1f), 0f, -0.3f, w);
				head.xRot = Mth.lerp(w, head.xRot, -0.55f * Mth.sin(Mth.PI * u) - 0.1f);
				body.xRot = Mth.lerp(w, body.xRot, -0.08f);
			}
			case AnimMarcio.COCAR -> {
				// mão esquerda vai até a cabeça e coça rapidinho
				misturar(esq, -2.6f, -0.3f, 0.35f + 0.1f * Mth.sin(t * 1.8f), w);
				head.zRot = Mth.lerp(w, head.zRot, -0.15f);
				head.xRot = Mth.lerp(w, head.xRot, head.xRot + 0.1f);
			}
			case AnimMarcio.ACENAR -> {
				misturar(esq, -2.8f, 0f, -0.25f - 0.35f * Mth.sin(t * 0.7f), w);
				head.zRot = Mth.lerp(w, head.zRot, 0.1f * Mth.sin(t * 0.35f));
			}
			case AnimMarcio.COMEMORAR -> {
				// braços para cima pulando e o corpo gingando
				float pulo = 0.25f * Mth.sin(t * 0.9f);
				misturar(dir, -2.9f + pulo, 0f, 0.35f, w);
				misturar(esq, -2.9f + pulo, 0f, -0.35f, w);
				head.xRot = Mth.lerp(w, head.xRot, -0.2f);
				body.yRot = Mth.lerp(w, body.yRot, 0.15f * Mth.sin(t * 0.45f));
			}
			case AnimMarcio.CARINHO -> carinho(d, t, w, root, head, body, esq, pernaD, pernaE);
			default -> {}
		}
	}

	/**
	 * Carinho no Pinto (sentado no baú, atrás dele), de três jeitos. O corpo inteiro gira pouco, e as pernas
	 * só acompanham em parte, para a cintura não "quebrar"; quem alcança o Pinto é o ombro e o braço.
	 */
	private static void carinho(DadosMarcio d, float t, float w, ModelPart root, ModelPart head, ModelPart body,
	                            ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float afago = Mth.sin(t * 0.9f), afago2 = Mth.sin(t * 0.9f + 1f);
		float giro, pernas;
		switch (d.animVar) {
			case 1 -> { // de lado: o braço vai aberto para o lado e para trás, olhando para baixo
				giro = -0.3f; pernas = 0.8f;
				head.yRot = Mth.lerp(w, head.yRot, -1.1f);
				head.xRot = Mth.lerp(w, head.xRot, 0.3f);
				misturar(esq, 1.35f + 0.1f * afago, -0.3f, -0.55f + 0.12f * afago2, w);
			}
			case 2 -> { // meio de pé (MarcioCliente levanta um pouco), vira mais e se inclina para o Pinto
				giro = -0.55f; pernas = 0.45f;
				head.yRot = Mth.lerp(w, head.yRot, -0.7f);
				head.xRot = Mth.lerp(w, head.xRot, 0.35f);
				body.xRot = Mth.lerp(w, body.xRot, 0.2f);
				misturar(esq, 1.5f + 0.15f * afago, -0.3f, -0.45f + 0.08f * afago2, w);
			}
			default -> { // por cima do ombro, olhando para trás
				giro = -0.35f; pernas = 0.8f;
				head.yRot = Mth.lerp(w, head.yRot, -1.0f);
				head.xRot = Mth.lerp(w, head.xRot, 0.1f);
				body.xRot = Mth.lerp(w, body.xRot, -0.06f);
				misturar(esq, 1.9f + 0.12f * afago, 0.3f, -0.15f + 0.08f * afago2, w);
			}
		}
		root.yRot += giro * w;
		pernaD.yRot -= giro * pernas * w;
		pernaE.yRot -= giro * pernas * w;
	}

	/**
	 * O corpo acompanha o jeito de falar (Falas.humor) enquanto o balão está aberto: cabisbaixo quando
	 * está triste, gesticulando no ritmo das palavras quando está animado ou contando piada, punho fechado
	 * quando está bravo, encolhido com medo, mão no queixo contando o sonho. O braço esquerdo só gesticula
	 * se estiver livre (fora da briga com o peixe, das especiais e de subir/descer do barco).
	 */
	private static void expressao(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart body, ModelPart dir, ModelPart esq) {
		float w = d.falaAlpha;
		if (w <= 0 || d.humor == Falas.Humor.NEUTRO || d.trans != MarcioCliente.SEM) return;
		if (d.fase == AnimPesca.LUTA) return;
		boolean livre = (d.fase == AnimPesca.NADA || d.fase == AnimPesca.BOIANDO) && d.anim == AnimMarcio.NADA && d.teste == null;
		float wb = livre ? w : 0f, p = d.falaPulso, s = d.falaSeg;
		switch (d.humor) {
			case Falas.Humor.TRISTE -> {
				head.xRot += 0.45f * w;
				head.zRot += 0.08f * w;
				body.xRot += 0.12f * w;
				dir.y += 0.7f * w; // ombros caídos
				esq.y += 0.7f * w;
				if (wb > 0) misturar(esq, -0.2f, 0f, -0.02f, wb * 0.7f);
			}
			case Falas.Humor.SONO -> {
				head.xRot += (0.3f + 0.08f * Mth.sin(age * 0.05f)) * w;
				head.zRot += 0.15f * Mth.sin(age * 0.03f) * w;
				dir.y += 0.4f * w;
				esq.y += 0.4f * w;
			}
			case Falas.Humor.ALEGRE -> {
				head.xRot -= (0.12f + 0.06f * p) * w;
				root.y -= 0.3f * p * w; // pulinho a cada palavra
				if (wb > 0) misturar(esq, -0.9f - 0.4f * p, -0.3f, -0.35f, wb * 0.8f);
			}
			case Falas.Humor.ANIMADO -> {
				head.xRot -= (0.2f + 0.08f * p) * w;
				body.xRot -= 0.08f * w; // peito para fora
				root.y -= 0.6f * p * w;
				if (wb > 0) misturar(esq, -2.2f - 0.3f * p, 0f, -0.4f, wb);
			}
			case Falas.Humor.BRAVO -> {
				head.xRot += 0.1f * w;
				body.xRot += 0.15f * w; // vai para a frente
				if (wb > 0) misturar(esq, -1.4f + 0.25f * p, -0.1f, -0.2f, wb); // punho sacudindo no ritmo
			}
			case Falas.Humor.MEDO -> {
				head.yRot += 0.15f * Mth.sin(age * 1.7f) * w; // olha para os lados
				root.x += 0.15f * Mth.sin(age * 3f) * w; // tremendo
				dir.y += 0.6f * w;
				esq.y += 0.6f * w;
				if (wb > 0) misturar(esq, -1.0f, 0f, 0.25f, wb); // braço encolhido na frente do peito
			}
			case Falas.Humor.PIADA -> {
				head.zRot += 0.12f * w;
				head.yRot += 0.25f * Mth.sin(s * 1.5f) * w;
				// no fim, a risadinha: os ombros sacodem
				float risada = AnimPesca.clamp((w < 1 ? 1 - w : 0) * 3f) * Math.abs(Mth.sin(age * 1.2f));
				dir.y -= 0.5f * risada;
				esq.y -= 0.5f * risada;
				if (wb > 0) misturar(esq, -0.8f - 0.3f * p, -0.5f + 0.3f * Mth.sin(s * 3f), -0.3f, wb); // mão aberta explicando
			}
			case Falas.Humor.BEBADO -> {
				root.zRot += 0.08f * Mth.sin(age * 0.12f) * w;
				head.zRot += 0.2f * Mth.sin(age * 0.09f) * w;
				head.xRot += 0.1f * w;
				if (wb > 0) misturar(esq, -0.4f + 0.2f * Mth.sin(age * 0.1f), 0f, -0.4f + 0.2f * Mth.sin(age * 0.07f), wb);
			}
			case Falas.Humor.SONHO -> {
				head.xRot -= 0.15f * w; // olha para cima, lembrando
				head.yRot += 0.2f * w;
				if (wb > 0) misturar(esq, -2.0f, 0.6f, 0.35f, wb); // mão no queixo
			}
			case Falas.Humor.CARINHO -> head.yRot = Mth.lerp(0.8f * w, head.yRot, -0.9f); // fala virado para o Pinto
			default -> {}
		}
	}

	private static void misturar(ModelPart p, float x, float y, float z, float w) {
		p.xRot = Mth.lerp(w, p.xRot, x);
		p.yRot = Mth.lerp(w, p.yRot, y);
		p.zRot = Mth.lerp(w, p.zRot, z);
	}
}

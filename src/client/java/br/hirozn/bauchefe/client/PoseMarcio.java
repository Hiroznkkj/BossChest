package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Falas;
import br.hirozn.bauchefe.Lugares;
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

		if (d.assento != 0) sentarAssento(d, age, root, head, body, dir, esq, pernaD, pernaE);
		else if (d.sentado) levantar(d, age, r, root, head, body, dir, esq, pernaD, pernaE);

		emPe(d, root, head, body, esq, pernaD, pernaE);
		if (!exportando) pesFirmes(d, root, pernaD, pernaE);
		if (!exportando) {
			// customizadas (AnimCustom) no lugar das do código: a fase da pesca, ou "parado" sentado no barco
			AnimCustom.Anim c = pescando ? AnimCustom.get(AnimPesca.NOMES[d.fase]) : AnimCustom.get(AnimCustom.PARADO);
			if (c != null) AnimCustom.aplicar(c, pescando ? d.tempo : d.agora, pescando ? 1f : d.sentado ? 1f - d.pe : 0f,
					root, head, body, dir, esq, pernaD, pernaE);
		}
		transicao(d, age, head, body, dir, esq, pernaD, pernaE, root);
		especial(d, root, head, body, dir, esq, pernaD, pernaE);
		if (!exportando) {
			// camadas procedurais por cima de tudo (somam com o que estiver acontecendo, até no meio de sentar)
			Camadas.aplicar(new Corpo(d, root, head, body, dir, esq, pernaD, pernaE), age);
			expressao(d, age, root, head, body, dir, esq);
		}
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
		if (d.fase == AnimPesca.NADA) dir.xRot -= 0.3f * impulso;

		// balanço junto com o barco, girando em torno do assento (12 px abaixo do pivô do root)
		if (!exportando) {
			float bz = 0.035f * sentado * Mth.sin(age * 0.07f + r), bx = 0.02f * sentado * Mth.sin(age * 0.05f + r * 1.3f);
			root.zRot += bz;
			root.xRot += bx;
			root.x += 12f * Mth.sin(bz);
			root.y += 12f * (1f - Mth.cos(bz)) + 12f * (1f - Mth.cos(bx));
			root.z -= 12f * Mth.sin(bx);
		}
		// a mão faz força na borda no começo de levantar (e no fim de sentar), quando o ombro ainda está baixo;
		// subindo da água as duas mãos já estão na borda (transicao cuida delas)
		if (d.trans != MarcioCliente.SUBIR_AGUA || d.transFase == Trajeto.ACOMODA) apoio(d, root, esq, Mth.sin(Mth.PI * AnimPesca.clamp(pe * 1.5f)));
	}

	/**
	 * Mão esquerda na borda do barco: aponta para a borda de verdade (posição e giro do barco neste quadro);
	 * sem barco por perto, uma pose de apoio do lado do joelho.
	 */
	private static void apoio(DadosMarcio d, ModelPart root, ModelPart esq, float w) {
		if (w <= 0) return;
		if (d.bordaEsq == null || exportando) { misturar(esq, -0.15f, 0.1f, -0.55f, w); return; }
		alcancar(d, root, esq, d.bordaEsq, w, 0.3f);
	}

	/** Corpo só com o que alcancar precisa (root e braço esquerdo). */
	private static Corpo corpo(DadosMarcio d, ModelPart root, ModelPart esq) {
		return new Corpo(d, root, null, null, null, esq, null, null);
	}

	/**
	 * Leva a mão esquerda até o ponto do mundo; se o braço não chega, o corpo se inclina para aquele lado
	 * (em volta do quadril sentado, ou dos pés em pé) só o quanto falta, até 'max' radianos.
	 */
	private static void alcancar(DadosMarcio d, ModelPart root, ModelPart esq, net.minecraft.world.phys.Vec3 alvo, float w, float max) {
		corpo(d, root, esq).alcancar(Corpo.Lado.ESQUERDO, alvo, w, max);
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
	 * Subir e descer do barco, sentar e levantar (o caminho do corpo está no Trajeto; aqui ficam as pernas e os
	 * braços de cada fase). Andando: passos de verdade, tantos quanto a distância. Passando pela borda: uma perna e
	 * depois a outra por cima dela, com a mão na borda real. Da água: as duas mãos agarram a borda e o corpo se puxa.
	 */
	private static void transicao(DadosMarcio d, float age, ModelPart head, ModelPart body, ModelPart dir, ModelPart esq,
	                              ModelPart pernaD, ModelPart pernaE, ModelPart root) {
		if (d.trans == MarcioCliente.SEM) return;
		float v = d.transV;
		boolean agua = d.trans == MarcioCliente.SUBIR_AGUA;
		switch (d.transFase) {
			case Trajeto.ANDA -> {
				if (agua) { // nadando até o barco: braçadas e pernas batendo
					float b = age * 0.35f;
					misturar(dir, -2.4f + 0.6f * Mth.sin(b), 0f, 0.3f, 0.8f);
					misturar(esq, -2.4f + 0.6f * Mth.sin(b + Mth.PI), 0f, -0.3f, 0.8f);
					pernaD.xRot += 0.35f * Mth.sin(age * 0.9f);
					pernaE.xRot -= 0.35f * Mth.sin(age * 0.9f);
				} else andar(v, d.transPassos, root, body, dir, esq, pernaD, pernaE);
			}
			case Trajeto.PASSA -> {
				if (!agua) { passarPerna(d, v, head, body, esq, pernaD, pernaE, root); return; }
				// da água: as duas mãos na borda (de verdade), o corpo se puxa e a perna vem por último
				float e = AnimPesca.inOutSine(v);
				if (d.bordaEsq != null) {
					Ik.mirar(d, root, esq, d.bordaEsq, Ik.BRACO, 1f);
					org.joml.Vector3f m = Ik.noRoot(root, Ik.modelo(d, d.bordaEsq));
					Ik.mirar(dir, m.add(-6f, 0f, 0f), Ik.BRACO, 1f - AnimPesca.inOutSine((v - 0.8f) / 0.2f));
				} else { misturar(dir, -2.6f, 0f, 0.25f, 1f); misturar(esq, -2.6f, 0f, -0.25f, 1f); }
				body.xRot += 0.2f + 0.4f * Mth.sin(Mth.PI * v);
				head.xRot += Mth.lerp(e, -0.25f, 0.2f);
				pernaD.xRot -= 1.4f * Mth.sin(Mth.PI * AnimPesca.clamp((v - 0.3f) * 1.6f));
				pernaE.xRot -= 1.2f * Mth.sin(Mth.PI * AnimPesca.clamp((v - 0.5f) * 2f));
			}
			case Trajeto.VIRA -> { // vira de costas para o assento em dois passinhos
				float s2 = Mth.sin(v * 2 * Mth.PI);
				pernaD.xRot += 0.3f * s2;
				pernaE.xRot -= 0.3f * s2;
			}
			default -> {}
		}
	}

	/** Passos (fase andando da transição, 'v' de 0 a 1), com pernas, braço esquerdo e o corpo subindo e descendo. */
	private static void andar(float v, float passos, ModelPart root, ModelPart body, ModelPart dir, ModelPart esq,
	                          ModelPart pernaD, ModelPart pernaE) {
		float w = AnimPesca.inOutSine(Math.min(1f, Math.min(v, 1f - v) * 5f));
		float passo = v * passos;
		float s = Mth.sin(passo * Mth.PI) * w;
		pernaD.xRot += 0.6f * s;
		pernaE.xRot -= 0.6f * s;
		esq.xRot -= 0.5f * s;
		dir.xRot += 0.15f * s; // com a vara, balança pouco
		body.xRot += 0.06f * w;
		root.y -= 0.5f * Math.abs(Mth.sin(passo * Mth.PI)) * w; // sobe e desce a cada passo
	}

	/** Passa uma perna e depois a outra por cima da borda, com a mão esquerda apoiada nela (a borda de verdade). */
	private static void passarPerna(DadosMarcio d, float v, ModelPart head, ModelPart body, ModelPart esq,
	                                ModelPart pernaD, ModelPart pernaE, ModelPart root) {
		float arco = Mth.sin(Mth.PI * v);
		float ld = Mth.sin(Mth.PI * AnimPesca.clamp(v * 1.6f)), le = Mth.sin(Mth.PI * AnimPesca.clamp((v - 0.38f) * 1.6f));
		pernaD.xRot -= 1.3f * ld;
		pernaD.zRot += 0.12f * ld; // joelho para fora para passar pela borda
		pernaE.xRot -= 1.2f * le;
		pernaE.zRot -= 0.12f * le;
		root.zRot += 0.05f * (ld - le); // o peso fica no pé que está no chão
		body.xRot += 0.3f * arco;
		head.xRot += 0.25f * arco; // olha onde pisa
		apoio(d, root, esq, Math.min(1f, arco * 1.5f));
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
	 * Carinho no Pinto, de três jeitos. Com o Pinto à vista, a mão vai até a cabeça dele de verdade (onde ele
	 * estiver neste quadro) e o corpo só gira o quanto precisa para alcançar, com as pernas acompanhando em parte
	 * para a cintura não "quebrar". O afago sobe e desce em cima dele. Sem o Pinto, a pose fixa de antes.
	 */
	private static void carinho(DadosMarcio d, float t, float w, ModelPart root, ModelPart head, ModelPart body,
	                            ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float afago = Mth.sin(t * 0.9f), afago2 = Mth.sin(t * 0.9f + 1f);
		float giroMax, pernas;
		switch (d.animVar) {
			case 1 -> { giroMax = 0.45f; pernas = 0.8f; head.xRot = Mth.lerp(w, head.xRot, 0.3f); } // de lado, olhando para baixo
			case 2 -> { giroMax = 0.7f; pernas = 0.45f; body.xRot = Mth.lerp(w, body.xRot, 0.2f); } // meio de pé, inclinado
			default -> { giroMax = 0.5f; pernas = 0.8f; body.xRot = Mth.lerp(w, body.xRot, -0.06f); } // por cima do ombro
		}
		if (d.pinto != null && !exportando) {
			// o menor giro do corpo com que a mão alcança a cabeça dele
			float base = root.yRot, giro = -giroMax;
			for (float g = 0; g <= giroMax + 1e-3f; g += 0.05f) {
				root.yRot = base - g * w;
				org.joml.Vector3f alvo = Ik.noRoot(root, Ik.modelo(d, d.pinto));
				float dx = alvo.x - esq.x, dy = alvo.y - esq.y, dz = alvo.z - esq.z;
				if (Mth.sqrt(dx * dx + dy * dy + dz * dz) <= Ik.BRACO + 0.3f) { giro = -g; break; }
			}
			root.yRot = base + giro * w;
			pernaD.yRot -= giro * pernas * w;
			pernaE.yRot -= giro * pernas * w;
			// afago: a mão sobe e desce 1,5 px em cima da cabeça dele
			alcancar(d, root, esq, d.pinto.add(0, 0.04 + 0.05 * afago, 0), w, 0.35f);
			esq.zRot += 0.06f * afago2 * w;
			Ik.olhar(d, root, head, d.pinto, w * 0.9f);
			return;
		}
		float giro;
		switch (d.animVar) {
			case 1 -> { giro = -0.3f; head.yRot = Mth.lerp(w, head.yRot, -1.1f); misturar(esq, 1.35f + 0.1f * afago, -0.3f, -0.55f + 0.12f * afago2, w); }
			case 2 -> { giro = -0.55f; head.yRot = Mth.lerp(w, head.yRot, -0.7f); head.xRot = Mth.lerp(w, head.xRot, 0.35f); misturar(esq, 1.5f + 0.15f * afago, -0.3f, -0.45f + 0.08f * afago2, w); }
			default -> { giro = -0.35f; head.yRot = Mth.lerp(w, head.yRot, -1.0f); head.xRot = Mth.lerp(w, head.xRot, 0.1f); misturar(esq, 1.9f + 0.12f * afago, 0.3f, -0.15f + 0.08f * afago2, w); }
		}
		root.yRot += giro * w;
		pernaD.yRot -= giro * pernas * w;
		pernaE.yRot -= giro * pernas * w;
	}

	// ---------- sentar em qualquer lugar ----------

	/**
	 * Sentado num assento qualquer (degrau, cadeira, banco de mod, carrinho, chão) ou encostado na parede.
	 * O quadril desce até a superfície do assento e as pernas vão até o chão: o ângulo sai da altura do assento
	 * (cos = altura / comprimento da perna), então os pés ficam no chão em qualquer altura; se o assento for mais
	 * alto que a perna, os pés ficam balançando. No chão, perna cruzada. Na parede, encostado de braços cruzados.
	 */
	private static void sentarAssento(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart body,
	                                  ModelPart dir, ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float s = 1f - d.pe; // 1 sentado
		if (d.assento == Lugares.ENCOSTO) {
			float w = d.trans == MarcioCliente.SENTAR ? (d.transFase == Trajeto.ACOMODA ? AnimPesca.inOutSine(d.transV) : 0f)
					: d.trans == MarcioCliente.LEVANTAR ? (d.transFase == Trajeto.LEVANTA ? 1f - AnimPesca.inOutSine(d.transV) : 0f) : 1f;
			if (w <= 0) return;
			Ik.girarNosPes(root, -0.13f * w, 0f, 24f); // costas na parede
			pernaD.zRot -= 0.15f * w; // um pé cruzado na frente do outro
			pernaD.xRot -= 0.08f * w;
			pernaE.zRot += 0.03f * w;
			Ik.mirar(dir, new org.joml.Vector3f(2.5f, 7.5f, -3.5f), Ik.BRACO, w); // braços cruzados
			Ik.mirar(esq, new org.joml.Vector3f(-2.5f, 7f, -3.8f), Ik.BRACO, w);
			head.zRot += 0.06f * w;
			return;
		}
		if (d.assento == Lugares.DEITADO) {
			// primeiro senta no chão com as pernas para a frente, depois deita para trás em volta do quadril
			float senta = AnimPesca.inOutSine(Math.min(1f, s * 2f)), deita = AnimPesca.inOutSine(Math.max(0f, s * 2f - 1f));
			root.y += 10.8f * senta;
			pernaD.xRot = Mth.lerp(senta, pernaD.xRot, Mth.lerp(deita, -1.45f, -0.55f)); // joelho direito para cima
			pernaE.xRot = Mth.lerp(senta, pernaE.xRot, Mth.lerp(deita, -1.45f, 0f));
			pernaD.yRot = Mth.lerp(senta, pernaD.yRot, 0.1f);
			pernaE.yRot = Mth.lerp(senta, pernaE.yRot, -0.05f);
			Ik.girarNosPes(root, -Mth.HALF_PI * deita, 0f, 12f);
			root.y -= 1.0f * deita; // costas rentes ao chão
			// mãos atrás da cabeça
			Ik.mirar(dir, new org.joml.Vector3f(-2.5f, -6f, 3f), Ik.BRACO, deita);
			Ik.mirar(esq, new org.joml.Vector3f(2.5f, -6f, 3f), Ik.BRACO, deita);
			body.xRot += 0.08f * (senta - deita);
			return;
		}
		if (d.assento == Lugares.CHAO) {
			root.y += 10.8f * s; // senta no chão
			pernaD.xRot = Mth.lerp(s, pernaD.xRot, -1.45f);
			pernaE.xRot = Mth.lerp(s, pernaE.xRot, -1.45f);
			pernaD.yRot = Mth.lerp(s, pernaD.yRot, -0.7f); // cruzadas na frente
			pernaE.yRot = Mth.lerp(s, pernaE.yRot, 0.7f);
			body.xRot += 0.1f * s;
			Ik.mirar(dir, new org.joml.Vector3f(-2.5f, 11f, -5f), Ik.BRACO, s); // mãos no colo
			Ik.mirar(esq, new org.joml.Vector3f(2.5f, 11f, -5f), Ik.BRACO, s);
			return;
		}
		// cadeira: o quadril (pivô das pernas, 12 px acima dos pés) desce até a superfície do assento, perto da
		// beira da frente (as pernas descem por fora do assento; o Corpo.livrar acerta se ainda encostarem)
		float h = d.assentoAltura * 16f; // altura do assento até o chão (px)
		root.y += 12f * s;
		root.z -= (h >= Ik.PERNA ? 6f : 3f) * s; // pés no ar: senta na beirada, com as pernas penduradas para fora
		float coxa = h >= Ik.PERNA ? 0.25f : (float) Math.acos(Mth.clamp(h / Ik.PERNA, 0f, 1f));
		float balanca = h >= Ik.PERNA ? 0.18f * Mth.sin(age * 0.12f) : 0f; // pés no ar: balançando
		pernaD.xRot = Mth.lerp(s, pernaD.xRot, -coxa + balanca);
		pernaE.xRot = Mth.lerp(s, pernaE.xRot, -coxa - balanca);
		pernaD.yRot = Mth.lerp(s, pernaD.yRot, 0.12f);
		pernaE.yRot = Mth.lerp(s, pernaE.yRot, -0.12f);
		body.xRot += 0.08f * s;
		float impulso = Mth.sin(Mth.PI * d.pe);
		body.xRot += 0.35f * impulso; // pega impulso para levantar e para sentar
		head.xRot += 0.2f * impulso;
		// assento alto (subindo de costas): as duas mãos no assento, dos lados, empurrando o corpo para cima
		boolean subindo = d.assentoPos != null && d.assentoAltura > 0.6f
				&& (d.trans == MarcioCliente.SENTAR && d.transFase == Trajeto.ACOMODA || d.trans == MarcioCliente.LEVANTAR && d.transFase == Trajeto.LEVANTA);
		if (subindo) {
			float empurra = Mth.sin(Mth.PI * d.transV);
			float r = d.assentoYaw * Mth.DEG_TO_RAD;
			net.minecraft.world.phys.Vec3 esqDele = new net.minecraft.world.phys.Vec3(Mth.cos(r), 0, Mth.sin(r));
			Corpo c = new Corpo(d, root, head, body, dir, esq, pernaD, pernaE);
			c.mao(Corpo.Lado.ESQUERDO, d.assentoPos.add(esqDele.scale(0.38)).add(0, 0.02, 0), empurra);
			c.mao(Corpo.Lado.DIREITO, d.assentoPos.add(esqDele.scale(-0.38)).add(0, 0.02, 0), empurra);
			return;
		}
		// mãos nos joelhos (a direita segura a vara, se tiver)
		float jd = 9f;
		Ik.mirar(dir, new org.joml.Vector3f(pernaD.x - 0.5f, pernaD.y + jd * Mth.cos(coxa), pernaD.z - jd * Mth.sin(coxa)), Ik.BRACO, s * 0.9f);
		Ik.mirar(esq, new org.joml.Vector3f(pernaE.x + 0.5f, pernaE.y + jd * Mth.cos(coxa), pernaE.z - jd * Mth.sin(coxa)), Ik.BRACO, s * 0.9f);
	}

	/**
	 * Pés no chão de verdade, parado em terra: num degrau ou na beira de um bloco, o quadril desce até o pé de
	 * baixo alcançar o chão dele (até 4 px) e a outra perna dobra para a frente para o pé ficar no seu chão.
	 */
	private static void pesFirmes(DadosMarcio d, ModelPart root, ModelPart pernaD, ModelPart pernaE) {
		if (Float.isNaN(d.chaoD) || Float.isNaN(d.chaoE)) return;
		float w = Mth.clamp(1f - d.andar * 3f, 0f, 1f);
		float hd = d.chaoD * 16f, he = d.chaoE * 16f;
		if (w <= 0 || Math.abs(hd - he) < 0.5f) return;
		float desce = Mth.clamp(-Math.min(hd, he), 0f, 4f);
		root.y += desce * w;
		for (int i = 0; i < 2; i++) {
			ModelPart perna = i == 0 ? pernaD : pernaE;
			float alt = (Ik.PERNA - desce) - (i == 0 ? hd : he); // do quadril até o chão desse pé
			if (alt < Ik.PERNA) perna.xRot -= (float) Math.acos(Mth.clamp(alt / Ik.PERNA, 0f, 1f)) * w;
		}
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

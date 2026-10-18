package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pose do Márcio por cima da pose humanoide vanilla: pesca, briga com peixe difícil (em pé),
 * nado, respiração, balanço no barco e as animações especiais. Inspirado no estilo do Fresh Animations
 * (FreshLX): movimentos pequenos e contínuos, fase aleatória por entidade e tudo misturado com easing.
 * Braço direito segura a vara; o esquerdo fica livre. zRot positivo abre o braço direito; no esquerdo é negativo.
 */
public final class PoseMarcio {
	private PoseMarcio() {}

	public static void aplicar(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart body,
	                           ModelPart dir, ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float r = d.rFase;
		float resp = Mth.sin(age * 0.09f + r); // respiração, ~3,5 s
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
		head.zRot += 0.02f * Mth.sin(age * 0.05f + r * 2f); // inclina a cabeça de leve, à toa

		if (d.sentado) levantar(d, age, r, root, head, body, dir, esq, pernaD, pernaE);

		emPe(d, age, root, head, body, esq, pernaD, pernaE);
		nadar(d, age, root, head, dir, esq, pernaD, pernaE);
		especial(d, root, head, body, dir, esq, pernaD, pernaE);
	}

	/**
	 * Sentar e levantar no barco. 'pe' vai de 0 (sentado) a 1 (em pé). As pernas giram da horizontal para a
	 * vertical e o quadril sobe junto com elas, de um jeito que os pés ficam no chão do barco (a altura sai do
	 * ângulo da perna, AnimPesca.alturaAssento). No meio do movimento o corpo e a cabeça vão para a frente e os
	 * braços acompanham, como quem pega impulso.
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
		head.xRot += 0.25f * impulso;
		esq.xRot -= 0.6f * impulso;
		if (d.fase == AnimPesca.NADA) dir.xRot -= 0.5f * impulso;

		// balanço junto com o barco, girando em torno do assento (12 px abaixo do pivô do root)
		float bz = 0.035f * sentado * Mth.sin(age * 0.07f + r), bx = 0.02f * sentado * Mth.sin(age * 0.05f + r * 1.3f);
		root.zRot += bz;
		root.xRot += bx;
		root.x += 12f * Mth.sin(bz);
		root.y += 12f * (1f - Mth.cos(bz)) + 12f * (1f - Mth.cos(bx));
		root.z -= 12f * Mth.sin(bx);
	}

	/** Peixe difícil: de pé (levantar), pernas abertas, corpo para trás puxando a vara com as duas mãos. */
	private static void emPe(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart body,
	                         ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		float w = d.fase == AnimPesca.LUTA ? AnimPesca.inOutSine(d.tempo / 10f)
				: d.fase == AnimPesca.PUXANDO && d.lutou ? 1f - AnimPesca.inOutSine(d.tempo / AnimPesca.DUR_PUXANDO) : 0f;
		if (w <= 0) return;
		float luta = d.fase == AnimPesca.LUTA ? AnimPesca.luta(d.tempo) : 0f, t = d.tempo;
		pernaD.zRot = Mth.lerp(w, pernaD.zRot, 0.12f); // pernas abertas para se equilibrar
		pernaE.zRot = Mth.lerp(w, pernaE.zRot, -0.12f);
		// as duas mãos na vara, o corpo cede e puxa junto com o peixe
		float tranco = Mth.sin(t * 0.55f);
		body.xRot = Mth.lerp(luta, body.xRot, -0.12f - 0.08f * tranco);
		head.xRot = Mth.lerp(luta, head.xRot, 0.2f + 0.05f * Mth.sin(t * 1.1f));
		if (luta > 0) {
			esq.xRot = Mth.lerp(luta, esq.xRot, -1.5f + 0.35f * tranco);
			esq.yRot = Mth.lerp(luta, esq.yRot, 0.45f);
			esq.zRot = Mth.lerp(luta, esq.zRot, 0f);
		}
	}

	/** Nado de cachorrinho: braços em círculo alternados, pernas batendo, cabeça para fora da água. */
	private static void nadar(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart dir, ModelPart esq,
	                          ModelPart pernaD, ModelPart pernaE) {
		float n = d.nado;
		if (n <= 0) return;
		float c = age * 0.33f;
		dir.xRot = Mth.lerp(n, dir.xRot, -2.3f + 0.9f * Mth.sin(c));
		dir.zRot = Mth.lerp(n, dir.zRot, 0.25f + 0.2f * Mth.cos(c));
		esq.xRot = Mth.lerp(n, esq.xRot, -2.3f + 0.9f * Mth.sin(c + Mth.PI));
		esq.zRot = Mth.lerp(n, esq.zRot, -0.25f - 0.2f * Mth.cos(c + Mth.PI));
		pernaD.xRot = Mth.lerp(n, pernaD.xRot, 0.5f * Mth.sin(age * 0.5f));
		pernaE.xRot = Mth.lerp(n, pernaE.xRot, 0.5f * Mth.sin(age * 0.5f + Mth.PI));
		head.xRot = Mth.lerp(n, head.xRot, -0.35f);
		root.y += n * 1.2f * Mth.sin(c * 2f); // sobe e desce com as braçadas
	}

	private static void especial(DadosMarcio d, ModelPart root, ModelPart head, ModelPart body, ModelPart dir, ModelPart esq,
	                             ModelPart pernaD, ModelPart pernaE) {
		float w = AnimMarcio.peso(d.anim, d.animTempo);
		if (w <= 0) return;
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
			case AnimMarcio.CARINHO -> {
				// vira o tronco e a cabeça para trás (o Pinto fica no baú), estica o braço esquerdo
				// para cima e para trás e afaga a cabeça dele; as pernas continuam para a frente
				float giro = 0.9f * w;
				root.yRot += giro;
				pernaD.yRot -= giro;
				pernaE.yRot -= giro;
				head.yRot = Mth.lerp(w, head.yRot, 0.8f);
				head.xRot = Mth.lerp(w, head.xRot, 0.15f);
				misturar(esq, 2.0f + 0.18f * Mth.sin(t * 0.9f), -0.2f, -0.35f + 0.08f * Mth.sin(t * 0.9f + 1f), w);
				body.xRot = Mth.lerp(w, body.xRot, -0.08f);
			}
			default -> {}
		}
	}

	private static void misturar(ModelPart p, float x, float y, float z, float w) {
		p.xRot = Mth.lerp(w, p.xRot, x);
		p.yRot = Mth.lerp(w, p.yRot, y);
		p.zRot = Mth.lerp(w, p.zRot, z);
	}
}

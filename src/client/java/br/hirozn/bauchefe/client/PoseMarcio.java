package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pose do Márcio por cima da pose vanilla: braços da pesca, respiração, balanço no barco,
 * pernas sentadas e as animações especiais. Inspirado no estilo do Fresh Animations (FreshLX):
 * movimentos pequenos e contínuos, fase aleatória por entidade e tudo misturado com easing.
 * Braço direito segura a vara; o esquerdo fica livre (apoio, aceno, carinho no Pinto).
 * zRot positivo abre o braço direito para fora; no esquerdo é negativo.
 */
public final class PoseMarcio {
	/** Nomes dos dois braços que o VillagerMeshMixin acrescenta ao modelo. */
	public static final String BRACO_D = "bauchefe_braco_direito", BRACO_E = "bauchefe_braco_esquerdo";

	private PoseMarcio() {}

	public static void aplicar(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart body,
	                           ModelPart dir, ModelPart esq, ModelPart rightLeg, ModelPart leftLeg) {
		float r = d.rFase;
		float resp = Mth.sin(age * 0.09f + r); // respiração, ~3,5 s

		// braço direito: pesca (arremesso, espera, mordida, puxada)
		dir.xRot = AnimPesca.braco(d.fase, d.tempo);
		dir.zRot = 0.06f + 0.02f * resp;
		// braço esquerdo: apoiado no joelho quando sentado, solto ao lado do corpo em pé; ajuda a puxar o peixe
		float puxa = d.fase == AnimPesca.PUXANDO || d.fase == AnimPesca.MORDIDA ? (dir.xRot - (-0.35f)) * 0.45f : 0f;
		esq.xRot = (d.sentado ? -0.55f : -0.1f) + puxa + 0.04f * Mth.sin(age * 0.06f + r * 1.7f);
		esq.zRot = -0.06f - 0.02f * resp;

		// respiração: o peito enche de leve, ombros e cabeça acompanham
		body.zScale = 1f + 0.03f * resp;
		body.xScale = 1f + 0.012f * resp;
		dir.y -= 0.25f * resp;
		esq.y -= 0.25f * resp;
		head.y -= 0.12f * resp;
		head.zRot += 0.02f * Mth.sin(age * 0.05f + r * 2f); // inclina a cabeça de leve, à toa

		if (d.sentado) {
			// sentado no barco, joelhos para a frente
			rightLeg.xRot = -1.4f; rightLeg.yRot = 0.3f;
			leftLeg.xRot = -1.4f; leftLeg.yRot = -0.3f;
			// balanço junto com o barco, girando em torno do assento (12 px abaixo do pivô do root)
			float bz = 0.035f * Mth.sin(age * 0.07f + r), bx = 0.02f * Mth.sin(age * 0.05f + r * 1.3f);
			root.zRot += bz;
			root.xRot += bx;
			root.x += 12f * Mth.sin(bz);
			root.y += 12f * (1f - Mth.cos(bz)) + 12f * (1f - Mth.cos(bx));
			root.z -= 12f * Mth.sin(bx);
		}

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
				// vira para trás (o Pinto fica no baú) e faz carinho com a mão esquerda
				misturar(esq, 0.9f + 0.15f * Mth.sin(t * 0.9f), 0.3f, -0.45f, w);
				body.yRot = Mth.lerp(w, body.yRot, -0.15f); // pouco: os braços não giram junto com o tronco
				head.xRot = Mth.lerp(w, head.xRot, 0.25f);
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

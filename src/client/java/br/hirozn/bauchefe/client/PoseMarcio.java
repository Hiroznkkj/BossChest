package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pose do Márcio por cima da pose vanilla: braços da pesca, respiração, balanço no barco,
 * pernas sentadas e as animações especiais. Inspirado no estilo do Fresh Animations (FreshLX):
 * movimentos pequenos e contínuos, fase aleatória por entidade e tudo misturado com easing.
 */
public final class PoseMarcio {
	private PoseMarcio() {}

	public static void aplicar(DadosMarcio d, float age, ModelPart root, ModelPart head, ModelPart body, ModelPart arms,
	                           ModelPart rightLeg, ModelPart leftLeg) {
		float r = d.rFase;

		// pesca (arremesso, espera, mordida, puxada)
		arms.xRot = AnimPesca.braco(d.fase, d.tempo);

		// respiração: o peito enche de leve e os braços e a cabeça acompanham
		float resp = Mth.sin(age * 0.09f + r);
		body.zScale = 1f + 0.03f * resp;
		body.xScale = 1f + 0.012f * resp;
		arms.y -= 0.25f * resp;
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
				// braços para cima, tremendo, cabeça para trás no bocejo
				misturar(arms, -3.0f + 0.06f * Mth.sin(t * 1.3f), arms.yRot, 0.05f * Mth.sin(t * 0.4f), w);
				head.xRot = Mth.lerp(w, head.xRot, -0.55f * Mth.sin(Mth.PI * u) - 0.1f);
				body.xRot = Mth.lerp(w, body.xRot, -0.08f);
			}
			case AnimMarcio.COCAR -> {
				// lado direito dos braços sobe até a cabeça e coça rapidinho
				misturar(arms, -2.3f, 0.3f, -0.35f + 0.08f * Mth.sin(t * 1.8f), w);
				head.zRot = Mth.lerp(w, head.zRot, 0.15f);
				head.xRot = Mth.lerp(w, head.xRot, head.xRot + 0.1f);
			}
			case AnimMarcio.ACENAR -> {
				misturar(arms, -2.7f, 0f, 0.35f * Mth.sin(t * 0.7f), w);
				head.zRot = Mth.lerp(w, head.zRot, 0.1f * Mth.sin(t * 0.35f));
			}
			case AnimMarcio.COMEMORAR -> {
				// braços para cima pulando e o corpo gingando
				misturar(arms, -3.0f + 0.25f * Mth.sin(t * 0.9f), 0f, 0f, w);
				head.xRot = Mth.lerp(w, head.xRot, -0.2f);
				body.yRot = Mth.lerp(w, body.yRot, 0.15f * Mth.sin(t * 0.45f));
				arms.yRot = Mth.lerp(w, arms.yRot, 0.15f * Mth.sin(t * 0.45f));
			}
			case AnimMarcio.CARINHO -> {
				// vira para trás (o Pinto fica no baú) e faz carinho
				misturar(arms, -1.3f + 0.12f * Mth.sin(t * 0.9f), 0.9f, 0f, w);
				body.yRot = Mth.lerp(w, body.yRot, 0.35f);
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

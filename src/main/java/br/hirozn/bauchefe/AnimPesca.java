package br.hirozn.bauchefe;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Matemática da animação de pesca, usada no servidor (duração das fases) e no cliente
 * (ângulo dos braços, ponta da vara e trajetória da boia). Tempo em ticks, com fração.
 */
public final class AnimPesca {
	public static final int NADA = 0, PREPARA = 1, VOO = 2, BOIANDO = 3, MORDIDA = 4, PUXANDO = 5, LUTA = 6;
	public static final int DUR_PREPARA = 10, DUR_VOO = 12, DUR_MORDIDA = 14, DUR_PUXANDO = 18;
	/** Nome de cada fase nas animações customizadas (config/bauchefe/animacoes), na ordem dos ids. */
	public static final String[] NOMES = {null, "pesca_preparar", "pesca_lancar", "pesca_boiando", "pesca_mordida", "pesca_puxar", "pesca_luta"};
	/** Peixe difícil: briga de 4,5 s entre a mordida e a puxada (ele fica de pé e o barco balança). */
	public static final int DUR_LUTA = 90;
	/** Na briga ele leva 16 ticks para ficar de pé (apoiando a mão na borda) antes de começar a puxar. */
	public static final float LEVANTA = 16f;
	/** Em VOO a boia sai da vara neste tick e chega na água no fim da fase. */
	public static final int SOLTA = 3;
	/** Em PUXANDO a boia volta até a vara nestes ticks. */
	public static final int VOLTA = 7;

	// rotação X do braço direito do pescador (0 = caído ao lado do corpo, negativo = para a frente/cima)
	private static final float DESCANSO = -0.35f, SEGURANDO = -0.6f, ATRAS = -2.7f, CHICOTE = -0.25f, PUXADO = -2.1f;

	// ponta da vara no espaço do braço direito (blocos, y para baixo, -z para a frente); ajustar no visual
	private static final double PONTA_X = -0.05, PONTA_Y = 0.44, PONTA_Z = -0.70;
	/** Quanto o Márcio desce no assento do barco (blocos): o modelo de jogador fica alto no lugar do villager. */
	public static final float ASSENTO = 0.5f;
	/** Ângulo das pernas sentado (rad), o mesmo do jogador num barco. */
	public static final float COXA = 1.4137f;
	// pivô do braço direito (ombro), igual ao do pillager
	private static final double OMBRO_X = -5 / 16.0, OMBRO_Y = 2 / 16.0;

	private AnimPesca() {}

	// ---------- easing ----------

	public static float inOutCubic(float u) { u = clamp(u); return u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2; }
	public static float inOutSine(float u) { u = clamp(u); return -(Mth.cos((float) Math.PI * u) - 1) / 2; }
	public static float outCubic(float u) { u = clamp(u); float v = 1 - u; return 1 - v * v * v; }
	public static float inQuad(float u) { u = clamp(u); return u * u; }
	public static float outQuad(float u) { u = clamp(u); return 1 - (1 - u) * (1 - u); }
	public static float clamp(float u) { return u < 0 ? 0 : u > 1 ? 1 : u; }

	// ---------- braços ----------

	/** Rotação X do braço direito na fase 'fase', 't' ticks depois de ela começar. Contínua entre as fases. */
	public static float braco(int fase, float t) {
		return switch (fase) {
			case PREPARA -> Mth.lerp(inOutCubic(t / DUR_PREPARA), DESCANSO, ATRAS); // levanta a vara para trás
			case VOO -> t < 4
					? Mth.lerp(inQuad(t / 4), ATRAS, CHICOTE) // lança rápido, passando um pouco do ponto
					: Mth.lerp(inOutSine((t - 4) / (DUR_VOO - 4)), CHICOTE, SEGURANDO);
			case BOIANDO -> SEGURANDO + 0.035f * Mth.sin(t * 0.09f); // respira de leve
			case MORDIDA -> {
				float u = clamp(t / DUR_MORDIDA);
				yield SEGURANDO + 0.18f * Mth.sin((float) Math.PI * u) + 0.06f * Mth.sin(t * 1.6f) * (1 - u); // o peixe puxa a linha
			}
			case PUXANDO -> t < 5
					? Mth.lerp(outCubic(t / 5), SEGURANDO, PUXADO) // puxa para cima
					: Mth.lerp(inOutCubic((t - 5) / (DUR_PUXANDO - 5)), PUXADO, DESCANSO);
			case LUTA -> Mth.lerp(luta(t), SEGURANDO, -1.7f + 0.35f * Mth.sin(t * 0.55f) + 0.1f * Mth.sin(t * 1.7f)); // puxa e cede
			default -> DESCANSO;
		};
	}

	/**
	 * Quanto o quadril está abaixado (1 sentado, 0 em pé) para 'pe' de 0 a 1: sai do ângulo da perna,
	 * para os pés ficarem no chão enquanto ele levanta ou senta.
	 */
	public static float alturaAssento(float pe) {
		return (1f - Mth.cos(COXA * (1f - clamp(pe)))) / (1f - Mth.cos(COXA));
	}

	/** Intensidade da briga: começa quando ele termina de levantar, entra em 10 ticks e sai nos últimos 10. */
	public static float luta(float t) {
		return inOutSine(Math.min(1f, Math.min((t - LEVANTA * 0.6f) / 10f, (DUR_LUTA - t) / 10f)));
	}

	/** De pé (1) ou sentado (0) na pesca: levanta no começo da briga e senta durante a puxada que vem depois dela. */
	public static float peLuta(int fase, float t, boolean lutou) {
		return fase == LUTA ? inOutSine(t / LEVANTA) : fase == PUXANDO && lutou ? 1f - inOutSine(t / DUR_PUXANDO) : 0f;
	}

	// ---------- ponta da vara ----------

	/**
	 * Ponta da vara no mundo, refazendo as transformações do LivingEntityRenderer e do VillagerModel
	 * (rotação 180 - corpo, escala -1,-1,1, translate -1.501, ombro e rotação do braço direito).
	 */
	public static Vec3 ponta(LivingEntity v, float partial, int fase, float t, boolean lutou) {
		float a = braco(fase, t);
		double c = Mth.cos(a), s = Mth.sin(a);
		// rotação X do braço (y para baixo, -z para a frente)
		double mx = OMBRO_X + PONTA_X, my = OMBRO_Y + PONTA_Y * c - PONTA_Z * s, mz = PONTA_Y * s + PONTA_Z * c;
		// escala -1,-1,1 e translate -1.501
		double x = -mx, y = 1.501 - my, z = mz;
		// sentado no barco ele desce ASSENTO; na briga com o peixe fica de pé de novo (mesma conta da PoseMarcio)
		if (v.isPassenger()) {
			y -= ASSENTO * alturaAssento(peLuta(fase, t, lutou));
		}
		float yaw = (180f - Mth.rotLerp(partial, v.yBodyRotO, v.yBodyRot)) * Mth.DEG_TO_RAD;
		double cy = Mth.cos(yaw), sy = Mth.sin(yaw);
		Vec3 base = v.getPosition(partial);
		float esc = v.getScale();
		return base.add((x * cy + z * sy) * esc, y * esc, (-x * sy + z * cy) * esc);
	}

	// ---------- boia ----------

	/** Arco da boia: horizontal com leve desaceleração, vertical em parábola. */
	public static Vec3 arco(Vec3 de, Vec3 para, float u, double altura) {
		u = clamp(u);
		float h = outQuad(u);
		return new Vec3(Mth.lerp(h, de.x, para.x), Mth.lerp(u, de.y, para.y) + altura * 4 * u * (1 - u), Mth.lerp(h, de.z, para.z));
	}
}

package br.hirozn.bauchefe;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Matemática da animação de pesca, usada no servidor (duração das fases) e no cliente
 * (ângulo dos braços, ponta da vara e trajetória da boia). Tempo em ticks, com fração.
 */
public final class AnimPesca {
	public static final int NADA = 0, PREPARA = 1, VOO = 2, BOIANDO = 3, MORDIDA = 4, PUXANDO = 5;
	public static final int DUR_PREPARA = 10, DUR_VOO = 12, DUR_MORDIDA = 14, DUR_PUXANDO = 18;
	/** Em VOO a boia sai da vara neste tick e chega na água no fim da fase. */
	public static final int SOLTA = 3;
	/** Em PUXANDO a boia volta até a vara nestes ticks. */
	public static final int VOLTA = 7;

	// ângulos do "arms" do VillagerModel (vanilla: -0.75, braços cruzados)
	private static final float DESCANSO = -0.75f, SEGURANDO = -1.05f, ATRAS = -2.5f, CHICOTE = -0.55f, PUXADO = -2.3f;

	// ponta da vara no espaço do "arms" (blocos, y para baixo, -z para a frente); ajustar no visual
	private static final double PONTA_X = -0.40, PONTA_Y = 0.19, PONTA_Z = -0.70;
	private static final double ARMS_Y = 3 / 16.0, ARMS_Z = -1 / 16.0;

	private AnimPesca() {}

	// ---------- easing ----------

	public static float inOutCubic(float u) { u = clamp(u); return u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2; }
	public static float inOutSine(float u) { u = clamp(u); return -(Mth.cos((float) Math.PI * u) - 1) / 2; }
	public static float outCubic(float u) { u = clamp(u); float v = 1 - u; return 1 - v * v * v; }
	public static float inQuad(float u) { u = clamp(u); return u * u; }
	public static float outQuad(float u) { u = clamp(u); return 1 - (1 - u) * (1 - u); }
	static float clamp(float u) { return u < 0 ? 0 : u > 1 ? 1 : u; }

	// ---------- braços ----------

	/** Rotação X do "arms" na fase 'fase', 't' ticks depois de ela começar. Contínua entre as fases. */
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
			default -> DESCANSO;
		};
	}

	// ---------- ponta da vara ----------

	/**
	 * Ponta da vara no mundo, refazendo as transformações do LivingEntityRenderer e do VillagerModel
	 * (rotação 180 - corpo, escala -1,-1,1, translate -1.501, pivô e rotação do "arms").
	 */
	public static Vec3 ponta(LivingEntity v, float partial, int fase, float t) {
		float a = braco(fase, t);
		double c = Mth.cos(a), s = Mth.sin(a);
		// rotação X do arms (y para baixo, -z para a frente)
		double mx = PONTA_X, my = ARMS_Y + PONTA_Y * c - PONTA_Z * s, mz = ARMS_Z + PONTA_Y * s + PONTA_Z * c;
		// escala -1,-1,1 e translate -1.501
		double x = -mx, y = 1.501 - my, z = mz;
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

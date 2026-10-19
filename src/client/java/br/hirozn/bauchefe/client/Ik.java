package br.hirozn.bauchefe.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cinemática inversa simples para o modelo de jogador (braço e perna são uma peça só, sem cotovelo e joelho):
 * em vez de ângulos fixos, o membro aponta para um ponto de verdade (a borda do barco, a cabeça do Pinto,
 * o chão embaixo do pé, a mão de quem bate o hi five). Também converte pontos do mundo para o espaço do modelo,
 * refazendo as transformações do LivingEntityRenderer (giro 180 - corpo, escala -1,-1,1, translate -1,501).
 * Espaço do modelo: pixels, y para baixo, frente = -z, esquerda do NPC = +x.
 */
public final class Ik {
	/** Comprimento até a mão (braço) e até o pé (perna), em pixels: medidos dos cubos do modelo (medir). */
	public static float BRACO = 10f, PERNA = 12f;

	private Ik() {}

	/** Mede braço e perna pelos cubos do modelo (do pivô até a ponta), em vez de números fixos. */
	public static void medir(ModelPart braco, ModelPart perna) {
		BRACO = ponta(braco, 10f) - 0.5f; // a mão fica meio pixel antes da ponta
		PERNA = ponta(perna, 12f);
	}

	private static float ponta(ModelPart p, float padrao) {
		float[] max = {Float.NaN};
		p.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, caminho, i, cubo) -> {
			if (caminho.isEmpty() || !caminho.contains("/")) max[0] = Float.isNaN(max[0]) ? cubo.maxY : Math.max(max[0], cubo.maxY);
		});
		return Float.isNaN(max[0]) ? padrao : max[0];
	}

	/** Ponto do mundo no espaço do modelo (antes do root). d.mx..mz é onde o corpo é desenhado; d.myaw o giro dele. */
	public static Vector3f modelo(DadosMarcio d, Vec3 w) {
		double rx = w.x - d.mx, ry = w.y - d.my, rz = w.z - d.mz;
		float a = (180f - d.myaw) * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a);
		double x = rx * c - rz * s, z = rx * s + rz * c; // desfaz o rotateY(a)
		return new Vector3f((float) -x * 16f, (float) (-ry + 1.501) * 16f, (float) z * 16f);
	}

	/** Ponto do espaço do modelo para o mundo (o caminho contrário de modelo). */
	public static Vec3 mundo(DadosMarcio d, float px, float py, float pz) {
		double x = -px / 16.0, y = -(py / 16.0 - 1.501), z = pz / 16.0;
		float a = (180f - d.myaw) * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a);
		return new Vec3(d.mx + x * c + z * s, d.my + y, d.mz - x * s + z * c);
	}

	/** Ponto do espaço do modelo no espaço de dentro do root (tirando o deslocamento e o giro dele). */
	public static Vector3f noRoot(ModelPart root, Vector3f m) {
		Matrix4f t = new Matrix4f().translate(root.x, root.y, root.z).rotateZYX(root.zRot, root.yRot, root.xRot)
				.scale(root.xScale, root.yScale, root.zScale).invert();
		return t.transformPosition(new Vector3f(m));
	}

	/**
	 * Aponta o membro (braço ou perna, que no modelo fica para baixo, +y) para o ponto 'alvo' (espaço do root),
	 * misturando com a pose atual pelo peso 'w'. Devolve quanto falta para alcançar (pixels; 0 = alcança).
	 */
	public static float mirar(ModelPart membro, Vector3f alvo, float comprimento, float w) {
		float dx = alvo.x - membro.x, dy = alvo.y - membro.y, dz = alvo.z - membro.z;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-3f || w <= 0) return 0;
		// direção (0,1,0) girada por Rz(z)·Rx(x) = (-cos x·sen z, cos x·cos z, sen x)
		float xr = (float) Math.asin(Mth.clamp(dz / len, -1f, 1f));
		float zr = (float) Mth.atan2(-dx, dy);
		membro.xRot = angulo(w, membro.xRot, xr);
		membro.yRot = Mth.lerp(w, membro.yRot, 0f);
		membro.zRot = angulo(w, membro.zRot, zr);
		return Math.max(0, len - comprimento);
	}

	/** Mesmo que mirar, com o alvo no mundo. */
	public static float mirar(DadosMarcio d, ModelPart root, ModelPart membro, Vec3 alvo, float comprimento, float w) {
		if (alvo == null || w <= 0) return 0;
		return mirar(membro, noRoot(root, modelo(d, alvo)), comprimento, w);
	}

	/** Vira a cabeça para olhar o ponto (espaço do root), com limite para não quebrar o pescoço. */
	public static void olhar(ModelPart head, Vector3f alvo, float w) {
		float dx = alvo.x - head.x, dy = alvo.y - head.y + 4f, dz = alvo.z - head.z; // olhos ~4 px acima do pescoço
		float yaw = Mth.clamp((float) Mth.atan2(-dx, -dz), -1.4f, 1.4f);
		float pitch = Mth.clamp((float) Mth.atan2(dy, Mth.sqrt(dx * dx + dz * dz)), -0.9f, 1.0f);
		head.yRot = angulo(w, head.yRot, yaw);
		head.xRot = angulo(w, head.xRot, pitch);
	}

	public static void olhar(DadosMarcio d, ModelPart root, ModelPart head, Vec3 alvo, float w) {
		if (alvo != null && w > 0) olhar(head, noRoot(root, modelo(d, alvo)), w);
	}

	/**
	 * Inclina o corpo inteiro girando em volta dos pés (que ficam no lugar): 'ax' positivo para a frente,
	 * negativo para trás; 'az' para os lados (positivo para a esquerda dele). 'altura' é a distância do pivô do root até os pés (24 px em pé).
	 */
	public static void girarNosPes(ModelPart root, float ax, float az, float altura) {
		root.xRot += ax;
		root.zRot += az;
		root.y += altura * (2f - Mth.cos(ax) - Mth.cos(az));
		root.z -= altura * Mth.sin(ax);
		root.x += altura * Mth.sin(az);
	}

	/** Interpolação de ângulo pelo caminho mais curto. */
	public static float angulo(float w, float de, float para) {
		return de + Mth.wrapDegrees((para - de) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * w;
	}
}

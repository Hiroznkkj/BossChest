package br.hirozn.bauchefe.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * O esqueleto padrão de todos os NPCs: o humanoide do Minecraft (raiz, cabeça, tronco, dois braços, duas pernas).
 * Qualquer modelo humanoide (jogador, aldeão com corpo de jogador, um modelo de mod) se encaixa nele, e as
 * animações (Blockbench, Emotecraft, o formato próprio) são escritas para estes ossos. O tamanho de cada parte é
 * medido do cubo dela no modelo, então braço fino, perna comprida ou cabeça grande já entram certos na colisão.
 */
public final class Esqueleto {
	public static final int CABECA = 0, TRONCO = 1, BRACO_D = 2, BRACO_E = 3, PERNA_D = 4, PERNA_E = 5, OSSOS = 6;
	/** Nomes dos ossos nas animações (iguais aos do Blockbench e do Emotecraft, em minúsculas e sem "_"). */
	public static final String[] NOMES = {"head", "body", "rightarm", "leftarm", "rightleg", "leftleg"};

	public final ModelPart root;
	public final ModelPart[] parte = new ModelPart[OSSOS];
	/** Cubo de cada osso no espaço dele (pixels): minX, minY, minZ, maxX, maxY, maxZ. */
	public final float[][] cubo = new float[OSSOS][];

	public Esqueleto(ModelPart root, ModelPart cabeca, ModelPart tronco, ModelPart bracoD, ModelPart bracoE, ModelPart pernaD, ModelPart pernaE) {
		this.root = root;
		parte[CABECA] = cabeca; parte[TRONCO] = tronco; parte[BRACO_D] = bracoD; parte[BRACO_E] = bracoE;
		parte[PERNA_D] = pernaD; parte[PERNA_E] = pernaE;
		float[][] padrao = {{-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 12, 2}, {-3, -2, -2, 1, 10, 2}, {-1, -2, -2, 3, 10, 2},
				{-2, 0, -2, 2, 12, 2}, {-2, 0, -2, 2, 12, 2}};
		for (int i = 0; i < OSSOS; i++) cubo[i] = medir(parte[i], padrao[i]);
	}

	public static Esqueleto de(Corpo c) { return new Esqueleto(c.root, c.cabeca, c.tronco, c.bracoD, c.bracoE, c.pernaD, c.pernaE); }

	/** O cubo do próprio osso (sem as camadas de fora, como manga e calça). */
	private static float[] medir(ModelPart p, float[] padrao) {
		float[] m = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		p.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, caminho, i, c) -> {
			if (!caminho.isEmpty()) return;
			m[0] = Math.min(m[0], c.minX); m[1] = Math.min(m[1], c.minY); m[2] = Math.min(m[2], c.minZ);
			m[3] = Math.max(m[3], c.maxX); m[4] = Math.max(m[4], c.maxY); m[5] = Math.max(m[5], c.maxZ);
		});
		return m[0] == Float.MAX_VALUE ? padrao.clone() : m;
	}

	/**
	 * Pontos espalhados no cubo de cada osso (pixels, no espaço dele), encolhidos 'encolhe' de cada lado: os cantos
	 * a cada ~3 px ao longo do comprimento e uma linha no meio. É o que o resolvedor confere contra os blocos.
	 */
	public float[][] pontos(int osso, float encolhe) {
		float[] q = cubo[osso];
		float x0 = q[0] + encolhe, x1 = q[3] - encolhe, y0 = q[1] + encolhe, y1 = q[4] - encolhe, z0 = q[2] + encolhe, z1 = q[5] - encolhe;
		int ny = Math.max(2, Math.round((y1 - y0) / 3f) + 1);
		float[][] p = new float[ny * 5][];
		int k = 0;
		for (int j = 0; j < ny; j++) {
			float y = y0 + (y1 - y0) * j / (ny - 1);
			p[k++] = new float[]{x0, y, z0}; p[k++] = new float[]{x1, y, z0}; p[k++] = new float[]{x0, y, z1}; p[k++] = new float[]{x1, y, z1};
			p[k++] = new float[]{(x0 + x1) / 2, y, (z0 + z1) / 2};
		}
		return p;
	}

	/** Matriz do osso no espaço do modelo (pixels): root e depois o osso, como o desenho faz. */
	public Matrix4f matriz(int osso) {
		Matrix4f m = new Matrix4f().translate(root.x, root.y, root.z).rotateZYX(root.zRot, root.yRot, root.xRot)
				.scale(root.xScale, root.yScale, root.zScale);
		ModelPart p = parte[osso];
		return m.translate(p.x, p.y, p.z).rotateZYX(p.zRot, p.yRot, p.xRot).scale(p.xScale, p.yScale, p.zScale);
	}

	/** Ponto de um osso (pixels, no espaço dele) no mundo. */
	public Vec3 ponto(DadosMarcio d, int osso, float x, float y, float z) {
		Vector3f v = matriz(osso).transformPosition(new Vector3f(x, y, z));
		return Ik.mundo(d, v.x, v.y, v.z);
	}

	/** Direção de um osso (no espaço dele) no mundo, unitária. */
	public Vec3 direcao(DadosMarcio d, int osso, float x, float y, float z) {
		Vector3f v = matriz(osso).transformDirection(new Vector3f(x, y, z));
		return Ik.mundo(d, v.x, v.y, v.z).subtract(Ik.mundo(d, 0, 0, 0)).normalize();
	}

	/** A caixa do osso no mundo, encolhida 'encolhe' pixels de cada lado (encostar não é atravessar). */
	public Colisao.Caixa caixa(DadosMarcio d, int osso, float encolhe) {
		float[] q = cubo[osso];
		Matrix4f m = matriz(osso);
		Vector3f c = m.transformPosition(new Vector3f((q[0] + q[3]) / 2, (q[1] + q[4]) / 2, (q[2] + q[5]) / 2));
		Vec3 cw = Ik.mundo(d, c.x, c.y, c.z), zero = Ik.mundo(d, 0, 0, 0);
		Vec3[] eixo = new Vec3[3];
		double[] meia = new double[3];
		for (int k = 0; k < 3; k++) {
			Vector3f e = m.transformDirection(new Vector3f(k == 0 ? 1 : 0, k == 1 ? 1 : 0, k == 2 ? 1 : 0));
			float esc = e.length();
			Vec3 w = Ik.mundo(d, e.x, e.y, e.z).subtract(zero);
			eixo[k] = w.normalize();
			meia[k] = Math.max(0.01, ((q[k + 3] - q[k]) / 2f * esc - encolhe) / 16.0);
		}
		return new Colisao.Caixa(cw, eixo, meia);
	}
}

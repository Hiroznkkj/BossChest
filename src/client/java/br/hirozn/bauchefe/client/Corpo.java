package br.hirozn.bauchefe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * O corpo do NPC para escrever poses procedurais sem conta de matriz. Tudo que recebe um Vec3 é um ponto do
 * mundo de verdade (a borda do barco, a cabeça do Pinto, o fogo, a mão do jogador); o resto é no espaço do corpo
 * (pixels, y para baixo, frente = -z, esquerda dele = +x). 'w' é sempre o peso (0..1) para misturar com a pose
 * que já está lá, então várias camadas podem agir ao mesmo tempo sem saltos. Ver PROCEDURAL.md.
 */
public final class Corpo {
	public enum Lado { DIREITO, ESQUERDO }

	public final DadosMarcio d;
	public final ModelPart root, cabeca, tronco, bracoD, bracoE, pernaD, pernaE;

	public Corpo(DadosMarcio d, ModelPart root, ModelPart cabeca, ModelPart tronco, ModelPart bracoD, ModelPart bracoE,
	             ModelPart pernaD, ModelPart pernaE) {
		this.d = d; this.root = root; this.cabeca = cabeca; this.tronco = tronco;
		this.bracoD = bracoD; this.bracoE = bracoE; this.pernaD = pernaD; this.pernaE = pernaE;
	}

	public ModelPart braco(Lado l) { return l == Lado.DIREITO ? bracoD : bracoE; }

	// ---------- onde estão as coisas ----------

	/** Ponto do mundo no espaço do corpo (dentro do root, já com o giro e o deslocamento dele). */
	public Vector3f noCorpo(Vec3 mundo) { return Ik.noRoot(root, Ik.modelo(d, mundo)); }

	/** Ponto (x, y, z em pixels) de uma parte do corpo, no mundo (por exemplo a mão: braco(Lado), 1, 10, 0). */
	public Vec3 noMundo(ModelPart parte, float x, float y, float z) {
		Vector3f m = noModelo(parte, x, y, z);
		return Ik.mundo(d, m.x, m.y, m.z);
	}

	/** Ponto de uma parte no espaço do modelo (pixels). */
	public Vector3f noModelo(ModelPart parte, float x, float y, float z) {
		PoseStack ps = new PoseStack();
		root.translateAndRotate(ps);
		if (parte != root) parte.translateAndRotate(ps);
		Vector3f v = new Vector3f(x / 16f, y / 16f, z / 16f);
		ps.last().pose().transformPosition(v);
		return v.mul(16f);
	}

	/** Direção (no chão) de um ponto do mundo vista pelo corpo: {x, z} de comprimento 1 (frente = z negativo). */
	public float[] direcao(Vec3 mundo) {
		Vector3f f = noCorpo(mundo);
		float len = Mth.sqrt(f.x * f.x + f.z * f.z);
		return len < 1e-3f ? new float[]{0f, -1f} : new float[]{f.x / len, f.z / len};
	}

	/** Distância do corpo até o ponto, no chão (blocos). */
	public float distancia(Vec3 mundo) {
		return (float) Math.sqrt(Math.pow(mundo.x - d.mx, 2) + Math.pow(mundo.z - d.mz, 2));
	}

	// ---------- mexer ----------

	/** Mão até o ponto do mundo (o braço aponta para ele). Devolve quanto faltou para alcançar (pixels). */
	public float mao(Lado l, Vec3 mundo, float w) {
		if (mundo == null || w <= 0) return 0;
		ModelPart b = braco(l);
		Vector3f alvo = noCorpo(mundo), mira = new Vector3f(alvo);
		// a mão fica no meio do cubo do braço (1 px fora do eixo): com a mira cheia, mede onde ela parou e
		// corrige a mira pelo erro; depois aplica uma vez só, com o peso de verdade
		float x0 = b.xRot, y0 = b.yRot, z0 = b.zRot, cx = l == Lado.DIREITO ? -1f : 1f;
		for (int i = 0; i < 2; i++) {
			Ik.mirar(b, mira, Ik.BRACO, 1f);
			Vector3f m = Ik.noRoot(root, noModelo(b, cx, Ik.BRACO, 0f));
			mira.add(new Vector3f(alvo).sub(m));
		}
		b.xRot = x0; b.yRot = y0; b.zRot = z0;
		return Ik.mirar(b, mira, Ik.BRACO, w);
	}

	/** Mão até um ponto do próprio corpo (pixels): no peito, no bolso, atrás da cabeça... */
	public void maoNoCorpo(Lado l, float x, float y, float z, float w) {
		Ik.mirar(braco(l), new Vector3f(x, y, z), Ik.BRACO, w);
	}

	/** Mão até o ponto; se não alcança, o corpo se inclina para lá só o quanto falta (até 'max' radianos). */
	public void alcancar(Lado l, Vec3 mundo, float w, float max) {
		float falta = mao(l, mundo, w);
		if (falta <= 0.3f) return;
		float[] f = direcao(mundo);
		float ang = Mth.clamp(falta / pivo(), 0f, max) * w;
		inclinar(-ang * f[1], ang * f[0], 1f);
		mao(l, mundo, w);
	}

	/** Olha para o ponto do mundo (com limite de pescoço). */
	public void olhar(Vec3 mundo, float w) { if (mundo != null && w > 0) Ik.olhar(cabeca, noCorpo(mundo), w); }

	public void olharNoCorpo(float x, float y, float z, float w) { if (w > 0) Ik.olhar(cabeca, new Vector3f(x, y, z), w); }

	/**
	 * Inclina o corpo todo: 'frente' positivo para a frente, 'lado' positivo para a esquerda (radianos), girando
	 * em volta dos pés em pé ou do quadril sentado, que ficam no lugar.
	 */
	public void inclinar(float frente, float lado, float w) {
		Ik.girarNosPes(root, frente * w, lado * w, pivo());
	}

	/** Recua para longe do ponto (pelo chão), com 'forca' radianos de inclinação. */
	public void recuarDe(Vec3 mundo, float forca, float w) {
		if (mundo == null || w <= 0) return;
		float[] f = direcao(mundo);
		inclinar(forca * f[1], -forca * f[0], w);
	}

	/** Distância do pivô do root até onde o corpo apoia (pés em pé, quadril sentado). */
	public float pivo() { return d.sentado ? 12f : 24f; }

	/** Envelope de uma reação: sobe em 'entra' ticks, fica até 'sai' e some até 'fim'. */
	public static float envelope(float t, float entra, float sai, float fim) {
		if (t < 0 || t >= fim) return 0f;
		return br.hirozn.bauchefe.AnimPesca.inOutSine(Math.min(t / entra, t < sai ? 1f : (fim - t) / (fim - sai)));
	}
}

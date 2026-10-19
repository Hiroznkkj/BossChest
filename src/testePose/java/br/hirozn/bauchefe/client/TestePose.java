package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Lugares;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Testes da pose procedural, sem abrir o jogo: ./gradlew testePose. Monta o modelo de jogador de verdade, roda a
 * PoseMarcio e mede no mundo: mão chegando nos alvos, corpo atravessando o barco, pés no chão, braços e pernas
 * dentro de blocos e "teleporte" (a cabeça andando rápido demais num quadro). Falha se algo passar do limite.
 * Para um caso novo: monte o DadosMarcio, chame aplicar() e confira com ok(...).
 */
public final class TestePose {
	static ModelPart root, head, body, dir, esq, pd, pe;
	static final List<String> falhas = new ArrayList<>();
	static int casos;

	public static void main(String[] a) {
		root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
		head = root.getChild("head"); body = root.getChild("body"); dir = root.getChild("right_arm"); esq = root.getChild("left_arm");
		pd = root.getChild("right_leg"); pe = root.getChild("left_leg");
		Ik.medir(dir, pd);
		carinho();
		borda();
		embarque();
		assentos();
		penetracao();
		teleporte();
		System.out.printf("%n%d verificações, %d falhas%n", casos, falhas.size());
		for (String f : falhas) System.out.println("  FALHOU: " + f);
		if (!falhas.isEmpty()) System.exit(1);
	}

	static void ok(boolean cond, String o_que, double valor, double limite) {
		casos++;
		System.out.printf("  %s %-60s %7.2f (limite %.2f)%n", cond ? "ok " : "XX ", o_que, valor, limite);
		if (!cond) falhas.add(String.format("%s: %.2f (limite %.2f)", o_que, valor, limite));
	}

	static void reset() { for (ModelPart p : root.getAllParts()) p.resetPose(); Ik.solido = null; }

	static DadosMarcio base() { DadosMarcio d = new DadosMarcio(); d.mx = 0; d.my = 0; d.mz = 0; d.myaw = 0; return d; }

	static void aplicar(DadosMarcio d, float t) { PoseMarcio.aplicar(d, t, root, head, body, dir, esq, pd, pe); }

	static Corpo corpo(DadosMarcio d) { return new Corpo(d, root, head, body, dir, esq, pd, pe); }

	/** Mão esquerda até a cabeça do Pinto, com ele em lugares diferentes. Limite: 3 px (o afago sobe e desce ~1 px). */
	static void carinho() {
		System.out.println("Carinho: mão até a cabeça do Pinto (px)");
		float[][] pintos = {{0, 1.12f, -0.83f}, {0.15f, 1.12f, -0.83f}, {-0.15f, 1.12f, -0.83f}, {0, 1.18f, -0.73f}, {0, 1.06f, -0.93f}};
		for (float[] p : pintos) for (int v = 0; v < 3; v++) {
			Vec3 pinto = new Vec3(p[0], p[1], p[2]);
			float soma = 0;
			for (int t = 20; t <= 40; t += 4) {
				reset(); DadosMarcio d = base(); d.sentado = true; d.anim = AnimMarcio.CARINHO; d.animTempo = t; d.animVar = v;
				d.pe = v == 2 ? 0.35f : 0f; d.agora = t; d.pinto = pinto;
				aplicar(d, t);
				Vec3 mao = corpo(d).noMundo(esq, 1, Ik.BRACO, 0);
				soma += (float) mao.distanceTo(pinto) * 16f;
			}
			ok(soma / 6 < 3.2f, String.format("Pinto em (%.2f %.2f %.2f), jeito %d", p[0], p[1], p[2], v), soma / 6, 3.2);
		}
	}

	/** Mão esquerda na borda do barco ao levantar, com a borda em lugares diferentes. Limite: 3 px. */
	static void borda() {
		System.out.println("Mão na borda ao levantar (px)");
		float[][] bordas = {{0.575f, 0.375f, 0.10f}, {0.575f, 0.45f, 0.10f}, {0.575f, 0.30f, 0.10f}, {0.52f, 0.375f, 0.25f}, {0.62f, 0.375f, -0.05f}};
		for (float[] b : bordas) {
			Vec3 borda = new Vec3(b[0], b[1], b[2]);
			reset(); DadosMarcio d = base(); d.sentado = true; d.pe = 0.33f; d.bordaEsq = borda;
			aplicar(d, 0);
			double dist = corpo(d).noMundo(esq, 1, Ik.BRACO, 0).distanceTo(borda) * 16;
			ok(dist < 3, String.format("borda em (%.2f %.2f %.2f)", b[0], b[1], b[2]), dist, 3);
		}
	}

	/** Subir no barco: o corpo nunca passa por dentro do casco antes de cruzar a borda, e os pés passam acima dela. */
	static void embarque() {
		System.out.println("Embarque: amostras dentro do casco antes de entrar; altura dos pés na borda");
		Vec3[] partidas = {new Vec3(0.2, 0.45, -1.9), new Vec3(-0.3, 0.45, -1.7), new Vec3(1.6, 0.45, 0.2), new Vec3(-1.5, 0.45, 1.4), new Vec3(0, 0.45, 2.1)};
		Vec3 lugar = new Vec3(0, 0.1875, 0.15);
		for (Vec3 s : partidas) {
			int dentro = 0;
			double cruza = -9;
			for (int k = 0; k <= 40; k++) {
				float u = k / 40f;
				int fase = u < 0.5f ? Trajeto.ANDA : u < 0.8f ? Trajeto.PASSA : Trajeto.ACOMODA;
				float v = u < 0.5f ? u / 0.5f : u < 0.8f ? (u - 0.5f) / 0.3f : (u - 0.8f) / 0.2f;
				Vec3 p = Trajeto.noBarco(MarcioCliente.SUBIR_TERRA, fase, v, s, lugar);
				boolean noCasco = Math.abs(p.x) < Trajeto.LADO - 0.05 && Math.abs(p.z) < Trajeto.PROA - 0.05 && p.y < Trajeto.BORDA;
				if (noCasco && (fase == Trajeto.ANDA || fase == Trajeto.PASSA && v < 0.5f)) dentro++;
				if (fase == Trajeto.PASSA && Math.abs(Math.abs(p.x) - Trajeto.LADO) < 0.04) cruza = Math.max(cruza, p.y);
			}
			ok(dentro == 0, String.format("saindo de (%.1f, %.1f): amostras dentro do casco", s.x, s.z), dentro, 0);
			ok(cruza > Trajeto.BORDA, String.format("saindo de (%.1f, %.1f): pés na borda (acima de %.2f)", s.x, s.z, Trajeto.BORDA), cruza, Trajeto.BORDA);
		}
	}

	/** Sentado em assentos de alturas diferentes: pés no chão (até 1 px) quando a perna alcança. */
	static void assentos() {
		System.out.println("Assentos: pés acima do chão (px)");
		for (float alt : new float[]{0.3f, 0.5f, 0.7f}) {
			reset(); DadosMarcio d = base(); d.sentado = true; d.pe = 0f; d.assento = Lugares.CADEIRA; d.assentoAltura = alt;
			aplicar(d, 0);
			double pes = corpo(d).noMundo(pd, 0, 12, 0).y + alt;
			ok(Math.abs(pes) <= 1.0 / 16 * 1.5, String.format("assento %.1f acima do chão", alt), pes * 16, 1.5);
		}
	}

	/** Braços e pernas não ficam dentro de blocos (Corpo.livrar), sentado numa laje com mundos diferentes em volta. */
	static void penetracao() {
		System.out.println("Membros dentro de blocos depois do livrar (pontos)");
		String[] nomes = {"doca continua na frente", "beirada, chão 1 abaixo", "beirada, chão 1,5 abaixo", "bloco cheio na frente dos pés"};
		List<List<AABB>> mundos = List.of(
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(0, 0, 1, 1, 0.5, 2), new AABB(-5, -1, -5, 5, 0, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(-5, -1.5, -5, 5, -0.5, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(-5, -2, -5, 5, -1, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(0, -1, 1, 1, 0, 2), new AABB(-5, -1, -5, 5, -1, 5)));
		float[] alturas = {0f, 1.0f, 1.5f, 0.5f};
		for (int i = 0; i < mundos.size(); i++) {
			List<AABB> m = mundos.get(i);
			reset();
			DadosMarcio d = base(); d.mx = 0.5; d.my = 0.5; d.mz = 0.5; d.sentado = true; d.pe = 0f;
			d.assento = Lugares.CADEIRA; d.assentoAltura = alturas[i];
			aplicar(d, 0);
			Ik.Solido s = (x, y, z) -> { for (AABB b : m) if (b.contains(x, y, z) && y < b.maxY - 0.01 && y > b.minY + 0.01) return true; return false; };
			Ik.solido = s;
			Corpo c = corpo(d);
			c.livrar();
			int dentro = 0;
			for (ModelPart membro : new ModelPart[]{pd, pe, dir, esq}) for (float y : new float[]{3, 7, 10}) {
				Vec3 w = c.noMundo(membro, membro == dir ? -1 : membro == esq ? 1 : 0, y, 0);
				if (s.em(w.x, w.y, w.z)) dentro++;
			}
			ok(dentro == 0, nomes[i], dentro, 0);
		}
		Ik.solido = null;
	}

	/** Cabeça no mundo, refazendo o renderer (giro do corpo, deitar para nadar, modelo). */
	static Vec3 cabeca(Vec3 corpo, float yaw, float nado, float linha) {
		PoseStack ps = new PoseStack();
		ps.translate(corpo.x, corpo.y, corpo.z);
		ps.rotateDegrees(Axis.YP, 180f - yaw);
		if (nado > 0) { ps.translate(0, linha, 0); ps.rotateDegrees(Axis.XP, -85f * nado); ps.translate(0, -linha, 0); }
		ps.scale(-1, -1, 1);
		ps.translate(0, -1.501f, 0);
		root.translateAndRotate(ps);
		head.translateAndRotate(ps);
		Vector3f v = new Vector3f(0, -4f / 16f, 0);
		ps.last().pose().transformPosition(v);
		return new Vec3(v.x, v.y, v.z);
	}

	/** Nenhuma transição move a cabeça mais que 0,35 bloco por tick (seria um "teleporte"). */
	static void teleporte() {
		System.out.println("Teleporte: maior velocidade da cabeça (blocos por tick)");
		// subir da água pelo lado direito do barco, como o MarcioCliente faz com o nado
		Vec3 de = new Vec3(-1.6, -0.55, 0.2), lugar = new Vec3(0, 0.1875, 0.15);
		float anda = (float) Mth.clamp((1.6 - 0.95) / 0.07, 4, 50), passa = 20, acomoda = 12;
		float nado = 1f, yaw = -90f;
		Vec3 ant = null;
		double max = 0;
		for (float t = 0; t <= anda + passa + acomoda + 10; t += 0.5f) {
			int fase; float v;
			if (t < anda) { fase = Trajeto.ANDA; v = t / anda; }
			else if (t < anda + passa) { fase = Trajeto.PASSA; v = (t - anda) / passa; }
			else { fase = Trajeto.ACOMODA; v = Math.min(1f, (t - anda - passa) / acomoda); }
			Vec3 c = Trajeto.noBarco(MarcioCliente.SUBIR_AGUA, fase, v, de, lugar);
			float alvo = fase == Trajeto.ANDA ? 1f : fase == Trajeto.PASSA ? Math.max(0f, 1f - v) : 0f;
			nado = alvo > nado ? Math.min(alvo, nado + 0.5f / 8f) : Math.max(alvo, nado - 0.5f / 16f);
			yaw += Mth.clamp(Mth.wrapDegrees((fase == Trajeto.ACOMODA ? 0f : -90f) - yaw), -7.5f, 7.5f);
			reset();
			DadosMarcio d = base(); d.sentado = true; d.trans = MarcioCliente.SUBIR_AGUA; d.transFase = fase; d.transV = v;
			d.pe = fase == Trajeto.ACOMODA ? 1f - AnimPesca.inOutSine(v) : 1f; d.nado = AnimPesca.inOutSine(nado);
			aplicar(d, t);
			Vec3 h = cabeca(c, yaw, d.nado, 0.38f);
			if (ant != null) max = Math.max(max, h.distanceTo(ant) / 0.5);
			ant = h;
		}
		ok(max < 0.35, "subir da água", max, 0.35);
		for (float alto : new float[]{0.5f, 1.0f}) {
			Trajeto tr = Trajeto.sentar(0, new Vec3(0.5, 0, 3), new Vec3(0.5, alto, 0.5), 0f, Lugares.CADEIRA);
			ant = null; max = 0;
			for (float t = 0; t <= tr.duracao(); t += 0.5f) {
				Vec3 c = tr.ponto(t, new Vec3(0.5, alto, 0.5), null, 0);
				reset();
				DadosMarcio d = base(); d.sentado = true; d.assento = Lugares.CADEIRA; d.assentoAltura = alto; d.pe = tr.pe(t);
				float[] f = tr.fase(t); d.trans = MarcioCliente.SENTAR; d.transFase = (int) f[0]; d.transV = f[1];
				aplicar(d, t);
				Vec3 h = cabeca(c, 0f, 0, 0);
				if (ant != null) max = Math.max(max, h.distanceTo(ant) / 0.5);
				ant = h;
			}
			ok(max < 0.35, String.format("sentar num assento %.1f acima", alto), max, 0.35);
		}
	}
}

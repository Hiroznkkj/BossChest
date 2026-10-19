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
		resolvedor();
		importar();
		autoColisao();
		System.out.printf("%n%d verificações, %d falhas%n", casos, falhas.size());
		for (String f : falhas) System.out.println("  FALHOU: " + f);
		if (!falhas.isEmpty()) System.exit(1);
	}

	static void ok(boolean cond, String o_que, double valor, double limite) {
		casos++;
		System.out.printf("  %s %-60s %7.2f (limite %.2f)%n", cond ? "ok " : "XX ", o_que, valor, limite);
		if (!cond) falhas.add(String.format("%s: %.2f (limite %.2f)", o_que, valor, limite));
	}

	static void reset() { for (ModelPart p : root.getAllParts()) p.resetPose(); Colisao.mundo = null; Resolver.esquecer(0); }

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

	/** Nenhuma parte dentro de blocos depois do Resolver, sentado numa laje com mundos diferentes em volta. */
	static void penetracao() {
		System.out.println("Corpo dentro de blocos depois do resolvedor (px)");
		String[] nomes = {"doca continua na frente", "beirada, chão 1 abaixo", "beirada, chão 1,5 abaixo", "bloco cheio na frente dos pés"};
		List<List<AABB>> mundos = List.of(
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(0, 0, 1, 1, 0.5, 2), new AABB(-5, -1, -5, 5, 0, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(-5, -1.5, -5, 5, -0.5, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(-5, -2, -5, 5, -1, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(0, -1, 1, 1, 0, 2), new AABB(-5, -1, -5, 5, -1, 5)));
		float[] alturas = {0f, 1.0f, 1.5f, 0.5f};
		for (int i = 0; i < mundos.size(); i++) {
			reset();
			DadosMarcio d = base(); d.mx = 0.5; d.my = 0.5; d.mz = 0.5; d.sentado = true; d.pe = 0f;
			d.assento = Lugares.CADEIRA; d.assentoAltura = alturas[i]; d.assentoBorda = 0.5f;
			aplicar(d, 0);
			double antes = dentro(d, mundos.get(i));
			resolver(d, mundos.get(i), 12, 0);
			ok(dentro(d, mundos.get(i)) < 0.3, nomes[i] + String.format(" (antes %.1f)", antes), dentro(d, mundos.get(i)), 0.3);
		}
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

	static Esqueleto esq() { return new Esqueleto(root, head, body, dir, esq, pd, pe); }

	/** Quanto o corpo todo está dentro dos blocos (px), com a mesma folga do resolvedor. */
	static double dentro(DadosMarcio d, List<AABB> m) {
		Esqueleto e = esq();
		double s = 0;
		for (int o = 0; o < Esqueleto.OSSOS; o++) s += Colisao.entra(e.caixa(d, o, 0.5f), m) * 16;
		return s;
	}

	/** Roda o resolvedor 'quadros' vezes (como no jogo, um por quadro), com o mundo 'm'. */
	static void resolver(DadosMarcio d, List<AABB> m, int quadros, float t) {
		Colisao.mundo = Colisao.de(m);
		float[] guarda = new float[0];
		for (int i = 0; i < quadros; i++) {
			for (ModelPart p : root.getAllParts()) p.resetPose();
			d.agora = t + i;
			aplicar(d, t);
			Resolver.resolver(esq(), d);
		}
	}

	/** O resolvedor de contatos: costas na parede, cabeça deitada virando, sentado sem atravessar, em pé no degrau. */
	static void resolvedor() {
		System.out.println("Resolvedor: apoios e colisão por parte do corpo (px)");
		List<AABB> chao = List.of(new AABB(-5, -1, -5, 5, 0, 5));
		// encostado: parede atrás (ele olha para +z, as costas ficam para -z), a 0,32 do meio dele, como o servidor faz
		for (double parede : new double[]{-0.32, -0.40, -0.26}) {
			reset();
			List<AABB> m = List.of(chao.get(0), new AABB(-5, 0, -3, 5, 3, parede));
			DadosMarcio d = base(); d.assento = Lugares.ENCOSTO; d.pe = 1f;
			aplicar(d, 0);
			double antes = Math.abs(esq().ponto(d, Esqueleto.TRONCO, 0, 6, 2).z - parede) * 16;
			resolver(d, m, 12, 0);
			Esqueleto e = esq();
			double depois = Math.abs(e.ponto(d, Esqueleto.TRONCO, 0, 6, 2).z - parede) * 16;
			double pes = Math.max(Math.abs(e.ponto(d, Esqueleto.PERNA_D, 0, 12, 0).y), Math.abs(e.ponto(d, Esqueleto.PERNA_E, 0, 12, 0).y)) * 16;
			ok(depois < 0.8, String.format("encostado, parede a %.2f: costas até a parede (antes %.1f)", -parede, antes), depois, 0.8);
			ok(pes < 1.0, String.format("encostado, parede a %.2f: pés no chão", -parede), pes, 1.0);
			ok(dentro(d, m) < 0.3, String.format("encostado, parede a %.2f: dentro de blocos", -parede), dentro(d, m), 0.3);
		}
		// deitado no chão virando a cabeça para os lados: a cabeça não entra no chão
		for (float vira : new float[]{0f, 0.8f, 1.3f}) {
			reset();
			DadosMarcio d = base(); d.assento = Lugares.DEITADO; d.pe = 0f; d.sentado = true;
			aplicar(d, 0);
			head.yRot += vira;
			Esqueleto e = esq();
			double antes = Colisao.entra(e.caixa(d, Esqueleto.CABECA, 0.5f), chao) * 16;
			Colisao.mundo = Colisao.de(chao);
			for (int i = 0; i < 30; i++) { // ~meio segundo de jogo
				for (ModelPart p : root.getAllParts()) p.resetPose();
				d.agora = i;
				aplicar(d, 0);
				head.yRot += vira;
				Resolver.resolver(esq(), d);
			}
			double cab = Colisao.entra(esq().caixa(d, Esqueleto.CABECA, 0.5f), chao) * 16;
			double costas = Math.abs(esq().ponto(d, Esqueleto.TRONCO, 0, 6, 2).y) * 16;
			ok(cab < 0.3, String.format("deitado, cabeça virada %.1f rad: cabeça dentro do chão (antes %.1f)", vira, antes), cab, 0.3);
			// com a cabeça bem virada a quina dela (é um cubo) precisa de espaço: as costas sobem um pouco
			double lim = vira > 1f ? 1.7 : 1.0;
			ok(costas < lim, String.format("deitado, cabeça virada %.1f rad: costas no chão", vira), costas, lim);
		}
		// sentado em lajes com o mundo em volta: nenhuma parte dentro de blocos
		String[] nomes = {"laje com bloco cheio na frente dos pés", "laje com parede atrás", "laje entre duas paredes"};
		List<List<AABB>> mundos = List.of(
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(0, 0, 1, 1, 1, 2), new AABB(-5, -1, -5, 5, 0, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(0, 0, -1, 1, 2, 0), new AABB(-5, -1, -5, 5, 0, 5)),
				List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(-1, 0, 0, 0, 2, 1), new AABB(1, 0, 0, 2, 2, 1), new AABB(-5, -1, -5, 5, 0, 5)));
		for (int i = 0; i < mundos.size(); i++) {
			reset();
			DadosMarcio d = base(); d.mx = 0.5; d.my = 0.5; d.mz = 0.5; d.sentado = true; d.pe = 0f;
			d.assento = Lugares.CADEIRA; d.assentoAltura = 0.5f; d.assentoBorda = 0.5f;
			aplicar(d, 0);
			double antes = dentro(d, mundos.get(i));
			resolver(d, mundos.get(i), 12, 0);
			ok(dentro(d, mundos.get(i)) < 0.3, nomes[i] + String.format(" (antes %.1f)", antes), dentro(d, mundos.get(i)), 0.3);
		}
		// em pé com o braço balançando perto de uma parede
		reset();
		List<AABB> m = List.of(chao.get(0), new AABB(0.2, 0, -2, 2, 3, 2));
		DadosMarcio d = base();
		aplicar(d, 0);
		esq.zRot -= 0.5f; // braço esquerdo aberto para o lado da parede (+x)
		double antes = dentro(d, m);
		Colisao.mundo = Colisao.de(m);
		for (int i = 0; i < 10; i++) {
			for (ModelPart p : root.getAllParts()) p.resetPose();
			d.agora = i;
			aplicar(d, 0);
			esq.zRot -= 0.5f;
			Resolver.resolver(esq(), d);
		}
		ok(dentro(d, m) < 0.3, String.format("em pé, braço aberto contra a parede (antes %.1f)", antes), dentro(d, m), 0.3);
		// tempo: um quadro do resolvedor não pode pesar no jogo
		reset();
		DadosMarcio t = base(); t.assento = Lugares.ENCOSTO; t.pe = 1f;
		List<AABB> mt = List.of(chao.get(0), new AABB(-5, 0, -3, 5, 3, -0.32));
		long ini = System.nanoTime();
		resolver(t, mt, 200, 0);
		double ms = (System.nanoTime() - ini) / 1e6 / 200;
		ok(ms < 1.5, "tempo por quadro do resolvedor (ms)", ms, 1.5);
		Colisao.mundo = null;
	}

	/** Importar animações: Emotecraft e o formato próprio viram a mesma animação do esqueleto. */
	static void importar() {
		System.out.println("Importar animações (graus)");
		String emote = """
				{"name":"aceno","emote":{"beginTick":0,"endTick":20,"stopTick":24,"isLoop":"false","degrees":true,"moves":[
				 {"tick":10,"easing":"LINEAR","rightArm":{"pitch":-160,"roll":20}},
				 {"tick":20,"easing":"EASEINOUTQUAD","rightArm":{"pitch":-160,"roll":40},"head":{"yaw":15}}]}}""";
		AnimCustom.Anim a = Importar.emotecraft("aceno", com.google.gson.JsonParser.parseString(emote).getAsJsonObject());
		float[] v = new float[3];
		a.ossos().get("rightarm").rot().valor(5 / 20f, v);
		ok(Math.abs(v[0] + 80) < 1, "Emotecraft: braço no meio do caminho (tique 5)", v[0], -80);
		a.ossos().get("rightarm").rot().valor(15 / 20f, v);
		ok(Math.abs(v[2] - 30) < 1, "Emotecraft: easing in-out no meio (tique 15)", v[2], 30);
		a.ossos().get("rightarm").rot().valor(24 / 20f, v);
		ok(Math.abs(v[0]) < 1, "Emotecraft: volta para a pose parada no stopTick", v[0], 0);
		String proprio = """
				{"nome":"teste","duracao":1,"suave":false,"apoios":{"pes":1},"quadros":[
				 {"t":0,"bracoD":[0,0,0]},{"t":1,"bracoD":[-90,0,10],"raizPos":[0,-2,0]}]}""";
		AnimCustom.Anim b = Importar.proprio("x", com.google.gson.JsonParser.parseString(proprio).getAsJsonObject());
		b.ossos().get("rightarm").rot().valor(0.5f, v);
		ok(Math.abs(v[0] + 45) < 0.5, "formato próprio: meio do caminho", v[0], -45);
		ok(b.apoios() != null && b.apoios().get("pes") == 1f, "formato próprio: apoios lidos", b.apoios() == null ? 0 : b.apoios().size(), 1);
	}

	/** Acenar, coçar a cabeça e as outras especiais: o braço não pode ficar dentro da cabeça depois do resolvedor. */
	static void autoColisao() {
		System.out.println("Auto-colisão: braço dentro da cabeça (px), pior quadro antes e depois do resolvedor");
		List<AABB> chao = List.of(new AABB(-5, -1, -5, 5, 0, 5));
		String[] nomes = {"", "espreguiçar", "coçar", "acenar", "comemorar", "carinho"};
		for (int sent = 0; sent < 2; sent++) for (int anim = 1; anim < nomes.length; anim++) for (int jeito = 0; jeito < 3; jeito++) {
			reset();
			Colisao.mundo = Colisao.de(chao);
			DadosMarcio d = base();
			d.pe = sent == 1 ? 0f : 1f; d.sentado = sent == 1; d.anim = anim; d.animVar = jeito;
			double pior0 = 0, pior1 = 0;
			for (int q = 0; q <= 120; q++) {
				for (ModelPart p : root.getAllParts()) p.resetPose();
				d.agora = q; d.animTempo = q;
				aplicar(d, q);
				pior0 = Math.max(pior0, Resolver.autoDentro(esq()));
				Resolver.resolver(esq(), d);
				pior1 = Math.max(pior1, Resolver.autoDentro(esq()));
			}
			ok(pior1 < 1.0, String.format("%s %s (jeito %d): antes %.1f", nomes[anim], sent == 1 ? "sentado" : "em pé", jeito, pior0), pior1, 1.0);
		}
	}
}

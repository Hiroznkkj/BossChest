package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Asas;
import br.hirozn.bauchefe.Boia;
import br.hirozn.bauchefe.Falas;
import br.hirozn.bauchefe.Pescador;
import br.hirozn.bauchefe.net.Pacotes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import br.hirozn.bauchefe.BauChefe;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Estado do Márcio no cliente: falas e animações recebidas do servidor, piscadas e as transições entre ações. */
public final class MarcioCliente {
	public static final int DUR_FALA = 80;
	/** Transições: nenhuma, pulo qualquer (teste, cama), subir no barco da terra, subir da água, descer do barco. */
	public static final int SEM = 0, PULO = 1, SUBIR_TERRA = 2, SUBIR_AGUA = 3, DESCER = 4;

	private record Fala(String[] palavras, float[] tempos, long inicio, int duracao, int humor) {}
	/** Tempo de cada palavra nos áudios (assets/bauchefe/marcio_falas.json, gerado a partir da transcrição). */
	private record Tempos(String[] palavras, float[] tempos, float duracao) {}
	private static Map<String, Tempos> tempos;
	private record Anim(int id, long inicio, int variacao) {}
	private record Teste(AnimCustom.Anim anim, long inicio) {}

	private static final Int2ObjectMap<Fala> falas = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<Anim> anims = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<Teste> testes = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<float[]> nado = new Int2ObjectOpenHashMap<>(); // {peso, último tempo, altura da água}
	private static final Int2ObjectMap<float[]> piscadas = new Int2ObjectOpenHashMap<>(); // {próxima piscada}
	private static final net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create();
	// {x,y,z,t do quadro anterior, desvio x,y,z, início, duração, tipo}
	private static final Int2ObjectMap<double[]> saltos = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<float[]> assento = new Int2ObjectOpenHashMap<>(); // {1 no barco, tempo da troca, última vez na água}
	private static final Int2ObjectMap<MarcioSom[]> sons = new Int2ObjectOpenHashMap<>();

	private MarcioCliente() {}

	private static Object mundo;
	private static long base;

	/**
	 * Ticks desde que o cliente entrou neste mundo. Não usa o tempo total do mundo direto: num mundo antigo
	 * ele passa de dezenas de milhões e, somado ao partialTick num float, perde a fração (a animação andava
	 * aos saltos, uns 5 quadros por segundo).
	 */
	private static long agora() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 0;
		long g = mc.level.getGameTime();
		if (mc.level != mundo || g < base) { mundo = mc.level; base = g; }
		return g - base;
	}

	public static void fala(Pacotes.Fala p) {
		Tempos t = p.som().isEmpty() ? null : tempos().get(p.som());
		if (t != null) falas.put(p.villager(), new Fala(t.palavras(), t.tempos(), agora(), Math.max(DUR_FALA, (int) (t.duracao() * 20) + 30), p.humor()));
		else falas.put(p.villager(), new Fala(p.texto().split(" "), null, agora(), DUR_FALA, p.humor()));
		Minecraft mc = Minecraft.getInstance();
		Entity v = mc.level == null ? null : mc.level.getEntity(p.villager());
		// fala nova corta a anterior (senão os áudios se sobrepõem, por ex. ao devolver o barco)
		MarcioSom[] antes = sons.remove(p.villager());
		if (antes != null) for (MarcioSom m : antes) mc.getSoundManager().stop(m);
		if (v != null && !p.som().isEmpty()) sons.put(p.villager(), MarcioSom.tocar(v, p.som()));
	}

	private static Map<String, Tempos> tempos() {
		if (tempos != null) return tempos;
		tempos = new HashMap<>();
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(BauChefe.id("marcio_falas.json"));
			if (res.isEmpty()) return tempos;
			try (var leitor = res.get().openAsReader()) {
				for (var e : JsonParser.parseReader(leitor).getAsJsonObject().entrySet()) {
					JsonObject o = e.getValue().getAsJsonObject();
					JsonArray ps = o.getAsJsonArray("palavras");
					String[] pal = new String[ps.size()];
					float[] tem = new float[ps.size()];
					for (int i = 0; i < ps.size(); i++) {
						pal[i] = ps.get(i).getAsJsonArray().get(0).getAsString();
						tem[i] = ps.get(i).getAsJsonArray().get(1).getAsFloat();
					}
					tempos.put(e.getKey(), new Tempos(pal, tem, o.get("duracao").getAsFloat()));
				}
			}
		} catch (Exception e) {
			BauChefe.LOG.warn("[Baú Chefe] Não deu para ler os tempos das falas do Márcio", e);
		}
		return tempos;
	}

	public static void anim(Pacotes.Anim p) {
		long t = agora();
		anims.put(p.villager(), new Anim(p.anim(), t, rnd.nextInt(3))); // cada vez um jeito (o carinho tem 3)
		long mundoT = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime(); // Asas usa o tempo do mundo
		if (p.anim() == AnimMarcio.CARINHO) { // o Pinto bate as asas no meio do carinho
			Minecraft mc = Minecraft.getInstance();
			Entity v = mc.level == null ? null : mc.level.getEntity(p.villager());
			if (v != null && v.getVehicle() != null)
				for (Entity e : v.getVehicle().getPassengers())
					if (Pescador.ehPinto(e)) { Asas.bater(e.getId(), mundoT + 40); Asas.carinho(e.getId(), mundoT); }
		}
	}

	/** /marcioanim tocar: toca a animação customizada no Márcio (só neste PC). */
	public static void tocar(int villager, AnimCustom.Anim a) {
		testes.put(villager, new Teste(a, agora()));
	}

	/** Preenche os dados do render state do pescador. */
	public static void extrair(Villager v, LivingEntityRenderState st, float partial, DadosMarcio d) {
		int id = v.getId();
		float t = agora() + partial;
		d.pescador = true;
		d.id = id;
		d.agora = t;
		d.rFase = AnimMarcio.fase(id);

		Boia b = Boia.doDono(id);
		d.fase = b == null ? AnimPesca.NADA : b.faseCliente();
		d.tempo = b == null ? 0 : b.tempoFase(partial);
		d.lutou = b != null && b.lutou();

		Anim a = anims.get(id);
		if (a != null && t - a.inicio() > AnimMarcio.duracao(a.id())) { anims.remove(id); a = null; }
		d.anim = a == null ? AnimMarcio.NADA : a.id();
		d.animTempo = a == null ? 0 : t - a.inicio();
		d.animVar = a == null ? 0 : a.variacao();
		Teste te = testes.get(id);
		if (te != null && t - te.inicio() > te.anim().duracao() * 20f) { testes.remove(id); te = null; }
		d.teste = te == null ? null : te.anim();
		d.testeTempo = te == null ? 0 : t - te.inicio();

		transicao(v, st, t, partial, d);

		Fala f = falas.get(id);
		if (f != null && t - f.inicio() > f.duracao()) { falas.remove(id); f = null; }
		d.fala = f == null ? null : f.palavras();
		d.humor = f == null ? Falas.Humor.NEUTRO : f.humor();
		d.falaAlpha = 0;
		if (f != null) {
			float e = t - f.inicio();
			d.falaTempos = f.tempos();
			d.falaSeg = e / 20f;
			d.falaAlpha = Mth.clamp(Math.min(e / 4f, (f.duracao() - e) / 10f), 0f, 1f); // aparece rápido, some devagar
			// batida da fala: um pulso a cada palavra dita (os gestos marcam o ritmo), ou um vaivém sem áudio
			d.falaPulso = 0.5f + 0.5f * Mth.sin(e * 0.45f);
			if (f.tempos() != null) {
				float ult = -1;
				for (float w : f.tempos()) if (w <= d.falaSeg) ult = w;
				d.falaPulso = ult < 0 ? 0 : (float) Math.exp(-(d.falaSeg - ult) * 6);
			}
		}

		// olhos: vão antes da cabeça para onde ele vai olhar, com mexidinhas; piscam a cada 3 a 6 s
		float rf = d.rFase, age = st.ageInTicks;
		float sac = Mth.clamp(Mth.sin(rf + age / 27f) + Mth.sin(rf + age / 16f), -0.15f, 0.15f);
		d.olhoX = Mth.clamp(-st.yRot / 40f, -1f, 1f) * 0.5f + sac;
		d.olhoY = Mth.clamp(st.xRot / 45f, -1f, 1f) * 0.3f;
		float[] p = piscadas.computeIfAbsent(id, k -> new float[]{t + 20 + rnd.nextInt(60)});
		float up = t - p[0];
		if (up >= 3) { p[0] = t + (rnd.nextFloat() < 0.2f ? 6 : 60 + rnd.nextInt(60)); up = -1; }
		float pisca = up < 0 ? 0 : up < 1 ? up : 1 - (up - 1) / 2f; // fecha em 1 tick, abre em 2
		float aperta = d.fase == AnimPesca.LUTA ? 0.45f * AnimPesca.luta(d.tempo) : 0f; // força na briga com o peixe
		// olhos meio fechados conforme o jeito de falar (triste, com sono, bêbado, bravo)
		float humor = switch (d.humor) {
			case Falas.Humor.TRISTE -> 0.35f;
			case Falas.Humor.SONO -> 0.55f;
			case Falas.Humor.BEBADO -> 0.4f;
			case Falas.Humor.BRAVO -> 0.25f;
			default -> 0f;
		};
		d.palpebra = Math.max(Math.max(pisca, aperta), humor * d.falaAlpha);
		d.distancia = (float) Math.sqrt(st.distanceToCameraSq);

		// nado: entra e sai aos poucos (~0,4 s), calculado pelo tempo que passou desde o último quadro
		float[] n = nado.computeIfAbsent(id, k -> new float[]{0f, t, 0.5f});
		float alvo = v.isInWater() && !v.isPassenger() ? 1f : 0f, dt = Mth.clamp(t - n[1], 0f, 5f);
		n[0] = alvo > n[0] ? Math.min(alvo, n[0] + dt / 8f) : Math.max(alvo, n[0] - dt / 8f);
		n[1] = t;
		if (alvo > 0) n[2] = Mth.lerp(Math.min(1f, dt * 0.3f), n[2], (float) v.getFluidHeight(FluidTags.WATER)); // linha d'água nos pés
		d.nado = AnimPesca.inOutSine(n[0]);
		d.agua = n[2];
	}

	/**
	 * Sentar, levantar e trocar de lugar sem teleporte. Quando o servidor muda o Márcio de lugar de uma vez
	 * (embarcar, descer na doca, deitar na cama, testes), o corpo sai do lugar antigo e faz o caminho até o novo
	 * com a animação do tipo certo: da terra ele anda até a borda, passa a perna por cima e senta; da água ele
	 * se agarra na borda e sobe; para descer ele levanta apoiando a mão, passa a perna e anda até a doca.
	 */
	private static void transicao(Villager v, LivingEntityRenderState st, float t, float partial, DadosMarcio d) {
		int id = v.getId();
		boolean noBarco = v.isPassenger();
		Vec3 pos = v.getPosition(partial);
		double[] sl = saltos.get(id);
		if (sl == null) saltos.put(id, sl = new double[]{pos.x, pos.y, pos.z, t, 0, 0, 0, -1000, 1, SEM});
		boolean visto = t - sl[3] < 3; // estava sendo desenhado até agora (senão a troca aconteceu fora da tela)
		float[] a0 = assento.computeIfAbsent(id, k -> new float[]{noBarco ? 1f : 0f, -1000f, -1000f});
		boolean mudou = (a0[0] > 0.5f) != noBarco;
		if (mudou) { a0[0] = noBarco ? 1f : 0f; a0[1] = visto ? t : -1000f; mudou = visto; }
		if (v.isInWater() && !noBarco) a0[2] = t;
		int tipoTroca = noBarco ? (t - a0[2] < 10 ? SUBIR_AGUA : SUBIR_TERRA) : DESCER;

		float u = (float) ((t - sl[7]) / sl[8]);
		double[] o = u < 1 ? caminho((int) sl[9], u, sl[4], sl[5], sl[6]) : new double[3];
		if (t - sl[3] < 3 && pos.distanceToSqr(sl[0], sl[1], sl[2]) > 0.9 * 0.9) {
			// mudou de lugar de uma vez: começa do ponto onde o corpo está agora (continua um caminho em andamento)
			sl[4] = sl[0] - pos.x + o[0]; sl[5] = sl[1] - pos.y + o[1]; sl[6] = sl[2] - pos.z + o[2];
			sl[7] = t;
			sl[9] = t - a0[1] < 4 ? tipoTroca : PULO;
			sl[8] = duracao((int) sl[9], Math.sqrt(sl[4] * sl[4] + sl[6] * sl[6]));
			u = 0;
		} else if (mudou && (u >= 1 || (sl[9] == PULO && t - sl[7] < 4))) {
			// subiu ou desceu sem pular longe (ou a troca de barco chegou logo depois do pulo): anima mesmo assim
			if (u >= 1) { sl[4] = sl[5] = sl[6] = 0; sl[7] = t; u = 0; }
			sl[9] = tipoTroca;
			sl[8] = duracao(tipoTroca, Math.sqrt(sl[4] * sl[4] + sl[6] * sl[6]));
		}
		sl[0] = pos.x; sl[1] = pos.y; sl[2] = pos.z; sl[3] = t;
		boolean ativo = u < 1 && sl[9] != SEM;
		o = ativo ? caminho((int) sl[9], u, sl[4], sl[5], sl[6]) : new double[3];
		d.saltoX = o[0]; d.saltoY = o[1]; d.saltoZ = o[2];
		d.trans = ativo ? (int) sl[9] : SEM;
		d.transU = ativo ? u : 1f;
		d.transDist = (float) Math.sqrt(sl[4] * sl[4] + sl[6] * sl[6]);

		// enquanto anda até o barco (ou até a doca), o corpo vira para onde está indo e depois volta
		if (ativo && d.transDist > 0.3f && sl[9] != PULO) {
			float rumo = (float) (Mth.atan2(sl[4], -sl[6]) * Mth.RAD_TO_DEG); // do lugar antigo para o novo
			float w = switch (d.trans) {
				case DESCER -> u < 0.35f ? 0f : Math.min(1f, Math.min((u - 0.35f) / 0.1f, (1f - u) / 0.25f));
				default -> Math.min(1f, Math.min(u / 0.1f, (0.8f - u) / 0.15f));
			};
			w = AnimPesca.inOutSine(Math.max(0f, w));
			float giro = Mth.wrapDegrees(rumo - st.bodyRot) * w;
			st.bodyRot += giro;
			st.yRot = Mth.clamp(st.yRot - giro, -60f, 60f); // a cabeça continua olhando para o mesmo lugar
		}

		// de pé (1) ou sentado (0): a troca normal leva 0,7 s; nas transições segue as fases delas
		float peTroca = noBarco ? 1f - AnimPesca.inOutSine((t - a0[1]) / 14f) : AnimPesca.inOutSine((t - a0[1]) / 14f);
		if (d.trans == SUBIR_TERRA || d.trans == SUBIR_AGUA) peTroca = 1f - AnimPesca.inOutSine((u - 0.75f) / 0.25f);
		else if (d.trans == DESCER) peTroca = AnimPesca.inOutSine(u / 0.35f);
		float pe = AnimPesca.peLuta(d.fase, d.tempo, d.lutou);
		if (d.anim == AnimMarcio.CARINHO && d.animVar == 2) pe = Math.max(pe, 0.35f * AnimMarcio.peso(d.anim, d.animTempo)); // meio de pé para alcançar o Pinto
		d.pe = noBarco ? Math.max(peTroca, pe) : peTroca;
		d.sentado = noBarco || d.pe < 1f; // ainda terminando de levantar depois de descer
	}

	private static float duracao(int tipo, double dist) {
		return (float) switch (tipo) {
			case SUBIR_TERRA -> Math.min(36, 24 + 4 * dist);
			case SUBIR_AGUA -> Math.min(40, 30 + 3 * dist);
			case DESCER -> Math.min(38, 26 + 4 * dist);
			default -> Math.min(20, 12 + 3 * dist);
		};
	}

	/** Desvio do corpo (lugar antigo - novo) que ainda falta andar no ponto 'u' (0..1) da transição. */
	private static double[] caminho(int tipo, float u, double dx, double dy, double dz) {
		float h, hv;
		double arco = 0;
		switch (tipo) {
			case SUBIR_TERRA -> { // anda até a borda, passa a perna por cima (com um passo para cima) e senta
				h = u < 0.45f ? 0.55f * io(u / 0.45f) : u < 0.75f ? 0.55f + 0.35f * io((u - 0.45f) / 0.3f) : 0.9f + 0.1f * io((u - 0.75f) / 0.25f);
				hv = h;
				if (u >= 0.45f && u < 0.75f) arco = 0.3 * Math.sin(Math.PI * (u - 0.45f) / 0.3f);
			}
			case SUBIR_AGUA -> { // nada até a borda, se agarra e se puxa para cima, depois senta
				h = u < 0.35f ? 0.4f * io(u / 0.35f) : u < 0.7f ? 0.4f + 0.5f * io((u - 0.35f) / 0.35f) : 0.9f + 0.1f * io((u - 0.7f) / 0.3f);
				hv = u < 0.35f ? 0.1f * io(u / 0.35f) : u < 0.7f ? 0.1f + 0.85f * io((u - 0.35f) / 0.35f) : 0.95f + 0.05f * io((u - 0.7f) / 0.3f);
				if (u >= 0.35f && u < 0.7f) arco = 0.15 * Math.sin(Math.PI * (u - 0.35f) / 0.35f);
			}
			case DESCER -> { // levanta no lugar, passa a perna por cima da borda e anda até a doca
				h = u < 0.35f ? 0f : u < 0.7f ? 0.75f * io((u - 0.35f) / 0.35f) : 0.75f + 0.25f * io((u - 0.7f) / 0.3f);
				hv = h;
				if (u >= 0.35f && u < 0.7f) arco = 0.3 * Math.sin(Math.PI * (u - 0.35f) / 0.35f);
			}
			default -> {
				h = hv = io(u);
				arco = Math.min(0.6, Math.sqrt(dx * dx + dz * dz) * 0.25) * Math.sin(Math.PI * u);
			}
		}
		return new double[]{dx * (1 - h), dy * (1 - hv) + arco, dz * (1 - h)};
	}

	private static float io(float u) { return AnimPesca.inOutSine(u); }
}

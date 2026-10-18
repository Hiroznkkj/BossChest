package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Asas;
import br.hirozn.bauchefe.Boia;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Estado do Márcio no cliente: falas e animações recebidas do servidor e as piscadas. */
public final class MarcioCliente {
	public static final int DUR_FALA = 80;

	private record Fala(String[] palavras, float[] tempos, long inicio, int duracao) {}
	/** Tempo de cada palavra nos áudios (assets/bauchefe/marcio_falas.json, gerado a partir da transcrição). */
	private record Tempos(String[] palavras, float[] tempos, float duracao) {}
	private static Map<String, Tempos> tempos;
	private record Anim(int id, long inicio) {}

	private static final Int2ObjectMap<Fala> falas = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<Anim> anims = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<float[]> nado = new Int2ObjectOpenHashMap<>(); // {peso, último tempo}
	private static final Int2ObjectMap<float[]> piscadas = new Int2ObjectOpenHashMap<>(); // {próxima piscada}
	private static final net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create();
	private static final Int2ObjectMap<double[]> saltos = new Int2ObjectOpenHashMap<>(); // {x,y,z,t, desvio x,y,z, início}
	private static final Int2ObjectMap<float[]> assento = new Int2ObjectOpenHashMap<>(); // {1 no barco, tempo da troca}
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
		if (t != null) falas.put(p.villager(), new Fala(t.palavras(), t.tempos(), agora(), Math.max(DUR_FALA, (int) (t.duracao() * 20) + 30)));
		else falas.put(p.villager(), new Fala(p.texto().split(" "), null, agora(), DUR_FALA));
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
		anims.put(p.villager(), new Anim(p.anim(), t));
		long mundoT = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime(); // Asas usa o tempo do mundo
		if (p.anim() == AnimMarcio.CARINHO) { // o Pinto bate as asas no meio do carinho
			Minecraft mc = Minecraft.getInstance();
			Entity v = mc.level == null ? null : mc.level.getEntity(p.villager());
			if (v != null && v.getVehicle() != null)
				for (Entity e : v.getVehicle().getPassengers())
					if (Pescador.ehPinto(e)) { Asas.bater(e.getId(), mundoT + 40); Asas.carinho(e.getId(), mundoT); }
		}
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

		// sentar/levantar: ao embarcar ou descer, a troca leva 0,7 s; na briga com peixe difícil ele fica de pé
		boolean noBarco = v.isPassenger();
		float[] a0 = assento.computeIfAbsent(id, k -> new float[]{noBarco ? 1f : 0f, -1000f});
		if ((a0[0] > 0.5f) != noBarco) { a0[0] = noBarco ? 1f : 0f; a0[1] = t; }
		float u = AnimPesca.inOutSine((t - a0[1]) / 14f);
		float peTroca = noBarco ? 1f - u : u;
		float peLuta = d.fase == AnimPesca.LUTA ? AnimPesca.inOutSine(d.tempo / 10f)
				: d.fase == AnimPesca.PUXANDO && d.lutou ? 1f - AnimPesca.inOutSine(d.tempo / AnimPesca.DUR_PUXANDO) : 0f;
		d.pe = noBarco ? Math.max(peTroca, peLuta) : peTroca;
		d.sentado = noBarco || d.pe < 1f; // ainda terminando de levantar depois de descer

		Anim a = anims.get(id);
		if (a != null && t - a.inicio() > AnimMarcio.duracao(a.id())) { anims.remove(id); a = null; }
		d.anim = a == null ? AnimMarcio.NADA : a.id();
		d.animTempo = a == null ? 0 : t - a.inicio();

		Fala f = falas.get(id);
		if (f != null && t - f.inicio() > f.duracao()) { falas.remove(id); f = null; }
		d.fala = f == null ? null : f.palavras();
		if (f != null) {
			float e = t - f.inicio();
			d.falaTempos = f.tempos();
			d.falaSeg = e / 20f;
			d.falaAlpha = Mth.clamp(Math.min(e / 4f, (f.duracao() - e) / 10f), 0f, 1f); // aparece rápido, some devagar
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
		d.palpebra = Math.max(pisca, aperta);
		d.distancia = (float) Math.sqrt(st.distanceToCameraSq);

		// salto: se a posição mudou de uma vez (> 0,9 bloco num tick), o corpo sai do lugar antigo e chega
		// ao novo em 0,5 s, num arco (embarcar, descer na doca, testes); continua um salto em andamento
		Vec3 pos = v.getPosition(partial);
		double[] sl = saltos.get(id);
		if (sl == null) saltos.put(id, sl = new double[]{pos.x, pos.y, pos.z, t, 0, 0, 0, -1000});
		double ox = 0, oy = 0, oz = 0;
		float us = (float) ((t - sl[7]) / 10.0);
		if (us < 1) {
			float e = 1f - AnimPesca.inOutSine(us);
			ox = sl[4] * e; oy = sl[5] * e; oz = sl[6] * e;
		}
		if (t - sl[3] < 3 && pos.distanceToSqr(sl[0], sl[1], sl[2]) > 0.9 * 0.9) {
			sl[4] = sl[0] - pos.x + ox; sl[5] = sl[1] - pos.y + oy; sl[6] = sl[2] - pos.z + oz;
			sl[7] = t;
			ox = sl[4]; oy = sl[5]; oz = sl[6];
			us = 0;
		}
		sl[0] = pos.x; sl[1] = pos.y; sl[2] = pos.z; sl[3] = t;
		double arco = us < 1 ? Math.min(0.6, Math.sqrt(sl[4] * sl[4] + sl[6] * sl[6]) * 0.25) * Mth.sin(Mth.PI * us) : 0;
		d.saltoX = ox; d.saltoY = oy + arco; d.saltoZ = oz;

		// nado: entra e sai aos poucos (~0,4 s), calculado pelo tempo que passou desde o último quadro
		float[] n = nado.computeIfAbsent(id, k -> new float[]{0f, t});
		float alvo = v.isInWater() && !v.isPassenger() ? 1f : 0f, dt = Mth.clamp(t - n[1], 0f, 5f);
		n[0] = alvo > n[0] ? Math.min(alvo, n[0] + dt / 8f) : Math.max(alvo, n[0] - dt / 8f);
		n[1] = t;
		d.nado = AnimPesca.inOutSine(n[0]);
	}
}

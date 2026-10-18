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
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

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
	private static final Int2ObjectMap<float[]> piscadas = new Int2ObjectOpenHashMap<>(); // {próxima piscada}
	private static final RandomSource r = RandomSource.create();

	private MarcioCliente() {}

	private static long agora() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : mc.level.getGameTime();
	}

	public static void fala(Pacotes.Fala p) {
		Tempos t = p.som().isEmpty() ? null : tempos().get(p.som());
		if (t != null) falas.put(p.villager(), new Fala(t.palavras(), t.tempos(), agora(), Math.max(DUR_FALA, (int) (t.duracao() * 20) + 30)));
		else falas.put(p.villager(), new Fala(p.texto().split(" "), null, agora(), DUR_FALA));
		Minecraft mc = Minecraft.getInstance();
		Entity v = mc.level == null ? null : mc.level.getEntity(p.villager());
		if (v != null && !p.som().isEmpty()) MarcioSom.tocar(v, p.som());
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
		if (p.anim() == AnimMarcio.CARINHO) { // o Pinto bate as asas no meio do carinho
			Minecraft mc = Minecraft.getInstance();
			Entity v = mc.level == null ? null : mc.level.getEntity(p.villager());
			if (v != null && v.getVehicle() != null)
				for (Entity e : v.getVehicle().getPassengers()) if (Pescador.ehPinto(e)) Asas.bater(e.getId(), t + 40);
		}
	}

	/** Preenche os dados do render state do pescador. */
	public static void extrair(Villager v, VillagerRenderState st, float partial, DadosMarcio d) {
		int id = v.getId();
		float t = agora() + partial;
		d.pescador = true;
		d.sentado = v.isPassenger();
		d.rFase = AnimMarcio.fase(id);

		Boia b = Boia.doDono(id);
		d.fase = b == null ? AnimPesca.NADA : b.faseCliente();
		d.tempo = b == null ? 0 : b.tempoFase(partial);

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

		// pupilas seguem o olhar da cabeça, com pequenas mexidas (microssacadas)
		float rf = d.rFase, age = st.ageInTicks;
		float sac = Mth.clamp(Mth.sin(rf + age / 27f) + Mth.sin(rf + age / 16f), -0.12f, 0.12f);
		d.pupilaX = Mth.clamp(-st.yRot / 45f, -1f, 1f) * 0.5f + sac;
		d.pupilaY = Mth.clamp(st.xRot / 45f, -1f, 1f) * 0.3f + Mth.clamp(Mth.sin(rf + age / 38f) + Mth.sin(rf + age / 13f), -0.08f, 0.08f);

		// pisca a cada 3 a 6 s: fecha em 1 tick e abre em 2 (às vezes pisca duas vezes)
		float[] p = piscadas.computeIfAbsent(id, k -> new float[]{t + 20 + r.nextInt(60)});
		float u = t - p[0];
		if (u >= 3) { p[0] = t + (r.nextFloat() < 0.2f ? 6 : 60 + r.nextInt(60)); u = -1; }
		d.palpebra = u < 0 ? 0 : u < 1 ? u : 1 - (u - 1) / 2f;

		d.distancia = (float) Math.sqrt(st.distanceToCameraSq);
	}
}

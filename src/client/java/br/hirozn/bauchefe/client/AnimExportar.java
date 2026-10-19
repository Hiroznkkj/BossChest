package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.BauChefe;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * /marcioanim exportar: grava as animações do código como arquivo do Blockbench (Bedrock), junto com o modelo
 * (marcio.geo.json) e a skin, em config/bauchefe/animacoes/originais/. Abrindo no Blockbench dá para mexer nelas e
 * salvar em config/bauchefe/animacoes/, que o mod usa no lugar das do código.
 */
public final class AnimExportar {
	private static final String[] OSSOS = {"root", "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};
	private static final String[] PARTES = {null, "head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

	private record Def(String nome, int ticks, boolean loop, BiConsumer<DadosMarcio, Float> preparar) {}

	private AnimExportar() {}

	/** Exporta e devolve a pasta onde ficou. */
	public static Path exportar() throws Exception {
		Path pasta = AnimCustom.pasta().resolve("originais");
		Files.createDirectories(pasta);
		ModelPart raiz = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER);
		ModelPart[] p = new ModelPart[OSSOS.length];
		p[0] = raiz;
		for (int i = 1; i < p.length; i++) p[i] = raiz.getChild(PARTES[i]);

		List<Def> defs = new ArrayList<>();
		defs.add(new Def(AnimCustom.PARADO, 40, true, (d, t) -> {}));
		for (int a = 1; a < AnimMarcio.NOMES.length; a++) {
			int id = a;
			defs.add(new Def(AnimMarcio.NOMES[a], AnimMarcio.duracao(a), false, (d, t) -> { d.anim = id; d.animTempo = t; }));
		}
		int[] durFase = {0, AnimPesca.DUR_PREPARA, AnimPesca.DUR_VOO, 70, AnimPesca.DUR_MORDIDA, AnimPesca.DUR_PUXANDO, AnimPesca.DUR_LUTA};
		for (int f = 1; f < AnimPesca.NOMES.length; f++) {
			int fase = f;
			defs.add(new Def(AnimPesca.NOMES[f], durFase[f], f == AnimPesca.BOIANDO, (d, t) -> {
				d.fase = fase; d.tempo = t;
				d.pe = AnimPesca.peLuta(fase, t, false); // fica de pé para brigar com o peixe
			}));
		}

		Map<String, Object> animacoes = new LinkedHashMap<>();
		PoseMarcio.exportando = true;
		try {
			for (Def def : defs) animacoes.put("animation.marcio." + def.nome(), gravar(def, p));
		} finally {
			PoseMarcio.exportando = false;
			for (ModelPart x : p) x.resetPose();
		}
		Map<String, Object> arq = new LinkedHashMap<>();
		arq.put("format_version", "1.8.0");
		arq.put("animations", animacoes);
		var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		Files.writeString(pasta.resolve("marcio.animation.json"), gson.toJson(arq));
		Files.writeString(pasta.resolve("marcio.geo.json"), gson.toJson(geometria()));
		var skin = Minecraft.getInstance().getResourceManager().getResource(BauChefe.id("textures/entity/marcio.png"));
		if (skin.isPresent()) try (InputStream in = skin.get().open()) { Files.copy(in, pasta.resolve("marcio.png"), StandardCopyOption.REPLACE_EXISTING); }
		return pasta;
	}

	/** Amostra a pose a cada 2 ticks e vira quadros-chave por osso (só os canais que saem da pose parada). */
	private static Map<String, Object> gravar(Def def, ModelPart[] p) {
		int n = def.ticks() / 2 + 1;
		float[][][] val = new float[OSSOS.length][3][]; // osso -> canal (rot, pos, esc) -> 3 valores por quadro
		for (int o = 0; o < OSSOS.length; o++) for (int c = 0; c < 3; c++) val[o][c] = new float[n * 3];
		float[] tempos = new float[n];
		for (int k = 0; k < n; k++) {
			float t = Math.min(k * 2, def.ticks());
			tempos[k] = t / 20f;
			for (ModelPart x : p) x.resetPose();
			DadosMarcio d = new DadosMarcio();
			d.sentado = true;
			d.agora = t;
			def.preparar().accept(d, t);
			PoseMarcio.aplicar(d, t, p[0], p[1], p[2], p[3], p[4], p[5], p[6]);
			for (int o = 0; o < OSSOS.length; o++) {
				ModelPart m = p[o];
				PartPose ini = m.getInitialPose();
				float[] r = {(m.xRot - ini.xRot()) * Mth.RAD_TO_DEG, (m.yRot - ini.yRot()) * Mth.RAD_TO_DEG, (m.zRot - ini.zRot()) * Mth.RAD_TO_DEG};
				float[] q = {m.x - ini.x(), -(m.y - ini.y()), m.z - ini.z()}; // no Blockbench y é para cima
				float[] e = {m.xScale, m.yScale, m.zScale};
				System.arraycopy(r, 0, val[o][0], k * 3, 3);
				System.arraycopy(q, 0, val[o][1], k * 3, 3);
				System.arraycopy(e, 0, val[o][2], k * 3, 3);
			}
		}
		Map<String, Object> ossos = new LinkedHashMap<>();
		String[] canais = {"rotation", "position", "scale"};
		for (int o = 0; o < OSSOS.length; o++) {
			Map<String, Object> osso = new LinkedHashMap<>();
			for (int c = 0; c < 3; c++) {
				float[] v = val[o][c];
				float base = c == 2 ? 1f : 0f;
				boolean usa = false;
				for (float x : v) if (Math.abs(x - base) > 1e-3f) { usa = true; break; }
				if (usa) osso.put(canais[c], quadros(tempos, v));
			}
			if (!osso.isEmpty()) ossos.put(OSSOS[o], osso);
		}
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("loop", def.loop());
		a.put("animation_length", def.ticks() / 20f);
		a.put("bones", ossos);
		return a;
	}

	/** Quadros-chave, tirando os do meio que só repetem o vizinho (linha reta). */
	private static Map<String, Object> quadros(float[] tempos, float[] v) {
		Map<String, Object> q = new LinkedHashMap<>();
		int n = tempos.length;
		for (int k = 0; k < n; k++) {
			if (k > 0 && k < n - 1 && igual(v, k, k - 1) && igual(v, k, k + 1)) continue;
			q.put(String.format(Locale.ROOT, "%.2f", tempos[k]), new double[]{arred(v[k * 3]), arred(v[k * 3 + 1]), arred(v[k * 3 + 2])});
		}
		return q;
	}

	private static boolean igual(float[] v, int a, int b) {
		for (int i = 0; i < 3; i++) if (Math.abs(v[a * 3 + i] - v[b * 3 + i]) > 1e-3f) return false;
		return true;
	}

	private static double arred(float x) { return Math.round(x * 1000.0) / 1000.0; }

	/** Modelo de jogador (braços largos) no formato do Blockbench, com o "root" por fora de tudo como no jogo. */
	private static Map<String, Object> geometria() {
		List<Object> ossos = new ArrayList<>();
		ossos.add(osso("root", null, new float[]{0, 24, 0}));
		ossos.add(osso("body", "root", new float[]{0, 24, 0}, cubo(-4, 12, -2, 8, 12, 4, 16, 16, 0), cubo(-4, 12, -2, 8, 12, 4, 16, 32, 0.25f)));
		ossos.add(osso("head", "root", new float[]{0, 24, 0}, cubo(-4, 24, -4, 8, 8, 8, 0, 0, 0), cubo(-4, 24, -4, 8, 8, 8, 32, 0, 0.5f)));
		ossos.add(osso("rightArm", "root", new float[]{-5, 22, 0}, cubo(-8, 12, -2, 4, 12, 4, 40, 16, 0), cubo(-8, 12, -2, 4, 12, 4, 40, 32, 0.25f)));
		ossos.add(osso("leftArm", "root", new float[]{5, 22, 0}, cubo(4, 12, -2, 4, 12, 4, 32, 48, 0), cubo(4, 12, -2, 4, 12, 4, 48, 48, 0.25f)));
		ossos.add(osso("rightLeg", "root", new float[]{-1.9f, 12, 0}, cubo(-3.9f, 0, -2, 4, 12, 4, 0, 16, 0), cubo(-3.9f, 0, -2, 4, 12, 4, 0, 32, 0.25f)));
		ossos.add(osso("leftLeg", "root", new float[]{1.9f, 12, 0}, cubo(-0.1f, 0, -2, 4, 12, 4, 16, 48, 0), cubo(-0.1f, 0, -2, 4, 12, 4, 0, 48, 0.25f)));
		Map<String, Object> desc = new LinkedHashMap<>();
		desc.put("identifier", "geometry.marcio");
		desc.put("texture_width", 64);
		desc.put("texture_height", 64);
		desc.put("visible_bounds_width", 3);
		desc.put("visible_bounds_height", 3);
		desc.put("visible_bounds_offset", new int[]{0, 1, 0});
		Map<String, Object> geo = new LinkedHashMap<>();
		geo.put("description", desc);
		geo.put("bones", ossos);
		Map<String, Object> arq = new LinkedHashMap<>();
		arq.put("format_version", "1.12.0");
		arq.put("minecraft:geometry", List.of(geo));
		return arq;
	}

	private static Map<String, Object> osso(String nome, String pai, float[] pivo, Object... cubos) {
		Map<String, Object> o = new LinkedHashMap<>();
		o.put("name", nome);
		if (pai != null) o.put("parent", pai);
		o.put("pivot", pivo);
		if (cubos.length > 0) o.put("cubes", cubos);
		return o;
	}

	private static Map<String, Object> cubo(float x, float y, float z, int w, int h, int d, int u, int v, float infla) {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("origin", new float[]{x, y, z});
		c.put("size", new int[]{w, h, d});
		if (infla > 0) c.put("inflate", infla);
		c.put("uv", new int[]{u, v});
		return c;
	}
}

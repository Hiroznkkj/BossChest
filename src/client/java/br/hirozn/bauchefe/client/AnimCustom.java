package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.BauChefe;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Animações customizadas do Márcio, no formato de animação do Blockbench (Bedrock, .animation.json).
 * Ficam em config/bauchefe/animacoes/ (recarregam sozinhas quando o arquivo muda) ou em
 * assets/bauchefe/animacoes/ (no mod ou num resource pack). Uma animação com o nome de uma que já existe
 * (acenar, pesca_luta, parado...) toma o lugar da do código; as outras tocam com /marcioanim tocar.
 * Ossos: head, body, rightArm, leftArm, rightLeg, leftLeg e root. Rotação em graus; posição em pixels
 * (y para cima, como no Blockbench). Os valores são a pose a partir do modelo parado (em pé, braços para baixo).
 */
public final class AnimCustom {
	public static final Path PASTA = FabricLoader.getInstance().getConfigDir().resolve("bauchefe").resolve("animacoes");
	/** Nomes das animações do código que dá para substituir (além das fases da pesca, AnimPesca.NOMES). */
	public static final String PARADO = "parado";

	/** Um canal de um osso (rotação, posição ou escala): quadros-chave no tempo (s), valor chegando e saindo de cada um. */
	record Canal(float[] t, float[][] pre, float[][] pos, int[] modo) {
		static final int LINEAR = 0, SUAVE = 1, DEGRAU = 2;

		void valor(float s, float[] out) {
			int n = t.length;
			if (n == 1 || s <= t[0]) { copiar(n == 1 ? pos[0] : pre[0], out); return; }
			if (s >= t[n - 1]) { copiar(pos[n - 1], out); return; }
			int i = 0;
			while (i < n - 2 && s >= t[i + 1]) i++;
			float u = (s - t[i]) / Math.max(1e-6f, t[i + 1] - t[i]);
			if (modo[i] == DEGRAU) { copiar(pos[i], out); return; }
			boolean suave = modo[i] == SUAVE || modo[i + 1] == SUAVE;
			for (int k = 0; k < 3; k++) {
				float p1 = pos[i][k], p2 = pre[i + 1][k];
				if (!suave) { out[k] = Mth.lerp(u, p1, p2); continue; }
				float p0 = i > 0 ? pos[i - 1][k] : p1, p3 = i + 2 < n ? pre[i + 2][k] : p2; // Catmull-Rom, como o Blockbench
				out[k] = 0.5f * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
			}
		}

		private static void copiar(float[] v, float[] out) { out[0] = v[0]; out[1] = v[1]; out[2] = v[2]; }
	}

	record Osso(Canal rot, Canal pos, Canal esc) {}

	/** duracao em segundos; loop = repete; somar = soma na pose atual em vez de trocar ("bauchefe_somar": true). */
	public record Anim(String nome, float duracao, boolean loop, boolean somar, Map<String, Osso> ossos) {}

	private static Map<String, Anim> anims = Map.of();
	private static long assinatura = -1, checado;
	public static String ultimoErro;

	private AnimCustom() {}

	/** Animação customizada com esse nome, ou null. Confere a pasta no máximo uma vez por segundo. */
	public static Anim get(String nome) {
		if (nome == null) return null;
		long agora = System.currentTimeMillis();
		if (agora - checado > 1000) {
			checado = agora;
			long a = assinar();
			if (a != assinatura) { assinatura = a; recarregar(); }
		}
		return anims.get(nome);
	}

	public static Set<String> nomes() { get(""); return new TreeSet<>(anims.keySet()); }

	/** Lê tudo de novo (resource packs primeiro; a pasta da config passa por cima). Devolve quantas carregou. */
	public static int recarregar() {
		Map<String, Anim> novo = new HashMap<>();
		ultimoErro = null;
		try {
			var rm = Minecraft.getInstance().getResourceManager();
			for (var e : rm.listResources("animacoes", id -> id.getNamespace().equals(BauChefe.MODID) && id.getPath().endsWith(".json")).entrySet())
				try (Reader r = e.getValue().openAsReader()) { ler(r, e.getKey().toString(), novo); }
				catch (Exception ex) { erro(e.getKey().toString(), ex); }
		} catch (Exception ignorado) {} // sem resource manager ainda
		if (Files.isDirectory(PASTA)) {
			try (Stream<Path> l = Files.list(PASTA)) {
				for (Path p : l.filter(x -> x.toString().endsWith(".json")).sorted().toList())
					try (Reader r = Files.newBufferedReader(p)) { ler(r, p.getFileName().toString(), novo); }
					catch (Exception ex) { erro(p.getFileName().toString(), ex); }
			} catch (Exception ex) { erro(PASTA.toString(), ex); }
		}
		anims = novo;
		checado = System.currentTimeMillis();
		assinatura = assinar();
		return novo.size();
	}

	private static void erro(String arquivo, Exception ex) {
		ultimoErro = arquivo + ": " + ex.getMessage();
		BauChefe.LOG.warn("[Baú Chefe] Animação do Márcio com erro em {}", arquivo, ex);
	}

	/** Muda quando algum .json da pasta muda (nome, tamanho ou data). */
	private static long assinar() {
		if (!Files.isDirectory(PASTA)) return 0;
		long h = 1;
		try (Stream<Path> l = Files.list(PASTA)) {
			for (Path p : l.filter(x -> x.toString().endsWith(".json")).toList())
				h += p.getFileName().toString().hashCode() * 31L + Files.size(p) * 17L + Files.getLastModifiedTime(p).toMillis();
		} catch (Exception e) { return -2; }
		return h;
	}

	// ---------- leitura ----------

	private static void ler(Reader r, String arquivo, Map<String, Anim> out) {
		JsonObject raiz = JsonParser.parseReader(r).getAsJsonObject();
		if (!raiz.has("animations")) throw new IllegalArgumentException("sem \"animations\" (exporte pelo Blockbench: Animation > Export Animations)");
		for (var e : raiz.getAsJsonObject("animations").entrySet()) {
			String chave = e.getKey();
			String nome = chave.substring(chave.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT); // animation.marcio.acenar -> acenar
			JsonObject a = e.getValue().getAsJsonObject();
			Map<String, Osso> ossos = new HashMap<>();
			float fim = 0;
			if (a.has("bones")) for (var b : a.getAsJsonObject("bones").entrySet()) {
				String osso = b.getKey().toLowerCase(Locale.ROOT).replace("_", "");
				JsonObject o = b.getValue().getAsJsonObject();
				Canal rot = canal(o.get("rotation"), 0), pos = canal(o.get("position"), 0), esc = canal(o.get("scale"), 1);
				for (Canal c : new Canal[]{rot, pos, esc}) if (c != null) fim = Math.max(fim, c.t()[c.t().length - 1]);
				ossos.put(osso, new Osso(rot, pos, esc));
			}
			float dur = a.has("animation_length") ? a.get("animation_length").getAsFloat() : fim;
			JsonElement loop = a.get("loop");
			boolean repete = loop != null && loop.isJsonPrimitive() && loop.getAsJsonPrimitive().isBoolean() && loop.getAsBoolean();
			boolean somar = a.has("bauchefe_somar") && a.get("bauchefe_somar").getAsBoolean();
			out.put(nome, new Anim(nome, Math.max(dur, 0.05f), repete, somar, ossos));
		}
	}

	private static Canal canal(JsonElement j, float padrao) {
		if (j == null || j.isJsonNull()) return null;
		if (!j.isJsonObject()) { float[] v = vetor(j, padrao); return new Canal(new float[]{0}, new float[][]{v}, new float[][]{v}, new int[]{0}); }
		TreeMap<Float, JsonElement> quadros = new TreeMap<>();
		for (var e : j.getAsJsonObject().entrySet()) quadros.put(Float.parseFloat(e.getKey()), e.getValue());
		if (quadros.isEmpty()) return null;
		int n = quadros.size(), i = 0;
		float[] t = new float[n];
		float[][] pre = new float[n][], pos = new float[n][];
		int[] modo = new int[n];
		for (var e : quadros.entrySet()) {
			t[i] = e.getKey();
			JsonElement v = e.getValue();
			if (v.isJsonObject()) {
				JsonObject o = v.getAsJsonObject();
				JsonElement p = o.has("pre") ? o.get("pre") : o.has("post") ? o.get("post") : o.get("vector");
				JsonElement q = o.has("post") ? o.get("post") : o.has("pre") ? o.get("pre") : o.get("vector");
				pre[i] = vetor(p, padrao);
				pos[i] = vetor(q, padrao);
				String m = o.has("lerp_mode") ? o.get("lerp_mode").getAsString() : "linear";
				modo[i] = m.equals("catmullrom") ? Canal.SUAVE : m.equals("step") ? Canal.DEGRAU : Canal.LINEAR;
			} else pre[i] = pos[i] = vetor(v, padrao);
			i++;
		}
		return new Canal(t, pre, pos, modo);
	}

	/** [x, y, z] (números ou textos com número) ou um número só para os três. Molang não é suportado (vira 0). */
	private static float[] vetor(JsonElement j, float padrao) {
		if (j == null || j.isJsonNull()) return new float[]{padrao, padrao, padrao};
		if (j.isJsonArray()) {
			var a = j.getAsJsonArray();
			float[] v = {padrao, padrao, padrao};
			for (int k = 0; k < Math.min(3, a.size()); k++) v[k] = numero(a.get(k));
			if (a.size() == 1) v[1] = v[2] = v[0];
			return v;
		}
		float n = numero(j);
		return new float[]{n, n, n};
	}

	private static float numero(JsonElement j) {
		try { return j.getAsJsonPrimitive().isNumber() ? j.getAsFloat() : Float.parseFloat(j.getAsString().trim()); }
		catch (Exception e) { ultimoErro = "valor não é número: " + j; return 0f; }
	}

	// ---------- aplicar ----------

	/**
	 * Aplica a animação no tempo 't' (ticks desde o começo) com peso 'w' (0..1, para entrar e sair sem salto).
	 * Sem "bauchefe_somar" o osso vai para a pose do arquivo; com ele, a pose do arquivo soma na que já está.
	 */
	public static void aplicar(Anim a, float t, float w, ModelPart root, ModelPart head, ModelPart body, ModelPart dir,
	                           ModelPart esq, ModelPart pernaD, ModelPart pernaE) {
		if (w <= 0) return;
		float s = t / 20f;
		s = a.loop() ? s % a.duracao() : Math.min(s, a.duracao());
		float[] v = new float[3];
		for (var e : a.ossos().entrySet()) {
			ModelPart p = switch (e.getKey()) {
				case "head" -> head;
				case "body" -> body;
				case "rightarm" -> dir;
				case "leftarm" -> esq;
				case "rightleg" -> pernaD;
				case "leftleg" -> pernaE;
				case "root" -> root;
				default -> null;
			};
			if (p == null) continue;
			Osso o = e.getValue();
			PartPose ini = p.getInitialPose();
			if (o.rot() != null) {
				o.rot().valor(s, v);
				float x = v[0] * Mth.DEG_TO_RAD, y = v[1] * Mth.DEG_TO_RAD, z = v[2] * Mth.DEG_TO_RAD;
				if (a.somar()) { p.xRot += w * x; p.yRot += w * y; p.zRot += w * z; }
				else { p.xRot = Mth.lerp(w, p.xRot, ini.xRot() + x); p.yRot = Mth.lerp(w, p.yRot, ini.yRot() + y); p.zRot = Mth.lerp(w, p.zRot, ini.zRot() + z); }
			}
			if (o.pos() != null) {
				o.pos().valor(s, v);
				float x = v[0], y = -v[1], z = v[2]; // no Blockbench y é para cima; no modelo, para baixo
				if (a.somar()) { p.x += w * x; p.y += w * y; p.z += w * z; }
				else { p.x = Mth.lerp(w, p.x, ini.x() + x); p.y = Mth.lerp(w, p.y, ini.y() + y); p.z = Mth.lerp(w, p.z, ini.z() + z); }
			}
			if (o.esc() != null) {
				o.esc().valor(s, v);
				float bx = a.somar() ? p.xScale * v[0] : v[0], by = a.somar() ? p.yScale * v[1] : v[1], bz = a.somar() ? p.zScale * v[2] : v[2];
				p.xScale = Mth.lerp(w, p.xScale, bx); p.yScale = Mth.lerp(w, p.yScale, by); p.zScale = Mth.lerp(w, p.zScale, bz);
			}
		}
	}
}

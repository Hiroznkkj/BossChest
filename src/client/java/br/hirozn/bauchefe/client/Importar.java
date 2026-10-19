package br.hirozn.bauchefe.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * Animações de outros lugares para o esqueleto padrão (Esqueleto), sem passar pelo Blockbench:
 * - Emotecraft (.json da pasta emotes/, o formato "emote" com "moves");
 * - o formato do Baú Chefe ("quadros"), simples de escrever à mão ou pedir para uma IA (ver ANIMACOES.md).
 * Tudo vira o mesmo AnimCustom.Anim que o Blockbench gera, então toca, mistura e passa pelo Resolver igual.
 */
public final class Importar {
	private Importar() {}

	// ---------- Emotecraft ----------

	/** Partes do Emotecraft -> ossos do esqueleto ("body" do Emotecraft é o corpo inteiro = root). */
	private static final Map<String, String> PARTES_EMOTE = Map.of("head", "head", "torso", "body", "rightArm", "rightarm",
			"leftArm", "leftarm", "rightLeg", "rightleg", "leftLeg", "leftleg", "body", "root");
	/** Onde fica o pivô de cada parte no modelo parado: o Emotecraft guarda a posição inteira, não o deslocamento. */
	private static final Map<String, float[]> PIVO = Map.of("rightArm", new float[]{-5, 2, 0}, "leftArm", new float[]{5, 2, 0},
			"rightLeg", new float[]{-1.9f, 12, 0}, "leftLeg", new float[]{1.9f, 12, 0});
	private static final String[] EIXOS = {"x", "y", "z", "pitch", "yaw", "roll"};

	/** Um emote do Emotecraft (formato json, "emote": {"moves": [...]}) como animação do esqueleto. */
	public static AnimCustom.Anim emotecraft(String nome, JsonObject raiz) {
		JsonObject e = raiz.getAsJsonObject("emote");
		boolean graus = !e.has("degrees") || e.get("degrees").getAsBoolean();
		int fim = inteiro(e, "endTick", 20), parar = inteiro(e, "stopTick", fim + 3);
		boolean loop = e.has("isLoop") && Boolean.parseBoolean(e.get("isLoop").getAsString());
		// quadros por parte e eixo: tique -> {valor, easing}
		Map<String, TreeMap<Integer, Object[]>> quadros = new HashMap<>();
		for (JsonElement mj : e.getAsJsonArray("moves")) {
			JsonObject mv = mj.getAsJsonObject();
			int t = inteiro(mv, "tick", 0);
			String ease = mv.has("easing") ? mv.get("easing").getAsString() : "linear";
			for (String parte : PARTES_EMOTE.keySet()) {
				if (!mv.has(parte)) continue;
				JsonObject p = mv.getAsJsonObject(parte);
				for (int k = 0; k < 6; k++) {
					if (!p.has(EIXOS[k])) continue;
					float v = p.get(EIXOS[k]).getAsFloat();
					if (k >= 3 && !graus) v *= Mth.RAD_TO_DEG;
					if (k < 3 && PIVO.containsKey(parte)) v -= PIVO.get(parte)[k]; // posição inteira -> deslocamento
					quadros.computeIfAbsent(parte + "." + k, x -> new TreeMap<>()).put(t, new Object[]{v, ease});
				}
			}
		}
		// amostra um valor por tique (o easing de cada trecho já fica dentro das amostras)
		int dur = loop ? Math.max(1, fim) : Math.max(1, parar);
		Map<String, AnimCustom.Osso> ossos = new HashMap<>();
		for (var en : PARTES_EMOTE.entrySet()) {
			String parte = en.getKey();
			boolean temRot = false, temPos = false;
			for (int k = 0; k < 6; k++) if (quadros.containsKey(parte + "." + k)) { if (k < 3) temPos = true; else temRot = true; }
			if (!temRot && !temPos) continue;
			float[] t = new float[dur + 1];
			float[][] rot = new float[dur + 1][3], pos = new float[dur + 1][3];
			for (int i = 0; i <= dur; i++) {
				t[i] = i / 20f;
				for (int k = 0; k < 6; k++) {
					float v = valorEmote(quadros.get(parte + "." + k), i, fim, parar, loop);
					if (k < 3) pos[i][k] = k == 1 ? -v : v; // o modelo tem y para baixo; a animação, para cima
					else rot[i][k - 3] = v;
				}
			}
			int[] modo = new int[dur + 1];
			ossos.put(en.getValue(), new AnimCustom.Osso(temRot ? new AnimCustom.Canal(t, rot, rot, modo) : null,
					temPos ? new AnimCustom.Canal(t, pos, pos, modo) : null, null));
		}
		return new AnimCustom.Anim(nome, dur / 20f, loop, false, ossos, AnimCustom.apoios(raiz.get("bauchefe_apoios")));
	}

	/** Valor de um eixo no tique 't': do padrão (0) até o primeiro quadro, entre quadros com o easing, e de volta no fim. */
	private static float valorEmote(TreeMap<Integer, Object[]> q, int t, int fim, int parar, boolean loop) {
		if (q == null || q.isEmpty()) return 0f;
		if (!loop && t >= fim) { // depois do fim volta para a pose parada até o stopTick
			var ult = q.floorEntry(fim);
			float v = ult == null ? 0f : (float) ult.getValue()[0];
			return parar > fim ? Mth.lerp(ease("easeinoutsine", Mth.clamp((t - fim) / (float) (parar - fim), 0f, 1f)), v, 0f) : 0f;
		}
		var a = q.floorEntry(t);
		var b = q.ceilingEntry(t);
		int ta = a == null ? 0 : a.getKey();
		float va = a == null ? 0f : (float) a.getValue()[0];
		if (b == null) return va;
		if (a == null && b.getKey() == 0) return (float) b.getValue()[0];
		if (b.getKey() == ta) return va;
		float u = (t - ta) / (float) (b.getKey() - ta);
		String ease = (String) b.getValue()[1]; // o easing do quadro de chegada
		return Mth.lerp(ease(ease, u), va, (float) b.getValue()[0]);
	}

	/** Easings do Emotecraft ("EASEINOUTQUAD", "LINEAR", "CONSTANT"...), de 0 a 1. */
	static float ease(String nome, float u) {
		String n = nome.toLowerCase(Locale.ROOT).replace("_", "");
		if (n.equals("constant")) return 0f;
		if (!n.startsWith("ease")) return u;
		boolean entra = n.startsWith("easein"), ambos = n.startsWith("easeinout");
		String tipo = n.substring(ambos ? 9 : entra ? 6 : n.startsWith("easeout") ? 7 : 4);
		if (ambos) return u < 0.5f ? curva(tipo, u * 2) / 2 : 1 - curva(tipo, (1 - u) * 2) / 2;
		return entra ? curva(tipo, u) : 1 - curva(tipo, 1 - u);
	}

	/** O "entrar" de cada easing (0..1 -> 0..1). Elastic e bounce viram cúbica (sem quique). */
	private static float curva(String tipo, float u) {
		return switch (tipo) {
			case "sine" -> 1 - Mth.cos(u * Mth.HALF_PI);
			case "quad" -> u * u;
			case "quart" -> u * u * u * u;
			case "quint" -> u * u * u * u * u;
			case "expo" -> u <= 0 ? 0 : (float) Math.pow(2, 10 * u - 10);
			case "circ" -> 1 - Mth.sqrt(Math.max(0, 1 - u * u));
			case "back" -> 2.70158f * u * u * u - 1.70158f * u * u;
			default -> u * u * u;
		};
	}

	private static int inteiro(JsonObject o, String k, int padrao) {
		try { return o.has(k) ? (int) o.get(k).getAsFloat() : padrao; } catch (Exception ex) { return padrao; }
	}

	// ---------- formato do Baú Chefe ----------

	/** Nomes aceitos para cada osso no formato próprio (português ou os do Blockbench). */
	private static final Map<String, String> OSSOS = new HashMap<>();
	static {
		String[][] n = {{"cabeca", "head"}, {"tronco", "body"}, {"corpo", "body"}, {"bracod", "rightarm"}, {"bracoe", "leftarm"},
				{"pernad", "rightleg"}, {"pernae", "leftleg"}, {"raiz", "root"}};
		for (String[] x : n) { OSSOS.put(x[0], x[1]); OSSOS.put(x[1], x[1]); }
	}

	/**
	 * Formato próprio: {"duracao": 1.5, "loop": false, "suave": true, "apoios": {"pes": 1}, "quadros": [
	 * {"t": 0, "bracoD": [0,0,0]}, {"t": 0.4, "bracoD": [-160,0,20], "cabeca": [0,15,0], "raizPos": [0,-2,0]}]}.
	 * Rotação em graus (como o modelo: x negativo leva o braço para a frente/cima); posição "<osso>Pos" em pixels,
	 * y para cima. Cada osso usa só os quadros em que aparece.
	 */
	public static AnimCustom.Anim proprio(String nomeArquivo, JsonObject raiz) {
		String nome = raiz.has("nome") ? raiz.get("nome").getAsString().toLowerCase(Locale.ROOT) : nomeArquivo;
		boolean suave = !raiz.has("suave") || raiz.get("suave").getAsBoolean();
		Map<String, TreeMap<Float, float[]>> rot = new HashMap<>(), pos = new HashMap<>();
		float fim = 0;
		for (JsonElement qj : raiz.getAsJsonArray("quadros")) {
			JsonObject q = qj.getAsJsonObject();
			float t = q.get("t").getAsFloat();
			fim = Math.max(fim, t);
			for (var en : q.entrySet()) {
				String k = en.getKey().toLowerCase(Locale.ROOT);
				boolean ehPos = k.endsWith("pos");
				String osso = OSSOS.get(ehPos ? k.substring(0, k.length() - 3) : k);
				if (osso == null || !en.getValue().isJsonArray()) continue;
				var a = en.getValue().getAsJsonArray();
				float[] v = {a.size() > 0 ? a.get(0).getAsFloat() : 0, a.size() > 1 ? a.get(1).getAsFloat() : 0, a.size() > 2 ? a.get(2).getAsFloat() : 0};
				(ehPos ? pos : rot).computeIfAbsent(osso, x -> new TreeMap<>()).put(t, v);
			}
		}
		Map<String, AnimCustom.Osso> ossos = new HashMap<>();
		Set<String> nomes = new HashSet<>(rot.keySet());
		nomes.addAll(pos.keySet());
		for (String o : nomes) ossos.put(o, new AnimCustom.Osso(canal(rot.get(o), suave), canal(pos.get(o), suave), null));
		float dur = raiz.has("duracao") ? raiz.get("duracao").getAsFloat() : fim;
		boolean loop = raiz.has("loop") && raiz.get("loop").getAsBoolean();
		boolean somar = raiz.has("somar") && raiz.get("somar").getAsBoolean();
		return new AnimCustom.Anim(nome, Math.max(dur, 0.05f), loop, somar, ossos, AnimCustom.apoios(raiz.get("apoios")));
	}

	private static AnimCustom.Canal canal(TreeMap<Float, float[]> q, boolean suave) {
		if (q == null || q.isEmpty()) return null;
		int n = q.size(), i = 0;
		float[] t = new float[n];
		float[][] v = new float[n][];
		int[] modo = new int[n];
		for (var e : q.entrySet()) { t[i] = e.getKey(); v[i] = e.getValue(); modo[i] = suave ? 1 : 0; i++; }
		return new AnimCustom.Canal(t, v, v, modo);
	}
}

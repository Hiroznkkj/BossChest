package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.Lugares;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Acerta a pose no mundo de verdade, depois que a animação (do código, do Blockbench ou do Emotecraft) e as
 * camadas já disseram o que o corpo quer fazer. Em vez de um "se encostar faz isso, se deitar faz aquilo",
 * a pose pede apoios (pés no chão, quadril no assento, costas na parede, nuca no chão) e o resolvedor procura
 * no mundo a superfície de cada um, com um raio a partir do próprio corpo. Aí ele mexe o mínimo possível na pose
 * (raiz, inclinação, cabeça, braços e pernas) para:
 * - nenhuma parte entrar em bloco (a caixa de cada parte contra as caixas de colisão dos blocos);
 * - o braço não entrar na própria cabeça (acenar, coçar a cabeça): ele abre o ombro em vez de atravessar;
 * - cada apoio encostar na sua superfície (sem flutuar, sem afundar);
 * - não fugir da pose pedida nem dar salto de um quadro para o outro.
 * É um mínimos quadrados (Levenberg-Marquardt) com derivadas numéricas: cada coisa acima vira um "erro" em
 * pixels e ele procura a pose com a menor soma deles. Para um apoio novo, basta acrescentar em APOIOS.
 */
public final class Resolver {
	private Resolver() {}

	/**
	 * Um apoio: um ponto de um osso (pixels, no espaço dele), a direção (no osso) em que ele procura a superfície,
	 * até onde procura (blocos) e a folga que fica entre o ponto e a superfície (pixels).
	 */
	public record Apoio(String nome, int osso, float x, float y, float z, float dx, float dy, float dz, float alcance, float folga, float sola) {
		/** 'sola' > 0: a superfície de apoio tem esse meio tamanho (px) em x e z; quem encosta é o canto mais baixo dela. */
		public Apoio(String nome, int osso, float x, float y, float z, float dx, float dy, float dz, float alcance, float folga) {
			this(nome, osso, x, y, z, dx, dy, dz, alcance, folga, 0f);
		}
	}

	public static final Apoio PE_D = new Apoio("pe_d", Esqueleto.PERNA_D, 0, 12, 0, 0, 1, 0, 0.3f, 0f, 1.5f);
	public static final Apoio PE_E = new Apoio("pe_e", Esqueleto.PERNA_E, 0, 12, 0, 0, 1, 0, 0.3f, 0f, 1.5f);
	/** Meio das costas, procurando para trás. */
	public static final Apoio COSTAS = new Apoio("costas", Esqueleto.TRONCO, 0, 6, 2, 0, 0, 1, 0.45f, 0f);
	/** Atrás da cabeça. */
	public static final Apoio NUCA = new Apoio("nuca", Esqueleto.CABECA, 0, -4, 4, 0, 0, 1, 0.35f, 0f);
	/** Embaixo do quadril; a folga é a grossura da coxa, que fica por cima do assento. */
	public static final Apoio QUADRIL = new Apoio("quadril", Esqueleto.TRONCO, 0, 12, 0, 0, 1, 0, 0.35f, 2f);
	public static final Apoio MAO_D = new Apoio("mao_d", Esqueleto.BRACO_D, -1, 10, 0, 0, 1, 0, 0.25f, 0f);
	public static final Apoio MAO_E = new Apoio("mao_e", Esqueleto.BRACO_E, 1, 10, 0, 0, 1, 0, 0.25f, 0f);
	public static final Map<String, Apoio> APOIOS = Map.of("pe_d", PE_D, "pe_e", PE_E, "pes", PE_D, "costas", COSTAS,
			"nuca", NUCA, "quadril", QUADRIL, "mao_d", MAO_D, "mao_e", MAO_E);

	// ---------- graus de liberdade: o que o resolvedor pode mexer ----------
	private static final int N = 19;
	/** Peso de fugir da pose pedida e de mudar desde o quadro anterior (erro em "pixels" por unidade). */
	private static final float[] FUGIR = new float[N], PULAR = new float[N], LIMITE = new float[N], PASSO = new float[N];
	static {
		for (int i = 0; i < N; i++) {
			boolean pos = i < 3, raiz = i < 5, cabeca = i < 7;
			FUGIR[i] = pos ? 0.35f : raiz ? 6f : cabeca ? 3f : 2.5f;
			PULAR[i] = pos ? 1f : raiz ? 10f : 6f;
			LIMITE[i] = pos ? 10f : raiz ? 0.8f : 1.5f;
			PASSO[i] = pos ? 0.1f : 0.01f;
		}
	}
	/** Passos por quadro (o resultado de um quadro começa o próximo, então ele continua melhorando). */
	private static final int ITERACOES = 4;
	private static final float ENTRAR = 16f; // peso de cada pixel dentro de um bloco
	private static final float APOIAR = 3f; // peso de cada pixel longe do apoio
	private static final float ENCOLHE = 0.5f; // encostar não é atravessar (pixels de folga em cada parte)
	private static final float CORPO = 12f; // peso de cada pixel de braço dentro da cabeça
	private static final float FOLGA_CORPO = 1.5f; // a mão encosta na camada 3D de fora (cabelo 0,8 px + manga 0,6 px) sem afundar
	private static final int[] BRACOS = {Esqueleto.BRACO_D, Esqueleto.BRACO_E};

	private static float get(Esqueleto e, int i) {
		ModelPart p = parte(e, i);
		return switch (campo(i)) { case 0 -> p.x; case 1 -> p.y; case 2 -> p.z; case 3 -> p.xRot; case 4 -> p.yRot; default -> p.zRot; };
	}

	private static void set(Esqueleto e, int i, float v) {
		ModelPart p = parte(e, i);
		switch (campo(i)) { case 0 -> p.x = v; case 1 -> p.y = v; case 2 -> p.z = v; case 3 -> p.xRot = v; case 4 -> p.yRot = v; default -> p.zRot = v; }
	}

	/** 0-2 raiz x,y,z; 3-4 inclinação da raiz (frente, lado); 5-6 cabeça (cima, lado); 7-18 braços e pernas (x,y,z). */
	private static ModelPart parte(Esqueleto e, int i) {
		if (i < 5) return e.root;
		if (i < 7) return e.parte[Esqueleto.CABECA];
		return e.parte[Esqueleto.BRACO_D + (i - 7) / 3];
	}

	private static int campo(int i) {
		if (i < 3) return i;
		if (i == 3) return 3;
		if (i == 4) return 5;
		if (i == 5) return 3;
		if (i == 6) return 4;
		return 3 + (i - 7) % 3;
	}

	// ---------- quais apoios a pose pede (uma tabela, não um if por ação) ----------

	/** Os apoios que a pose atual pede, com o peso de cada um. */
	public static List<Map.Entry<Apoio, Float>> pedidos(DadosMarcio d) {
		List<Map.Entry<Apoio, Float>> l = new ArrayList<>();
		float s = 1f - d.pe; // 1 sentado
		float parado = Mth.clamp(1f - d.andar * 3f, 0f, 1f);
		switch (d.assento) {
			case Lugares.ENCOSTO -> { add(l, COSTAS, 1f); add(l, PE_D, 1f); add(l, PE_E, 1f); }
			case Lugares.DEITADO -> { add(l, COSTAS, s); add(l, NUCA, s); }
			case Lugares.CHAO -> add(l, QUADRIL, s);
			case Lugares.CADEIRA -> { add(l, QUADRIL, s); add(l, PE_D, s); add(l, PE_E, s); }
			default -> { if (!d.sentado && d.trans == MarcioCliente.SEM && d.nado < 0.1f) { add(l, PE_D, parado); add(l, PE_E, parado); } }
		}
		// animação customizada tocando: os apoios que ela pede ("apoios" no arquivo)
		if (d.teste != null && d.teste.apoios() != null)
			for (var e : d.teste.apoios().entrySet()) {
				Apoio a = APOIOS.get(e.getKey());
				if (a == PE_D && e.getKey().equals("pes")) add(l, PE_E, e.getValue());
				if (a != null) add(l, a, e.getValue());
			}
		return l;
	}

	private static void add(List<Map.Entry<Apoio, Float>> l, Apoio a, float w) { if (w > 0.01f) l.add(Map.entry(a, w)); }

	// ---------- o resolvedor ----------

	/** Uma superfície achada para um apoio: o ponto, a normal e o peso. */
	private record Alvo(Apoio a, Vec3 ponto, Vec3 normal, float w) {}

	private static final Int2ObjectMap<float[]> anterior = new Int2ObjectOpenHashMap<>(); // {deltas..., tempo}

	/** Resultado do último quadro de cada NPC (para o /marcioanim colisao). */
	public record Retrato(Colisao.Caixa[] caixas, double[] dentro, List<Vec3[]> apoios, List<AABB> mundo, double antes, double depois) {}
	public static final Int2ObjectMap<Retrato> retratos = new Int2ObjectOpenHashMap<>();
	public static boolean retratar;

	/** Acerta a pose (já pronta nas partes) no mundo de Colisao.mundo. Devolve quanto ficou dentro de blocos (px). */
	public static double resolver(Esqueleto e, DadosMarcio d) {
		Colisao.Mundo m = Colisao.mundo;
		if (m == null || d.nado > 0.05f) { anterior.remove(d.id); return 0; }
		float[] base = new float[N];
		for (int i = 0; i < N; i++) base[i] = get(e, i);
		float[] ant = anterior.get(d.id);
		if (ant != null && (d.agora - ant[N] > 40 || d.agora < ant[N])) ant = null; // voltou a aparecer: sem memória
		boolean assentado = ant != null && ant[N + 1] > 0; // já tinha parado de melhorar: dois passos bastam
		float[] prev = ant == null ? new float[N] : java.util.Arrays.copyOf(ant, N);

		// o mundo em volta do corpo (com folga para os raios dos apoios)
		AABB regiao = null;
		for (int o = 0; o < Esqueleto.OSSOS; o++) {
			AABB b = e.caixa(d, o, 0).envolve();
			regiao = regiao == null ? b : regiao.minmax(b);
		}
		List<AABB> mundo = new ArrayList<>();
		m.caixas(regiao.inflate(0.7), mundo);

		// cada apoio pedido procura sua superfície a partir da pose pedida
		List<Alvo> alvos = new ArrayList<>();
		for (var en : pedidos(d)) {
			Apoio a = en.getKey();
			Vec3 p = e.ponto(d, a.osso(), a.x(), a.y(), a.z()), dir = e.direcao(d, a.osso(), a.dx(), a.dy(), a.dz());
			Colisao.Toque t = Colisao.raio(mundo, p.subtract(dir.scale(0.25)), dir, a.alcance() + 0.25);
			if (t != null) alvos.add(new Alvo(a, t.ponto().add(t.normal().scale(a.folga() / 16.0)), t.normal(), en.getValue()));
		}

		float[] delta = prev.clone();
		for (int i = 0; i < N; i++) delta[i] = Mth.clamp(delta[i], -LIMITE[i], LIMITE[i]);
		Cena cena = new Cena(e, d, mundo);
		double[] r = residuos(e, d, base, delta, prev, cena, alvos);
		double antes = dentro(e, d, mundo);
		boolean nada = antes == 0 && (d.assento == Lugares.DEITADO || autoDentro(e) == 0) && alvos.stream().allMatch(al -> Math.abs(erroApoio(e, d, al)) < 0.25);
		float maxAnt = 0;
		for (float v : prev) maxAnt = Math.max(maxAnt, Math.abs(v));
		if (nada && maxAnt < 1e-3f) { anterior.remove(d.id); retrato(e, d, mundo, alvos, 0, 0); return 0; } // a pose já está boa
		double custo = soma(r);
		float lambda = 1e-2f;
		double custo0 = custo;
		int its = assentado ? 2 : ITERACOES;
		for (int it = 0; it < its; it++) {
			int M = r.length;
			double[][] J = new double[M][N];
			for (int j = 0; j < N; j++) {
				float v0 = delta[j];
				delta[j] = v0 + PASSO[j];
				double[] rj = residuos(e, d, base, delta, prev, cena, alvos);
				delta[j] = v0;
				for (int k = 0; k < M; k++) J[k][j] = (rj[k] - r[k]) / PASSO[j];
			}
			double[][] A = new double[N][N];
			double[] g = new double[N];
			for (int k = 0; k < M; k++) for (int a = 0; a < N; a++) {
				if (J[k][a] == 0) continue;
				g[a] += J[k][a] * r[k];
				for (int b = 0; b < N; b++) A[a][b] += J[k][a] * J[k][b];
			}
			boolean melhorou = false;
			for (int tenta = 0; tenta < 8 && !melhorou; tenta++) {
				double[][] Al = new double[N][];
				for (int a = 0; a < N; a++) { Al[a] = A[a].clone(); Al[a][a] += lambda * (A[a][a] + 1e-3); }
				double[] passo = resolverLinear(Al, g);
				if (passo == null) break;
				float[] novo = delta.clone();
				for (int a = 0; a < N; a++) novo[a] = (float) Mth.clamp(novo[a] - passo[a], -LIMITE[a], LIMITE[a]);
				double[] rn = residuos(e, d, base, novo, prev, cena, alvos);
				double cn = soma(rn);
				if (cn < custo) { delta = novo; r = rn; custo = cn; lambda *= 0.3f; melhorou = true; }
				else lambda *= 5f;
			}
			if (!melhorou) break;
		}
		aplicar(e, base, delta);
		float[] guarda = new float[N + 2];
		System.arraycopy(delta, 0, guarda, 0, N);
		guarda[N] = d.agora;
		guarda[N + 1] = custo > custo0 * 0.97 ? 1 : 0;
		anterior.put(d.id, guarda);
		double depois = dentro(e, d, mundo);
		retrato(e, d, mundo, alvos, antes, depois);
		return depois * 16;
	}

	/** Esquece o quadro anterior (para começar do zero, por exemplo nos testes). */
	public static void esquecer(int id) { anterior.remove(id); }

	private static void aplicar(Esqueleto e, float[] base, float[] delta) {
		for (int i = 0; i < N; i++) set(e, i, base[i] + delta[i]);
	}

	/**
	 * O que não muda durante a resolução de um quadro: a conta do modelo para o mundo (perto da origem do NPC,
	 * para a conta em float não perder precisão), as caixas do mundo perto de cada osso e as quinas delas.
	 */
	private static final class Cena {
		final Matrix4f mundo; // pixels do modelo -> blocos, relativo a (ox, oy, oz)
		final double ox, oy, oz;
		final List<AABB>[] perto; // por osso, já relativas
		final float[][] quinas; // por osso: as arestas das caixas perto (x,y,z de uma ponta, x,y,z da outra)
		final float[][][] pontos;
		final int total;

		@SuppressWarnings("unchecked")
		Cena(Esqueleto e, DadosMarcio d, List<AABB> mundoTodo) {
			ox = d.mx; oy = d.my; oz = d.mz;
			float a = (180f - d.myaw) * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a);
			mundo = new Matrix4f(-c / 16f, 0, s / 16f, 0, 0, -1 / 16f, 0, 0, s / 16f, 0, c / 16f, 0, 0, 1.501f, 0, 1);
			perto = new List[Esqueleto.OSSOS];
			quinas = new float[Esqueleto.OSSOS][];
			pontos = new float[Esqueleto.OSSOS][][];
			int n = 0;
			for (int o = 0; o < Esqueleto.OSSOS; o++) {
				AABB env = e.caixa(d, o, 0).envolve().inflate(0.3); // folga para o quanto ele pode mexer num quadro
				perto[o] = new ArrayList<>();
				List<Float> q = new ArrayList<>();
				for (AABB b : mundoTodo) {
					if (!b.intersects(env)) continue;
					AABB r = b.move(-ox, -oy, -oz);
					perto[o].add(r);
					// as 12 arestas da caixa (de, até)
					for (int i = 0; i < 8; i++) for (int ax = 0; ax < 3; ax++) {
						if ((i & (1 << ax)) != 0) continue;
						int j = i | (1 << ax);
						for (int w : new int[]{i, j}) {
							q.add((float) ((w & 1) == 0 ? r.minX : r.maxX)); q.add((float) ((w & 2) == 0 ? r.minY : r.maxY)); q.add((float) ((w & 4) == 0 ? r.minZ : r.maxZ));
						}
					}
				}
				quinas[o] = new float[q.size()];
				for (int k = 0; k < q.size(); k++) quinas[o][k] = q.get(k);
				pontos[o] = Resolver.pontos(e, o);
				n += pontos[o].length + q.size() / 6;
			}
			total = n;
		}
	}

	/** Os erros (em pixels) da pose base + delta: fugir da pose, pular do quadro anterior, entrar em bloco, apoio longe. */
	private static double[] residuos(Esqueleto e, DadosMarcio d, float[] base, float[] delta, float[] prev, Cena c, List<Alvo> alvos) {
		aplicar(e, base, delta);
		double[] r = new double[2 * N + c.total + autoTotal(e) + alvos.size()];
		int k = 0;
		for (int i = 0; i < N; i++) r[k++] = FUGIR[i] * delta[i];
		for (int i = 0; i < N; i++) r[k++] = PULAR[i] * (delta[i] - prev[i]);
		Vector3f v = new Vector3f();
		for (int o = 0; o < Esqueleto.OSSOS; o++) {
			if (c.perto[o].isEmpty()) { k += c.pontos[o].length; continue; }
			Matrix4f m = new Matrix4f(c.mundo).mul(e.matriz(o));
			// pontos do osso dentro dos blocos
			for (float[] p : c.pontos[o]) {
				m.transformPosition(p[0], p[1], p[2], v);
				r[k++] = ENTRAR * 16 * Colisao.fundo(c.perto[o], v.x, v.y, v.z);
			}
			// arestas dos blocos entrando no osso (a beira da laje no meio da coxa, que nenhum ponto pega)
			Matrix4f inv = m.invert(new Matrix4f());
			float[] cubo = e.cubo[o], q = c.quinas[o];
			Vector3f w = new Vector3f();
			for (int i = 0; i < q.length; i += 6) {
				inv.transformPosition(q[i], q[i + 1], q[i + 2], v);
				inv.transformPosition(q[i + 3], q[i + 4], q[i + 5], w);
				r[k++] = ENTRAR * arestaDentro(v, w, cubo);
			}
		}
		// braço dentro da própria cabeça (deitado não confere: braços ao lado e a cabeça virando no travesseiro)
		Matrix4f cab = e.matriz(Esqueleto.CABECA).invert();
		for (int o : d.assento == Lugares.DEITADO ? new int[0] : BRACOS) {
			Matrix4f m = new Matrix4f(cab).mul(e.matriz(o));
			for (float[] p : pontosBraco(e, o)) {
				m.transformPosition(p[0], p[1], p[2], v);
				r[k++] = CORPO * naCabeca(e, v);
			}
		}
		if (d.assento == Lugares.DEITADO) k += autoTotal(e);
		for (Alvo a : alvos) r[k++] = APOIAR * a.w() * erroApoio(e, d, a);
		aplicar(e, base, new float[N]);
		return r;
	}

	/**
	 * Os pontos da metade de baixo do braço (antebraço e mão). O topo do ombro pode raspar na cabeça quando ela
	 * vira (é assim no modelo do Minecraft); o que não pode é a mão atravessar a cabeça.
	 */
	private static float[][] pontosBraco(Esqueleto e, int o) {
		float[] q = e.cubo[o];
		float meio = (q[1] + q[4]) / 2;
		return java.util.Arrays.stream(pontos(e, o)).filter(p -> p[1] >= meio).toArray(float[][]::new);
	}

	/** Quantos pontos de braço são conferidos contra a cabeça. */
	private static int autoTotal(Esqueleto e) {
		int n = 0;
		for (int o : BRACOS) n += pontosBraco(e, o).length;
		return n;
	}

	/** Quão fundo (px) um ponto (no espaço da cabeça) está dentro da cabeça, com a folga. 0 = fora. */
	private static float naCabeca(Esqueleto e, Vector3f p) {
		float[] q = e.cubo[Esqueleto.CABECA];
		float f = Float.MAX_VALUE;
		for (int k = 0; k < 3; k++) {
			float mn = q[k] - FOLGA_CORPO, mx = q[k + 3] + FOLGA_CORPO, x = p.get(k);
			if (x <= mn || x >= mx) return 0;
			f = Math.min(f, Math.min(x - mn, mx - x));
		}
		return f;
	}

	/** Soma de quanto os braços entram na cabeça (px), na pose atual das partes. */
	static double autoDentro(Esqueleto e) {
		Matrix4f cab = e.matriz(Esqueleto.CABECA).invert();
		Vector3f v = new Vector3f();
		double s = 0;
		for (int o : BRACOS) {
			Matrix4f m = new Matrix4f(cab).mul(e.matriz(o));
			for (float[] p : pontosBraco(e, o)) { m.transformPosition(p[0], p[1], p[2], v); s += naCabeca(e, v); }
		}
		return s;
	}

	/** Quão fundo (px) a aresta a-b entra no cubo (encolhido): o meio do trecho dela que fica dentro. */
	private static float arestaDentro(Vector3f a, Vector3f b, float[] cubo) {
		float t0 = 0, t1 = 1;
		for (int k = 0; k < 3; k++) {
			float o = a.get(k), dd = b.get(k) - o, mn = cubo[k] + ENCOLHE, mx = cubo[k + 3] - ENCOLHE;
			if (Math.abs(dd) < 1e-6f) { if (o <= mn || o >= mx) return 0; continue; }
			float u = (mn - o) / dd, x = (mx - o) / dd;
			t0 = Math.max(t0, Math.min(u, x));
			t1 = Math.min(t1, Math.max(u, x));
			if (t0 >= t1) return 0;
		}
		float t = (t0 + t1) / 2, f = Float.MAX_VALUE;
		for (int k = 0; k < 3; k++) {
			float mn = cubo[k] + ENCOLHE, mx = cubo[k + 3] - ENCOLHE;
			if (Math.min(a.get(k), b.get(k)) <= mn && Math.max(a.get(k), b.get(k)) >= mx) continue; // a aresta atravessa nesse eixo: sair por ele não adianta
			float p = a.get(k) + (b.get(k) - a.get(k)) * t;
			f = Math.min(f, Math.min(p - mn, mx - p));
		}
		return f == Float.MAX_VALUE ? 0 : Math.max(0, f);
	}

	private static final java.util.Map<Esqueleto, float[][][]> PONTOS = new java.util.WeakHashMap<>();

	private static float[][] pontos(Esqueleto e, int o) {
		return PONTOS.computeIfAbsent(e, x -> {
			float[][][] t = new float[Esqueleto.OSSOS][][];
			for (int i = 0; i < Esqueleto.OSSOS; i++) t[i] = x.pontos(i, ENCOLHE);
			return t;
		})[o];
	}

	/** Distância do ponto do apoio até a superfície dele (pixels; positivo = flutuando, negativo = afundando). */
	private static double erroApoio(Esqueleto e, DadosMarcio d, Alvo a) {
		Apoio ap = a.a();
		double menor = Double.MAX_VALUE;
		float r = ap.sola();
		for (int i = 0; i < (r > 0 ? 5 : 1); i++) {
			float x = ap.x() + (i == 0 ? 0 : (i & 1) == 0 ? -r : r), z = ap.z() + (i == 0 ? 0 : i < 3 ? -r : r);
			Vec3 p = e.ponto(d, ap.osso(), x, ap.y(), z);
			menor = Math.min(menor, p.subtract(a.ponto()).dot(a.normal()) * 16);
		}
		return menor;
	}

	/** Quanto o corpo todo está dentro de blocos (blocos). */
	private static double dentro(Esqueleto e, DadosMarcio d, List<AABB> mundo) {
		double s = 0;
		for (int o = 0; o < Esqueleto.OSSOS; o++) s += Colisao.entra(e.caixa(d, o, ENCOLHE), mundo);
		return s;
	}

	private static double soma(double[] r) { double s = 0; for (double v : r) s += v * v; return s; }

	/** Eliminação de Gauss com pivô (sistema pequeno, 19x19). */
	private static double[] resolverLinear(double[][] A, double[] b) {
		int n = b.length;
		double[][] M = new double[n][n + 1];
		for (int i = 0; i < n; i++) { System.arraycopy(A[i], 0, M[i], 0, n); M[i][n] = b[i]; }
		for (int c = 0; c < n; c++) {
			int p = c;
			for (int i = c + 1; i < n; i++) if (Math.abs(M[i][c]) > Math.abs(M[p][c])) p = i;
			if (Math.abs(M[p][c]) < 1e-12) return null;
			double[] t = M[c]; M[c] = M[p]; M[p] = t;
			for (int i = c + 1; i < n; i++) {
				double f = M[i][c] / M[c][c];
				if (f != 0) for (int j = c; j <= n; j++) M[i][j] -= f * M[c][j];
			}
		}
		double[] x = new double[n];
		for (int i = n - 1; i >= 0; i--) {
			double s = M[i][n];
			for (int j = i + 1; j < n; j++) s -= M[i][j] * x[j];
			x[i] = s / M[i][i];
		}
		return x;
	}

	private static void retrato(Esqueleto e, DadosMarcio d, List<AABB> mundo, List<Alvo> alvos, double antes, double depois) {
		if (!retratar) return;
		Colisao.Caixa[] cx = new Colisao.Caixa[Esqueleto.OSSOS];
		double[] dentro = new double[Esqueleto.OSSOS];
		for (int o = 0; o < Esqueleto.OSSOS; o++) { cx[o] = e.caixa(d, o, 0); dentro[o] = Colisao.entra(e.caixa(d, o, ENCOLHE), mundo); }
		List<Vec3[]> ap = new ArrayList<>();
		for (Alvo a : alvos) ap.add(new Vec3[]{e.ponto(d, a.a().osso(), a.a().x(), a.a().y(), a.a().z()), a.ponto()});
		retratos.put(d.id, new Retrato(cx, dentro, ap, mundo, antes * 16, depois * 16));
	}
}

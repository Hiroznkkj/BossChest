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
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import br.hirozn.bauchefe.Assento;
import br.hirozn.bauchefe.Lugares;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Estado do Márcio no cliente: falas e animações recebidas do servidor, piscadas e as transições entre ações. */
public final class MarcioCliente {
	public static final int DUR_FALA = 80;
	/** Transições: nenhuma, pulo qualquer (teste, cama), subir no barco da terra, subir da água, descer do barco. */
	public static final int SEM = 0, PULO = 1, SUBIR_TERRA = 2, SUBIR_AGUA = 3, DESCER = 4, SENTAR = 5, LEVANTAR = 6;

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
	private record Reac(float t0, Vec3 onde, int alvo) {}
	private static final Int2ObjectMap<Reac[]> reacoes = new Int2ObjectOpenHashMap<>(); // por tipo (Pacotes.Reacao)
	private static final Int2ObjectMap<Trajeto> trajetos = new Int2ObjectOpenHashMap<>();
	// onde ele estava montado no último quadro: {id do veículo, x,y,z (no barco: espaço do barco), yaw, tipo, -, 1 barco / 2 assento}
	private static final Int2ObjectMap<double[]> montadoEm = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<float[]> giro = new Int2ObjectOpenHashMap<>(); // {giro do corpo suave, último tempo}
	private static final Int2ObjectMap<float[]> beiradas = new Int2ObjectOpenHashMap<>(); // {início, pode de novo em, tinha buraco}

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

	/** Reação procedural mandada pelo servidor (susto, hi five, livro). */
	public static void reacao(Pacotes.Reacao r) {
		if (r.tipo() <= 0 || r.tipo() > Pacotes.Reacao.PARA) return;
		Reac[] rs = reacoes.computeIfAbsent(r.villager(), k -> new Reac[Pacotes.Reacao.PARA + 1]);
		if (r.tipo() == Pacotes.Reacao.PARA) { if (r.alvo() > 0 && r.alvo() < rs.length) rs[r.alvo()] = null; return; } // terminou (medir, aquecer, conversa)
		rs[r.tipo()] = new Reac(agora(), new Vec3(r.x(), r.y(), r.z()), r.alvo());
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
		boolean nadando = v.isInWater() && !v.isPassenger();
		// subindo no barco da água: continua nadando até a borda e vai endireitando enquanto se puxa
		// (antes levantava de uma vez ao montar e o corpo deitado "pulava" quase um bloco para o lado)
		float alvo = nadando ? 1f : d.trans == SUBIR_AGUA ? (d.transFase == Trajeto.ANDA ? 1f : d.transFase == Trajeto.PASSA ? Math.max(0f, 1f - d.transV) : 0f) : 0f;
		float dt = Mth.clamp(t - n[1], 0f, 5f);
		n[0] = alvo > n[0] ? Math.min(alvo, n[0] + dt / 8f) : Math.max(alvo, n[0] - dt / 16f);
		n[1] = t;
		if (nadando) n[2] = Mth.lerp(Math.min(1f, dt * 0.3f), n[2], (float) v.getFluidHeight(FluidTags.WATER)); // linha d'água nos pés
		d.nado = AnimPesca.inOutSine(n[0]);
		d.agua = n[2];
		alvos(v, st, t, partial, d);
		Ik.solido = Ik.doMundo(v.level()); // para o Corpo.livrar não deixar braço e perna dentro dos blocos
	}

	/**
	 * Sentar, levantar e trocar de lugar sem teleporte. Subir e descer do barco e sentar e levantar de um assento
	 * seguem o Trajeto (calculado com a posição e o giro de verdade do barco e do assento, a cada quadro). Qualquer
	 * outra mudança de lugar de uma vez (cama, testes) vira um pulinho do lugar antigo até o novo. O corpo nunca
	 * vira de uma vez: no máximo 15° por tick, seguindo o rumo do caminho.
	 */
	private static void transicao(Villager v, LivingEntityRenderState st, float t, float partial, DadosMarcio d) {
		int id = v.getId();
		Entity veiculo = v.getVehicle();
		boolean montado = veiculo != null;
		Vec3 pos = v.getPosition(partial);
		double[] sl = saltos.get(id); // {x,y,z,t do quadro anterior, desvio x,y,z, início, duração, -, corpo x,y,z}
		if (sl == null) saltos.put(id, sl = new double[]{pos.x, pos.y, pos.z, t, 0, 0, 0, -1000, 1, 0, pos.x, pos.y, pos.z});
		boolean visto = t - sl[3] < 3; // estava sendo desenhado até agora (senão a troca aconteceu fora da tela)
		float[] a0 = assento.computeIfAbsent(id, k -> new float[]{montado ? 1f : 0f, -1000f, -1000f});
		boolean mudou = (a0[0] > 0.5f) != montado;
		if (mudou) { a0[0] = montado ? 1f : 0f; a0[1] = visto ? t : -1000f; }
		if (v.isInWater() && !montado) a0[2] = t;
		Vec3 corpoAntes = visto ? new Vec3(sl[10], sl[11], sl[12]) : pos;

		Trajeto tr = trajetos.get(id);
		if (tr != null && t - tr.inicio > tr.duracao()) { trajetos.remove(id); tr = null; }
		double[] m = montadoEm.get(id);
		if (mudou && visto) {
			tr = null;
			if (veiculo instanceof AbstractBoat b) tr = Trajeto.subir(t - a0[2] < 10, t, corpoAntes, b, Trajeto.local(b, partial, pos), partial);
			else if (veiculo instanceof Assento as) tr = Trajeto.sentar(t, corpoAntes, as.position(), as.getYRot(), as.tipo());
			else if (veiculo == null && m != null) {
				if (m[7] == 1 && v.level().getEntity((int) m[0]) instanceof AbstractBoat b) tr = Trajeto.descer(t, pos, b, new Vec3(m[1], m[2], m[3]), partial);
				else if (m[7] == 2) tr = Trajeto.levantar(t, pos, new Vec3(m[1], m[2], m[3]), (float) m[4], (int) m[5]);
			}
			if (tr != null) { trajetos.put(id, tr); sl[7] = -1000; }
		}
		// lembra onde está montado (para o caminho de descer/levantar no quadro em que ele sai)
		if (veiculo instanceof AbstractBoat b) { Vec3 l = Trajeto.local(b, partial, pos); montadoEm.put(id, new double[]{b.getId(), l.x, l.y, l.z, 0, 0, 0, 1}); }
		else if (veiculo instanceof Assento as) montadoEm.put(id, new double[]{as.getId(), as.getX(), as.getY(), as.getZ(), as.getYRot(), as.tipo(), 0, 2});
		else if (veiculo == null) montadoEm.remove(id);

		Vec3 corpo = null;
		AbstractBoat barcoTr = tr != null && tr.veiculo >= 0 && v.level().getEntity(tr.veiculo) instanceof AbstractBoat bb ? bb : null;
		if (tr != null) {
			corpo = tr.ponto(t, pos, barcoTr, partial);
			if (corpo == null) { trajetos.remove(id); tr = null; } // o barco sumiu: vira um pulo comum
		}
		// pulo: mudou de lugar de uma vez sem trajeto (cama, testes): sai de onde o corpo estava, num arco
		if (tr == null && visto && pos.distanceToSqr(sl[0], sl[1], sl[2]) > 0.9 * 0.9 && corpoAntes.distanceToSqr(pos) > 0.01) {
			sl[4] = corpoAntes.x - pos.x; sl[5] = corpoAntes.y - pos.y; sl[6] = corpoAntes.z - pos.z;
			sl[7] = t;
			sl[8] = Math.min(20, 12 + 3 * Math.sqrt(sl[4] * sl[4] + sl[6] * sl[6]));
		}
		float u = (float) ((t - sl[7]) / sl[8]);
		if (tr == null) {
			double[] o = u < 1 ? pulo(u, sl[4], sl[5], sl[6]) : new double[3];
			corpo = pos.add(o[0], o[1], o[2]);
		}
		d.saltoX = corpo.x - pos.x; d.saltoY = corpo.y - pos.y; d.saltoZ = corpo.z - pos.z;
		sl[0] = pos.x; sl[1] = pos.y; sl[2] = pos.z; sl[3] = t;
		sl[10] = corpo.x; sl[11] = corpo.y; sl[12] = corpo.z;

		if (tr != null) {
			float[] f = tr.fase(t);
			d.trans = tr.tipo; d.transFase = (int) f[0]; d.transV = f[1]; d.transPassos = tr.passos; d.transU = (t - tr.inicio) / tr.duracao();
			d.transDist = tr.passos / 2.2f;
		} else if (u < 1) {
			d.trans = PULO; d.transFase = Trajeto.ANDA; d.transV = u; d.transU = u;
			d.transDist = (float) Math.sqrt(sl[4] * sl[4] + sl[6] * sl[6]);
			d.transPassos = Math.max(1, Math.round(d.transDist * 2.2f));
		} else { d.trans = SEM; d.transU = 1f; }

		// giro do corpo: segue o rumo do caminho e nunca vira de uma vez
		float alvoYaw = st.bodyRot;
		if (tr != null) { float r = tr.rumo(t, pos, barcoTr, partial); if (!Float.isNaN(r)) alvoYaw = r; }
		float[] g = giro.computeIfAbsent(id, k -> new float[]{st.bodyRot, t});
		float dtg = Mth.clamp(t - g[1], 0f, 5f);
		g[1] = t;
		if (!visto) g[0] = alvoYaw;
		g[0] += Mth.clamp(Mth.wrapDegrees(alvoYaw - g[0]), -15f * dtg, 15f * dtg);
		float giroAplicado = Mth.wrapDegrees(g[0] - st.bodyRot);
		st.bodyRot = g[0];
		st.yRot = Mth.clamp(st.yRot - giroAplicado, -75f, 75f); // a cabeça continua olhando para o mesmo lugar

		// de pé (1) ou sentado (0)
		boolean encosto = veiculo instanceof Assento as && as.tipo() == Lugares.ENCOSTO;
		float peTroca = tr != null ? tr.pe(t)
				: montado ? 1f - AnimPesca.inOutSine((t - a0[1]) / 14f) : AnimPesca.inOutSine((t - a0[1]) / 14f);
		if (encosto && tr == null) peTroca = 1f;
		float pe = AnimPesca.peLuta(d.fase, d.tempo, d.lutou);
		if (d.anim == AnimMarcio.CARINHO && d.animVar == 2) pe = Math.max(pe, 0.35f * AnimMarcio.peso(d.anim, d.animTempo)); // meio de pé para alcançar o Pinto
		d.pe = montado ? Math.max(peTroca, pe) : peTroca;
		d.sentado = (montado && !encosto) || d.pe < 1f;

		// assento (cadeira, chão, encosto): tipo e altura até o chão onde os pés ficam
		d.assento = 0;
		Vec3 a = null;
		float aYaw = 0;
		if (veiculo instanceof Assento as) { d.assento = as.tipo(); a = as.position(); aYaw = as.getYRot(); }
		else if (tr != null && (tr.tipo == SENTAR || tr.tipo == LEVANTAR)) { d.assento = tr.assentoTipo; a = tr.assento; aYaw = tr.assentoYaw; }
		d.assentoPos = a;
		d.assentoYaw = aYaw;
		if (a != null) {
			float r = aYaw * Mth.DEG_TO_RAD;
			float chao = d.assento == Lugares.CHAO || d.assento == Lugares.ENCOSTO ? 0f
					: chao(v.level(), new Vec3(a.x - Mth.sin(r) * 0.55, a.y, a.z + Mth.cos(r) * 0.55), a.y, 1.5);
			d.assentoAltura = Float.isNaN(chao) ? 0.5f : -chao;
		}
	}

	/** Pulo comum (lugar antigo - novo): o desvio que falta em 'u' (0..1), num arco. */
	private static double[] pulo(float u, double dx, double dy, double dz) {
		float h = io(u);
		double arco = Math.min(0.6, Math.sqrt(dx * dx + dz * dz) * 0.25) * Math.sin(Math.PI * u);
		return new double[]{dx * (1 - h), dy * (1 - h) + arco, dz * (1 - h)};
	}

	/**
	 * Os alvos de verdade para a pose procedural: borda do barco, cabeça do Pinto, chão embaixo de cada pé,
	 * beirada à frente, de onde veio o susto e a pancada, mão de quem bate o hi five, livro.
	 */
	private static void alvos(Villager v, LivingEntityRenderState st, float t, float partial, DadosMarcio d) {
		int id = v.getId();
		Vec3 pos = v.getPosition(partial);
		d.mx = pos.x + d.saltoX; d.my = pos.y + d.saltoY; d.mz = pos.z + d.saltoZ;
		d.myaw = st.bodyRot;
		Entity veiculo = v.getVehicle();
		Trajeto tr = trajetos.get(id);
		AbstractBoat barco = veiculo instanceof AbstractBoat b ? b
				: tr != null && tr.veiculo >= 0 && v.level().getEntity(tr.veiculo) instanceof AbstractBoat b2 ? b2 : null;
		d.bordaEsq = null;
		if (barco != null) {
			Trajeto.medir(barco);
			d.bordaEsq = tr != null && d.transFase == Trajeto.PASSA ? tr.bordaDaPassagem(pos, barco, partial)
					: Trajeto.mundo(barco, partial, new Vec3(Trajeto.LADO - 0.05, Trajeto.BORDA, 0.25)); // borda à esquerda dele
		}
		d.pinto = null;
		if (veiculo != null)
			for (Entity e : veiculo.getPassengers())
				if (Pescador.ehPinto(e) && e instanceof net.minecraft.world.entity.LivingEntity c) {
					float yb = Mth.rotLerp(partial, c.yBodyRotO, c.yBodyRot) * Mth.DEG_TO_RAD;
					// cabeça: perto do topo e da frente da galinha, pelo tamanho dela
					d.pinto = c.getPosition(partial).add(-Mth.sin(yb) * c.getBbWidth() * 0.45, c.getBbHeight() * 0.88, Mth.cos(yb) * c.getBbWidth() * 0.45);
				}

		// pés: altura do chão embaixo de cada um (em pé, em terra, fora de transição)
		d.chaoD = d.chaoE = Float.NaN;
		boolean emPe = veiculo == null && d.trans == SEM && d.nado < 0.1f;
		if (emPe) {
			d.chaoD = chao(v.level(), Ik.mundo(d, -1.9f, 24f, 0f), pos.y, 1.2);
			d.chaoE = chao(v.level(), Ik.mundo(d, 1.9f, 24f, 0f), pos.y, 1.2);
		}
		// beirada: quando o pé da frente chega num buraco (ou na água) de repente, se equilibra por 2 s
		float[] bd = beiradas.computeIfAbsent(id, k -> new float[]{-1000f, 0f, 0f});
		boolean buraco = false;
		if (emPe && v.onGround()) {
			float r = st.bodyRot * Mth.DEG_TO_RAD;
			buraco = Float.isNaN(chao(v.level(), new Vec3(pos.x - Mth.sin(r) * 0.5, pos.y, pos.z + Mth.cos(r) * 0.5), pos.y, 1.3));
		}
		if (buraco && bd[2] == 0 && t > bd[1]) { bd[0] = t; bd[1] = t + 300; }
		bd[2] = buraco ? 1 : 0;
		float eb = t - bd[0];
		d.beirada = eb < 40 ? io(Math.min(eb / 4f, (40 - eb) / 14f)) : 0f;

		// reações vindas do servidor
		Reac[] rs = reacoes.get(id);
		d.sustoT = d.ofereceT = d.bateT = d.livroT = d.guardaT = 999f;
		d.susto = d.maoDe = null;
		if (rs != null) {
			Reac r;
			if ((r = rs[Pacotes.Reacao.SUSTO]) != null) { d.sustoT = t - r.t0(); d.susto = r.onde(); }
			if ((r = rs[Pacotes.Reacao.LIVRO]) != null) d.livroT = t - r.t0();
			if ((r = rs[Pacotes.Reacao.GUARDA]) != null) d.guardaT = t - r.t0();
			Reac of = rs[Pacotes.Reacao.OFERECE], ba = rs[Pacotes.Reacao.BATE];
			if (of != null) d.ofereceT = t - of.t0();
			if (ba != null) d.bateT = t - ba.t0();
			Reac ult = ba != null && (of == null || ba.t0() >= of.t0()) ? ba : of;
			if (ult != null) {
				Entity quem = ult.alvo() >= 0 ? v.level().getEntity(ult.alvo()) : null;
				d.maoDe = quem != null ? quem.getEyePosition(partial) : ult.onde();
			}
			if (ba != null && of != null && ba.t0() >= of.t0()) d.ofereceT = 999f; // já bateram: para de oferecer
		}
		// estados contínuos: medindo o quarto, esquentando na fogueira, conversando; calor e armadura nova
		d.medindo = false; d.aquecer = d.calor = d.armaduraPes = d.armaduraCabeca = d.conversaCom = null;
		d.calorT = d.armaduraT = 999f;
		if (rs != null) {
			Reac r;
			d.medindo = rs[Pacotes.Reacao.MEDIR] != null;
			if ((r = rs[Pacotes.Reacao.AQUECER]) != null) d.aquecer = r.onde();
			if ((r = rs[Pacotes.Reacao.CALOR]) != null) { d.calorT = t - r.t0(); d.calor = r.onde(); }
			if ((r = rs[Pacotes.Reacao.ARMADURA]) != null && v.level().getEntity(r.alvo()) instanceof Entity quem) {
				d.armaduraT = t - r.t0(); d.armaduraPes = quem.getPosition(partial); d.armaduraCabeca = quem.getEyePosition(partial);
			}
			if ((r = rs[Pacotes.Reacao.CONVERSA]) != null) {
				Entity quem = v.level().getEntity(r.alvo());
				d.conversaCom = quem != null ? quem.getEyePosition(partial) : r.onde();
			}
		}
		d.chuva = veiculo == null && v.level().isRainingAt(v.blockPosition().above());
		d.emChamas = v.isOnFire();
		d.livro = v.getMainHandItem().is(net.minecraft.world.item.Items.BOOK);
		d.andar = st.walkAnimationSpeed;
		// pancada: recua para o lado contrário de onde veio
		d.dorT = 999f;
		d.dorDe = null;
		if (v.hurtTime > 0) {
			d.dorT = 10 - v.hurtTime + partial;
			var src = v.getLastDamageSource();
			d.dorDe = src != null ? src.getSourcePosition() : null;
		}
	}

	/** Altura do chão embaixo do ponto (relativa a 'y', procurando até 'fundo' blocos para baixo), ou NaN se não tem. */
	static float chao(net.minecraft.world.level.Level level, Vec3 p, double y, double fundo) {
		int x = Mth.floor(p.x), z = Mth.floor(p.z);
		double lx = p.x - x, lz = p.z - z;
		for (int by = Mth.floor(y + 0.6); by >= Mth.floor(y - fundo); by--) {
			net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(x, by, z);
			var st = level.getBlockState(bp);
			double topo = Double.NaN;
			for (net.minecraft.world.phys.AABB b : st.getCollisionShape(level, bp).toAabbs())
				if (lx >= b.minX - 0.05 && lx <= b.maxX + 0.05 && lz >= b.minZ - 0.05 && lz <= b.maxZ + 0.05 && by + b.maxY <= y + 0.6)
					topo = Double.isNaN(topo) ? by + b.maxY : Math.max(topo, by + b.maxY);
			if (!Double.isNaN(topo)) return topo - y < -fundo ? Float.NaN : (float) (topo - y);
		}
		return Float.NaN;
	}

	private static float io(float u) { return AnimPesca.inOutSine(u); }
}

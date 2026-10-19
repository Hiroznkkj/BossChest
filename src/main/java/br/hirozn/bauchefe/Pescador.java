package br.hirozn.bauchefe;

import br.hirozn.bauchefe.net.Pacotes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Villagers com "pescador" no nome, dentro de um barco, passeiam pela água e pescam sozinhos.
 * Navegação no estilo da IA dos peixes: escolhe um ponto aleatório de água e vai até ele
 * por um caminho que só passa por água (busca em largura na superfície).
 * À noite atracam numa doca de madeira e dormem; de manhã voltam para o barco.
 * Também falam (balão), olham em volta, fazem animações à toa e correm atrás de quem pega o barco.
 */
public final class Pescador {
	private static final int LONGE_DE_CASA = 48, BUSCA_MAX = 900;
	private static final double VELOCIDADE = 0.07;

	private enum Modo { NAVEGANDO, INDO_DOCA, PARADO_NOITE, EM_TERRA, VOLTANDO, PERSEGUINDO, FUGINDO, BUSCANDO, INDO_SENTAR, SENTADO, MEDINDO, CONVERSANDO }
	/** Até onde ele procura o barco quando fica sem ele. */
	private static final int RAIO_BARCO = 25;

	/** Marca do barco do Márcio (tag da entidade) e do dono dele. */
	public static final String TAG_BARCO = "bauchefe.barco_do_marcio", TAG_DONO = "bauchefe.dono.";

	private static final class Estado {
		BlockPos casa;
		List<BlockPos> caminho = List.of();
		int idx, timer;
		Vec3 ultimaPos = Vec3.ZERO;
		int re, remouEm, travas; // ticks de ré para desencalhar; último tick em que remou; encalhes seguidos
		final Map<BlockPos, Integer> evitar = new HashMap<>(); // onde encalhou -> até quando evitar
		Modo modo = Modo.NAVEGANDO;
		// pesca
		Boia boia;
		int faseT;
		boolean fisgou, dificil;
		// noite
		UUID barco;
		BlockPos doca; // onde ele pisa na doca
		int voltando;
		// vida: falas, olhar, animações, barco roubado
		int proximaFala = 200, proximaAnim = 1200, anim, animFim, olharAte, proximaQueixa;
		Entity olharEnt;
		Vec3 olharPonto;
		final Map<UUID, Integer> cumprimentou = new HashMap<>();
		UUID ladrao;
		BlockPos barril; // posto de trabalho (marca a doca)
		int calma; // ticks sem monstro por perto, fugindo
		int esperar; // sem caminho nem lugar para pescar: espera um pouco antes de tentar de novo
		Vec3 ancora; // onde ele estava da última vez que andou de verdade (mais de 4 blocos)
		int ancoraT, puxando; // desde quando está ali; ticks puxando o barco afundado pela corda
		boolean sair; // preso no mesmo lugar (beco, canal estreito): não pesca até sair dali
		int deslizarAte; // depois de encalhar 2 vezes: empurra o barco com o remo pelo meio do caminho até lá
		// sentar: para onde vai, o modo para voltar depois, até quando fica, livro, papo com quem
		Modo antes;
		Lugares.Lugar lugar;
		int sentadoAte, duracaoSentado, livroEm, proximoPapo, sentouNoite = -100000;
		boolean lendo;
		UUID papoCom;
		int correrAte; // correndo de um raio: não mexe no caminho dele
		int hiFiveEm, hiFiveAte, doeuEm; // oferecer a mão (depois do oi), até quando oferece, última reclamação de pancada
		UUID hiFiveCom;
		// arredores: fogueira onde esquenta as mãos; medindo o quarto (as duas pontas e qual); conversa
		BlockPos fogoAquecer;
		BlockPos[] medir;
		int medirIdx;
		UUID conversaCom;
		int conversaAte, proximaVez;
		boolean vezDele, conversaComecou;
		final Map<UUID, String> armaduras = new HashMap<>(); // armadura de cada jogador da última vez que viu
		int armaduraEm, calorEm, fogoEm, chuvaEm; // até quando não reage de novo
		boolean bebado; // de vez em quando acorda bêbado: fala bobagem e rema torto o dia todo
		// /bauchefe marcio testar: resultado forçado da pesca, mordida rápida, fora do barco até, fila do "tudo"
		Boolean testeFisga, testeDificil;
		boolean testeRapido;
		int remontarEm, demoProx;
		ArrayDeque<String> demo;
	}

	private static final Map<UUID, Estado> estados = new HashMap<>();
	private static final List<ItemEntity> peixesVoando = new ArrayList<>();
	private static final int VOO_PEIXE = 12;
	private static int relogio;

	private Pescador() {}

	public static void registrar() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD) return;
			relogio++;
			peixesVoando.removeIf(e -> { if (e.tickCount > VOO_PEIXE || !e.isAlive()) { e.discard(); return true; } return false; });
			if (relogio % 100 == 0) procurar(level);
			if (relogio % 20 == 0) cuidarDoPinto(level);
			for (Iterator<Map.Entry<UUID, Estado>> it = estados.entrySet().iterator(); it.hasNext(); ) {
				var en = it.next();
				Estado s = en.getValue();
				Entity e = level.getEntity(en.getKey());
				if (!(e instanceof Villager v) || !ehPescador(v)) {
					if (relogio % 100 == 0) { largarBoia(s); it.remove(); }
					continue;
				}
				if (relogio % 100 == 0) { profissao(level, v); s.barril = barril(level, v); }
				vida(level, v, s);
				if (s.remontarEm > 0) { if (relogio >= s.remontarEm) remontar(level, v, s); continue; } // teste fora do barco
				if (s.demo != null) demo(level, v, s);
				if (s.modo == Modo.INDO_SENTAR || s.modo == Modo.SENTADO) { sentar(level, v, s); continue; }
				if (s.modo == Modo.MEDINDO) { medir(level, v, s); continue; }
				if (s.modo == Modo.CONVERSANDO) { conversar(level, v, s); continue; }
				if (s.modo == Modo.PERSEGUINDO) { perseguir(level, v, s); continue; }
				if (s.modo == Modo.BUSCANDO) { if (!buscar(level, v, s)) it.remove(); continue; }
				if (s.modo == Modo.EM_TERRA || s.modo == Modo.VOLTANDO) { if (!emTerra(level, v, s)) it.remove(); continue; }
				if (!(v.getVehicle() instanceof AbstractBoat barco)) {
					largarBoia(s);
					if (roubado(level, v, s)) continue;
					// caiu do barco ou o barco foi embora: vai buscar (de dia)
					if (!noite(level) && !v.isSleeping()) { s.modo = Modo.BUSCANDO; s.voltando = 0; falar(level, v, s, Falas.Tipo.BUSCA, null, false); continue; }
					if (relogio % 100 == 0) it.remove();
					continue;
				}
				s.barco = barco.getUUID();
				tick(level, v, barco, s);
			}
		});
	}

	/** A boia pertence a uma pesca em andamento? (boia órfã se apaga sozinha) */
	public static boolean boiaAtiva(Boia b) {
		LivingEntity dono = b.getDono();
		Estado s = dono == null ? null : estados.get(dono.getUUID());
		return s != null && s.boia == b;
	}

	public static boolean ehPescador(Entity e) {
		return e instanceof Villager && e.hasCustomName() && e.getCustomName().getString().toLowerCase(Locale.ROOT).contains("pescador");
	}

	public static boolean ehPinto(Entity e) {
		return e instanceof Chicken && e.hasCustomName() && e.getCustomName().getString().toLowerCase(Locale.ROOT).contains("pinto");
	}

	/** Barco com baú do pescador (marcado, ou com ele ou o Pinto dentro): aceita 2 e põe a galinha em cima do baú. */
	public static boolean barcoDoMarcio(AbstractBoat b) {
		if (!(b instanceof AbstractChestBoat)) return false;
		if (b.entityTags().contains(TAG_BARCO)) return true;
		for (Entity p : b.getPassengers()) if (ehPescador(p) || ehPinto(p)) return true;
		return false;
	}

	/**
	 * Quem pode entrar no barco do Márcio: ele (um só), o Pinto (uma galinha só) e jogador ("roubando").
	 * Enquanto ele dorme o barco fica com lugar vago e qualquer bicho que encostasse entrava.
	 */
	public static boolean podeEntrar(AbstractBoat b, Entity e) {
		if (e instanceof Player) return true;
		if (ehPescador(e)) return !b.hasPassenger(Pescador::ehPescador);
		if (ehPinto(e)) return !b.hasPassenger(p -> p instanceof Chicken);
		return false;
	}

	/** Tira do barco do Márcio quem não devia estar lá (outra galinha, bicho que entrou enquanto ele dormia). */
	private static void limparBarco(AbstractBoat b) {
		boolean pinto = false, marcio = false;
		for (Entity p : List.copyOf(b.getPassengers())) {
			boolean fica = p instanceof Player || (ehPescador(p) && !marcio) || (ehPinto(p) && !pinto);
			if (ehPescador(p)) marcio = true;
			if (ehPinto(p)) pinto = true;
			if (!fica) p.stopRiding();
		}
	}

	private static void guardarVara(Villager v) {
		if (v.getMainHandItem().is(Items.FISHING_ROD)) v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	static boolean noite(ServerLevel level) {
		long t = level.getOverworldClockTime() % 24000;
		return t >= 12500 || t < 100; // villagers vão dormir às 12000
	}

	private static void procurar(ServerLevel level) {
		boolean noite = noite(level);
		for (Villager v : level.getEntities(EntityTypes.VILLAGER, Pescador::ehPescador)) {
			if (estados.containsKey(v.getUUID())) continue;
			if (v.getVehicle() instanceof AbstractBoat) {
				Estado s = new Estado();
				s.casa = v.blockPosition();
				estados.put(v.getUUID(), s);
			} else if (!noite && !v.isPassenger()) {
				// em terra de dia (ex.: servidor reiniciou à noite): volta para um barco com baú vazio por perto
				AbstractChestBoat b = barcoDele(level, v, RAIO_BARCO);
				if (b == null) b = barcoLivre(level, v.position(), RAIO_BARCO);
				if (b == null) continue;
				Estado s = new Estado();
				s.casa = b.blockPosition();
				s.barco = b.getUUID();
				s.modo = Modo.VOLTANDO;
				estados.put(v.getUUID(), s);
				roubado(level, v, s);
			}
		}
	}

	/**
	 * Profissão de pescador no nível 5, com XP para não perder. O posto de trabalho do pescador é o barril:
	 * o barril que ele pegar (perto da água) marca a doca dele (ver planejarDoca).
	 */
	private static void profissao(ServerLevel level, Villager v) {
		VillagerData d = v.getVillagerData();
		if (!d.profession().is(VillagerProfession.FISHERMAN)) {
			// solta o posto antigo (ex.: composteira do farmer) para outro villager poder usar
			v.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(g -> { if (g.dimension() == level.dimension()) level.getPoiManager().release(g.pos()); });
			v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
			v.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		}
		if (!d.profession().is(VillagerProfession.FISHERMAN) || d.level() < 5)
			v.setVillagerData(d.withProfession(level.registryAccess(), VillagerProfession.FISHERMAN).withLevel(5));
		if (v.getVillagerXp() <= 0) v.setVillagerXp(1);
	}

	/**
	 * Barril que marca a doca: o barril de trabalho dele, se tiver; senão o barril mais perto (até 48 blocos)
	 * que tenha água a até 3 blocos. Barril é o posto do pescador, então o jogo já sabe onde estão (POI).
	 */
	private static BlockPos barril(ServerLevel level, Villager v) {
		BlockPos trabalho = v.getBrain().getMemory(MemoryModuleType.JOB_SITE)
				.filter(g -> g.dimension() == level.dimension()).map(g -> g.pos()).orElse(null);
		if (trabalho != null && level.getBlockState(trabalho).is(Blocks.BARREL)) return trabalho;
		return level.getPoiManager().findClosest(t -> t.is(PoiTypes.FISHERMAN), b -> naBeiraDaAgua(level, b),
				v.blockPosition(), 48, PoiManager.Occupancy.ANY).orElse(null);
	}

	private static boolean naBeiraDaAgua(Level level, BlockPos b) {
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 0; dy++)
			if (agua(level, b.offset(dx, dy, dz))) return true;
		return false;
	}

	private static void segurarVara(Villager v) {
		if (!v.getMainHandItem().is(Items.FISHING_ROD)) {
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
			v.setDropChance(EquipmentSlot.MAINHAND, 0f);
		}
	}

	// ---------- comportamento no barco ----------

	private static void tick(ServerLevel level, Villager v, AbstractBoat barco, Estado s) {
		RandomSource r = level.getRandom();
		segurarVara(v);
		if (relogio % 100 == 0) marcarBarco(barco, v);
		if (relogio % 20 == 0) limparBarco(barco);
		s.timer--;
		// monstro por perto: larga tudo e foge com o barco para a doca
		if (s.modo != Modo.FUGINDO && relogio % 10 == 0) {
			Entity m = monstro(level, barco, 12);
			if (m != null) { fugir(level, v, barco, s, m); return; }
		}
		if (s.modo == Modo.FUGINDO) { fugindo(level, v, barco, s, r); return; }
		if (s.boia != null) { pescar(level, v, barco, s, r); return; } // termina a pesca mesmo se anoitecer
		if (relogio < s.animFim) { animando(level, v, barco, s); return; }
		if (noite(level)) { noturno(level, v, barco, s); return; }
		if (s.modo != Modo.NAVEGANDO) { s.modo = Modo.NAVEGANDO; s.caminho = List.of(); s.idx = 0; }
		// preso no mesmo lugar (beco, canal estreito) por 1 min e meio: para de pescar e procura a saída
		Vec3 aqui = barco.position();
		if (s.ancora == null || aqui.distanceToSqr(s.ancora) > 4 * 4) { s.ancora = aqui; s.ancoraT = relogio; s.sair = false; }
		else if (!s.sair && relogio - s.ancoraT > 1800) { s.sair = true; s.caminho = List.of(); s.idx = 0; }

		if (s.idx >= s.caminho.size()) {
			if (relogio < s.esperar) { frear(barco, 0.6); olhar(level, v, s, r); return; }
			// parado entre um trecho e outro: às vezes faz uma animação à toa
			if (!s.sair && relogio >= s.proximaAnim && comecarAnim(level, v, barco, s, r, 0)) return;
			if (!s.sair && s.timer <= 0 && r.nextFloat() < 0.6f && comecarPesca(level, v, barco, s, r)) return;
			planejar(level, barco, s, r, null);
			if (s.caminho.isEmpty()) {
				// nenhuma água alcançável além de onde está: aí sim pesca dali mesmo;
				// se nem isso, espera 2 s e empurra o barco para o meio da água
				s.sair = false; s.ancoraT = relogio;
				if (comecarPesca(level, v, barco, s, r)) return;
				s.esperar = relogio + 40;
				centralizar(level, barco);
				return;
			}
		}
		if (remar(level, v, barco, s, r)) { s.caminho = List.of(); s.idx = 0; }
		olhar(level, v, s, r);
	}

	/**
	 * Rema seguindo o caminho: mira num ponto mais à frente (até 3,5 blocos, só se a linha até ele for toda
	 * água), diminui nas curvas fechadas e quando o barranco está logo à frente, desviando para o lado com
	 * mais água. Se ficar parado, dá ré um pouco. Devolve true quando precisa de um caminho novo.
	 */
	private static boolean remar(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		Vec3 pos = barco.position();
		if (relogio - s.remouEm > 1) s.ultimaPos = pos; // voltou a remar depois de parar: não é encalhe
		s.remouEm = relogio;
		// chegou perto do destino: pronto
		BlockPos fim = s.caminho.get(s.caminho.size() - 1);
		if (Math.pow(fim.getX() + 0.5 - pos.x, 2) + Math.pow(fim.getZ() + 0.5 - pos.z, 2) < 1.2) {
			s.idx = s.caminho.size();
			s.travas = 0;
			return false;
		}
		// se já está mais perto de um ponto adiante, pula para ele
		int perto = s.idx;
		double menor = Double.MAX_VALUE;
		for (int k = s.idx; k < Math.min(s.caminho.size(), s.idx + 6); k++) {
			BlockPos c = s.caminho.get(k);
			double d = Math.pow(c.getX() + 0.5 - pos.x, 2) + Math.pow(c.getZ() + 0.5 - pos.z, 2);
			if (d < menor) { menor = d; perto = k; }
		}
		s.idx = perto;
		// passa pelos pontos que já ficaram para trás; numa curva só passa perto do meio do bloco
		// (a caixa do barco não gira, então ele precisa estar centrado para caber no canal novo)
		while (s.idx < s.caminho.size() - 1) {
			BlockPos c = s.caminho.get(s.idx);
			double raio = curva(s.caminho, s.idx) ? 0.7 : 1.4;
			if (Math.pow(c.getX() + 0.5 - pos.x, 2) + Math.pow(c.getZ() + 0.5 - pos.z, 2) >= raio * raio) break;
			s.idx++;
		}
		// em canal de 1 bloco os raios laterais com a largura toda do barco batem sempre na margem
		BlockPos sob = partida(level, barco);
		double meia = sob != null && largura(level, sob) <= 4 ? 0.3 : 0.65;
		// canal de 1 bloco (o barco tem 1,4 de largura e entala nas margens) ou encalhado de novo:
		// ele empurra o barco com o remo, deslizando pelo meio do caminho, sem a física do barco
		BlockPos prox = s.caminho.get(s.idx);
		if (s.re == 0 && (meia < 0.5 || largura(level, prox) <= 4 || relogio < s.deslizarAte)) {
			deslizar(level, v, barco, s, prox, r);
			return false;
		}
		BlockPos alvo = s.caminho.get(s.idx);
		for (int k = s.idx + 1; k < s.caminho.size(); k++) {
			BlockPos q = s.caminho.get(k);
			if (Math.pow(q.getX() + 0.5 - pos.x, 2) + Math.pow(q.getZ() + 0.5 - pos.z, 2) > 3.5 * 3.5 || !retaDeAgua(level, pos, q, meia + 0.1)) break;
			alvo = q;
		}

		float yaw = barco.getYRot();
		double rad = yaw * Mth.DEG_TO_RAD;
		Vec3 frente = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
		if (s.re > 0) { // ré: sai do barranco e vira para o caminho
			s.re--;
			barco.setDeltaMovement(-frente.x * VELOCIDADE * 0.6, barco.getDeltaMovement().y, -frente.z * VELOCIDADE * 0.6);
			return s.re == 0;
		}

		double dx = alvo.getX() + 0.5 - pos.x, dz = alvo.getZ() + 0.5 - pos.z;
		float querido = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		if (s.bebado) querido += 35f * Mth.sin(relogio * 0.04f); // rema em zigue-zague
		// barranco logo à frente? desvia para o lado com mais água
		double vel = VELOCIDADE * (s.modo == Modo.FUGINDO ? 1.7 : 1.0);
		// raios: se o rumo desejado bate na margem logo ali, testa rumos vizinhos e pega o mais livre
		// (com folga para a largura do barco); e diminui quando a água à frente é curta
		double y = barco.getY();
		if (raio(level, pos, querido, 4, y, meia) < 2.5) {
			float melhor = 0;
			double nota = -1e9;
			for (int a = -75; a <= 75; a += 15) {
				double n = raio(level, pos, querido + a, 4, y, meia) - Math.abs(a) / 40.0;
				if (n > nota) { nota = n; melhor = a; }
			}
			querido += melhor;
		}
		vel *= Mth.clamp(raio(level, pos, yaw, 4, y, meia) / 2.5, 0.25, 1.0);
		float erro = Mth.wrapDegrees(querido - yaw);
		yaw += Mth.clamp(erro, -6f, 6f);
		vel *= Mth.clamp(1f - Math.abs(erro) / 70f, 0.15f, 1f); // curva fechada: vira quase parado
		barco.setYRot(yaw);
		barco.setYHeadRot(yaw);
		rad = yaw * Mth.DEG_TO_RAD;
		// empurrãozinho de lado de volta para o meio do caminho (o barco não consegue andar de lado sozinho)
		BlockPos meio = s.caminho.get(Math.max(0, s.idx - 1));
		double lx = meio.getX() + 0.5 - pos.x, lz = meio.getZ() + 0.5 - pos.z;
		double fx = -Mth.sin(rad), fz = Mth.cos(rad), ao = lx * fx + lz * fz;
		lx -= fx * ao; lz -= fz * ao; // só a parte de lado
		barco.setDeltaMovement(fx * vel + Mth.clamp(lx * 0.15, -0.03, 0.03), barco.getDeltaMovement().y, fz * vel + Mth.clamp(lz * 0.15, -0.03, 0.03));
		v.getLookControl().setLookAt(alvo.getX() + 0.5, barco.getY() + 0.6, alvo.getZ() + 0.5);
		if (relogio % 30 == 0) level.playSound(null, barco.getX(), barco.getY(), barco.getZ(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.4f, 0.9f + r.nextFloat() * 0.2f);

		// encalhou? dá ré e depois pede um caminho novo
		if (relogio % 30 == 0) {
			boolean preso = pos.distanceToSqr(s.ultimaPos) < 0.04;
			s.ultimaPos = pos;
			if (preso) encalhou(level, barco, s, frente);
		}
		return false;
	}

	/** Leva o barco até o meio do bloco 'c' direto pela posição (sem colisão com a margem), virando a proa para lá. */
	private static void deslizar(ServerLevel level, Villager v, AbstractBoat barco, Estado s, BlockPos c, RandomSource r) {
		Vec3 pos = barco.position();
		double dx = c.getX() + 0.5 - pos.x, dz = c.getZ() + 0.5 - pos.z, len = Math.sqrt(dx * dx + dz * dz);
		float yaw = barco.getYRot(), erro = Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - yaw);
		yaw += Mth.clamp(erro, -10f, 10f);
		barco.setYRot(yaw);
		barco.setYHeadRot(yaw);
		barco.setDeltaMovement(0, barco.getDeltaMovement().y, 0);
		// devagar, e quase parado enquanto a proa ainda está virando
		double passo = Math.min(len, VELOCIDADE * 0.8 * Mth.clamp(1f - Math.abs(erro) / 120f, 0.2f, 1f));
		if (len > 1e-3) barco.setPos(pos.x + dx / len * passo, barco.getY(), pos.z + dz / len * passo);
		s.ultimaPos = barco.position(); // não conta como encalhe
		v.getLookControl().setLookAt(c.getX() + 0.5, barco.getY() + 0.6, c.getZ() + 0.5);
		if (relogio % 30 == 0) level.playSound(null, barco.getX(), barco.getY(), barco.getZ(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.4f, 0.9f + r.nextFloat() * 0.2f);
	}

	/**
	 * Encalhou: marca os blocos logo à frente para o próximo caminho desviar deles por 1 min e dá ré.
	 * Se encalhar 3 vezes seguidas, empurra o barco de leve para o meio do bloco de água mais livre por perto.
	 */
	private static void encalhou(ServerLevel level, AbstractBoat barco, Estado s, Vec3 frente) {
		s.re = 15;
		s.travas++;
		Vec3 pos = barco.position();
		for (double dist : new double[]{1.0, 1.8}) {
			BlockPos b = BlockPos.containing(pos.x + frente.x * dist, barco.getY() - 0.3, pos.z + frente.z * dist);
			s.evitar.put(b, relogio + 1200);
		}
		for (int k = s.idx; k < Math.min(s.caminho.size(), s.idx + 2); k++) s.evitar.put(s.caminho.get(k), relogio + 1200);
		if (s.travas >= 2) s.deslizarAte = relogio + 15 + 60; // depois da ré, empurra com o remo por 3 s
		if (s.travas >= 3) {
			centralizar(level, barco);
			s.travas = 0;
		}
	}

	/** Pode passar por esse bloco de água (não está na lista de encalhes recentes)? */
	private static boolean livreDeEncalhe(Estado s, BlockPos p) {
		Integer ate = s.evitar.get(p);
		if (ate == null) return true;
		if (ate < relogio) { s.evitar.remove(p); return true; }
		return false;
	}

	/** O caminho muda de direção nesse ponto? */
	private static boolean curva(List<BlockPos> c, int i) {
		if (i <= 0 || i >= c.size() - 1) return false;
		BlockPos a = c.get(i - 1), b = c.get(i), d = c.get(i + 1);
		return (b.getX() - a.getX()) != (d.getX() - b.getX()) || (b.getZ() - a.getZ()) != (d.getZ() - b.getZ());
	}

	/** Quanto dá para andar em linha reta nesse rumo (graus, como o yaw) até bater na margem, até 'max' blocos. */
	private static double raio(Level level, Vec3 de, float rumo, double max, double y, double meia) {
		double rad = rumo * Mth.DEG_TO_RAD, fx = -Mth.sin(rad), fz = Mth.cos(rad), lx = -fz * meia, lz = fx * meia;
		for (double t = 0.4; t <= max; t += 0.25) {
			double x = de.x + fx * t, z = de.z + fz * t;
			if (!aguaEm(level, new Vec3(x, 0, z), y) || !aguaEm(level, new Vec3(x + lx, 0, z + lz), y)
					|| !aguaEm(level, new Vec3(x - lx, 0, z - lz), y)) return t;
		}
		return max;
	}

	/** Empurra o barco de leve para o bloco de água mais livre por perto. */
	private static void centralizar(ServerLevel level, AbstractBoat barco) {
		Vec3 pos = barco.position();
		BlockPos aqui = BlockPos.containing(pos.x, barco.getY() - 0.3, pos.z), melhor = null;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			BlockPos q = aqui.offset(dx, 0, dz);
			if (agua(level, q) && (melhor == null || largura(level, q) > largura(level, melhor))) melhor = q;
		}
		if (melhor == null) return;
		Vec3 c = Vec3.atBottomCenterOf(melhor);
		barco.setDeltaMovement((c.x - pos.x) * 0.2, barco.getDeltaMovement().y, (c.z - pos.z) * 0.2);
	}

	/** Bloco de água de onde partir: o de baixo do barco ou, se ele está na beirada, o mais perto. */
	private static BlockPos partida(Level level, AbstractBoat barco) {
		BlockPos ini = BlockPos.containing(barco.getX(), barco.getY() - 0.3, barco.getZ());
		if (agua(level, ini)) return ini;
		BlockPos melhor = null;
		double best = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			BlockPos q = ini.offset(dx, 0, dz);
			double d = Vec3.atBottomCenterOf(q).distanceToSqr(barco.getX(), q.getY(), barco.getZ());
			if (agua(level, q) && d < best) { best = d; melhor = q; }
		}
		return melhor;
	}

	/** Tem água (superfície) no bloco desse ponto, na altura do barco? */
	private static boolean aguaEm(Level level, Vec3 p, double y) {
		return agua(level, BlockPos.containing(p.x, y - 0.3, p.z));
	}

	/** A linha reta do barco até esse ponto passa só por água, com folga para a largura do barco? */
	private static boolean retaDeAgua(Level level, Vec3 de, BlockPos ate, double meia) {
		Vec3 para = Vec3.atBottomCenterOf(ate);
		double dx = para.x - de.x, dz = para.z - de.z, len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01) return true;
		double nx = -dz / len * meia, nz = dx / len * meia; // meia largura do barco com folga (menos em canal estreito)
		for (double t = 0; t <= len; t += 0.5) {
			double x = de.x + dx * t / len, z = de.z + dz * t / len;
			for (int lado = -1; lado <= 1; lado++)
				if (!agua(level, BlockPos.containing(x + nx * lado, ate.getY(), z + nz * lado))) return false;
		}
		return true;
	}

	private static void frear(AbstractBoat barco, double f) {
		Vec3 m = barco.getDeltaMovement();
		barco.setDeltaMovement(m.x * f, m.y, m.z * f);
	}

	// ---------- pesca ----------

	/** Joga a boia: procura água livre ao redor (prefere à direita e à frente). Devolve false se não achou. */
	private static boolean comecarPesca(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		double rad = barco.getYRot() * Mth.DEG_TO_RAD;
		Vec3 frente = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad)), dir = new Vec3(-frente.z, 0, frente.x);
		Vec3 alvo = null;
		Vec3[] direcoes = {dir.add(frente.scale(0.5)), frente, dir, frente.subtract(dir.scale(0.5)), dir.scale(-1),
				dir.subtract(frente.scale(0.5)), frente.scale(-1)};
		busca:
		for (double dist : new double[]{3.5, 2.5, 5.0})
			for (Vec3 d : direcoes) {
				Vec3 p = barco.position().add(d.normalize().scale(dist));
				BlockPos bp = BlockPos.containing(p.x, barco.getY() - 0.3, p.z);
				if (agua(level, bp)) { alvo = new Vec3(p.x, bp.getY() + level.getFluidState(bp).getHeight(level, bp) - 0.05, p.z); break busca; }
			}
		if (alvo == null) { s.timer = 200; return false; }
		Boia b = new Boia(BauChefe.BOIA, level);
		b.snapTo(alvo.x, alvo.y, alvo.z, 0, 0);
		b.setDono(v);
		b.setFase(AnimPesca.PREPARA);
		level.addFreshEntity(b);
		s.boia = b;
		s.faseT = 0;
		barco.setDeltaMovement(0, barco.getDeltaMovement().y, 0);
		return true;
	}

	private static void mudar(Estado s, int fase) {
		s.boia.setFase(fase);
		s.faseT = 0;
	}

	private static void pescar(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		Boia boia = s.boia;
		if (boia.isRemoved()) { s.boia = null; return; }
		frear(barco, 0.5);
		Vec3 b = boia.position();
		v.getLookControl().setLookAt(b.x, b.y, b.z);
		s.faseT++;
		switch (boia.getFase()) {
			case AnimPesca.PREPARA -> {
				if (s.faseT >= AnimPesca.DUR_PREPARA) {
					mudar(s, AnimPesca.VOO);
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5f, 0.4f / (r.nextFloat() * 0.4f + 0.8f));
				}
			}
			case AnimPesca.VOO -> {
				if (s.faseT >= AnimPesca.DUR_VOO) {
					mudar(s, AnimPesca.BOIANDO);
					s.timer = s.testeRapido ? 30 : 100 + r.nextInt(200); // 5 a 15 s até morder
					level.sendParticles(ParticleTypes.SPLASH, b.x, b.y + 0.1, b.z, 6, 0.1, 0, 0.1, 0.05);
				}
			}
			case AnimPesca.BOIANDO -> {
				if (relogio % 20 == 0) level.sendParticles(ParticleTypes.BUBBLE_POP, b.x, b.y, b.z, 1, 0.05, 0, 0.05, 0);
				if (s.timer < 50) { // peixe chegando: rastro de bolhas
					double t = s.timer / 50.0;
					level.sendParticles(ParticleTypes.FISHING, b.x + t * 2, b.y + 0.05, b.z + t * 1.5, 1, 0, 0, 0, 0);
				}
				if (s.timer <= 0) {
					mudar(s, AnimPesca.MORDIDA);
					s.fisgou = s.testeFisga != null ? s.testeFisga : r.nextFloat() < 0.7f;
					s.dificil = s.fisgou && (s.testeDificil != null ? s.testeDificil : r.nextFloat() < 0.25f); // às vezes o peixe briga
					s.testeFisga = null; s.testeDificil = null; s.testeRapido = false;
					level.playSound(null, b.x, b.y, b.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35f, 1 + (r.nextFloat() - r.nextFloat()) * 0.4f);
					level.sendParticles(ParticleTypes.SPLASH, b.x, b.y + 0.1, b.z, 12, 0.2, 0, 0.2, 0.1);
					level.sendParticles(ParticleTypes.BUBBLE, b.x, b.y - 0.2, b.z, 8, 0.2, 0.1, 0.2, 0.1);
				}
			}
			case AnimPesca.MORDIDA -> {
				if (s.faseT % 4 == 0) level.sendParticles(ParticleTypes.SPLASH, b.x, b.y + 0.1, b.z, 3, 0.1, 0, 0.1, 0.05);
				if (s.faseT >= AnimPesca.DUR_MORDIDA && s.dificil) {
					mudar(s, AnimPesca.LUTA);
				} else if (s.faseT >= AnimPesca.DUR_MORDIDA) {
					mudar(s, AnimPesca.PUXANDO);
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.5f, 1f);
					if (s.fisgou) {
						boolean raro = peixeVoando(level, barco, b, r, false);
						falar(level, v, s, raro ? Falas.Tipo.RARO : Falas.Tipo.PEIXE, null, false);
					} else falar(level, v, s, Falas.Tipo.VAZIO, null, false);
				}
			}
			case AnimPesca.LUTA -> {
				// o peixe briga: respingos e a linha estalando; depois puxa (peixe difícil costuma ser raro)
				frear(barco, 0.3);
				if (s.faseT % 6 == 0) level.sendParticles(ParticleTypes.SPLASH, b.x, b.y + 0.1, b.z, 6, 0.3, 0, 0.3, 0.1);
				if (s.faseT % 25 == 0) level.playSound(null, b.x, b.y, b.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3f, 0.8f + r.nextFloat() * 0.4f);
				if (s.faseT >= AnimPesca.DUR_LUTA) {
					mudar(s, AnimPesca.PUXANDO);
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.6f, 0.8f);
					boolean raro = peixeVoando(level, barco, b, r, true);
					falar(level, v, s, raro ? Falas.Tipo.RARO : Falas.Tipo.PEIXE, null, false);
				}
			}
			case AnimPesca.PUXANDO -> {
				if (s.faseT >= AnimPesca.DUR_PUXANDO) {
					boia.discard();
					s.boia = null;
					s.timer = 400 + r.nextInt(400); // rema 20 a 40 s antes da próxima pesca
					s.caminho = List.of(); s.idx = 0;
					if (s.fisgou && relogio >= s.proximaAnim && r.nextFloat() < 0.5f) comecarAnim(level, v, barco, s, r, AnimMarcio.COMEMORAR);
				}
			}
			default -> largarBoia(s);
		}
	}

	/** O peixe salta da água até o barco (só visual) e vai para o baú. Devolve true se for raro. */
	private static boolean peixeVoando(ServerLevel level, AbstractBoat barco, Vec3 de, RandomSource r, boolean dificil) {
		ItemStack peixe = dificil && r.nextBoolean() ? new ItemStack(r.nextBoolean() ? Items.PUFFERFISH : Items.TROPICAL_FISH) : peixeAleatorio(r);
		if (barco instanceof AbstractChestBoat bau) guardar(bau, peixe.copy());
		ItemEntity ie = new ItemEntity(level, de.x, de.y + 0.2, de.z, peixe);
		// lançamento balístico (gravidade 0.04 e arrasto 0.98 do ItemEntity) para cair no baú
		Vec3 d = barco.position().add(0, 1.0, 0).subtract(ie.position());
		double arrasto = 1.12;
		ie.setDeltaMovement(d.x / VOO_PEIXE * arrasto, d.y / VOO_PEIXE + 0.5 * 0.04 * VOO_PEIXE, d.z / VOO_PEIXE * arrasto);
		ie.setPickUpDelay(Integer.MAX_VALUE);
		level.addFreshEntity(ie);
		peixesVoando.add(ie);
		level.sendParticles(ParticleTypes.SPLASH, de.x, de.y + 0.1, de.z, 10, 0.15, 0, 0.15, 0.1);
		return peixe.is(Items.PUFFERFISH) || peixe.is(Items.TROPICAL_FISH);
	}

	private static void largarBoia(Estado s) {
		if (s.boia != null) { s.boia.discard(); s.boia = null; }
	}

	/** Guarda o peixe no baú do barco; o que não couber se perde. */
	private static void guardar(AbstractChestBoat bau, ItemStack st) {
		for (int i = 0; i < bau.getContainerSize() && !st.isEmpty(); i++) {
			ItemStack s = bau.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, st) && s.getCount() < s.getMaxStackSize()) {
				int n = Math.min(st.getCount(), s.getMaxStackSize() - s.getCount());
				s.grow(n); st.shrink(n);
			}
		}
		for (int i = 0; i < bau.getContainerSize() && !st.isEmpty(); i++)
			if (bau.getItem(i).isEmpty()) { bau.setItem(i, st.copy()); st.setCount(0); }
		bau.setChanged();
	}

	private static ItemStack peixeAleatorio(RandomSource r) {
		float f = r.nextFloat(); // chances da pesca vanilla
		return new ItemStack(f < 0.60f ? Items.COD : f < 0.85f ? Items.SALMON : f < 0.98f ? Items.PUFFERFISH : Items.TROPICAL_FISH);
	}

	// ---------- noite ----------

	private static void noturno(ServerLevel level, Villager v, AbstractBoat barco, Estado s) {
		if (s.modo == Modo.NAVEGANDO) {
			s.bebado = false;
			Modo m = planejarDoca(level, barco, s) ? Modo.INDO_DOCA : Modo.PARADO_NOITE;
			falar(level, v, s, m == Modo.INDO_DOCA ? Falas.Tipo.NOITE : Falas.Tipo.SEM_DOCA, null, false); // antes de "dormir"
			s.modo = m;
		}
		if (s.modo == Modo.PARADO_NOITE) { frear(barco, 0.6); return; } // sem doca: passa a noite no barco
		if (s.idx >= s.caminho.size()) { // chegou na doca
			frear(barco, 0);
			desembarcar(v, s);
			return;
		}
		if (remar(level, v, barco, s, level.getRandom()) && !planejarDoca(level, barco, s)) s.modo = Modo.PARADO_NOITE;
	}

	private static void desembarcar(Villager v, Estado s) {
		v.stopRiding(); // o Pinto fica dormindo em cima do baú
		v.teleportTo(s.doca.getX() + 0.5, s.doca.getY(), s.doca.getZ() + 0.5);
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); // o cérebro vanilla leva ele para a cama
		s.modo = Modo.EM_TERRA;
	}

	/** Devolve false quando desiste de voltar (o procurar tenta de novo depois). */
	private static boolean emTerra(ServerLevel level, Villager v, Estado s) {
		if (s.modo == Modo.EM_TERRA) guardarVara(v); // dorme sem a vara; pega de novo de manhã
		else segurarVara(v);
		AbstractBoat barco = s.barco != null && level.getEntity(s.barco) instanceof AbstractBoat b ? b : null;
		if (barco != null && !barco.hasPassenger(Pescador::ehPescador)) frear(barco, 0); // barco atracado
		if (barco != null && relogio % 20 == 0) limparBarco(barco);
		if (s.modo == Modo.EM_TERRA) {
			// começo da noite, ainda acordado: uma vez por noite senta um pouco (lê, conversa ou se encosta)
			long hora = level.getOverworldClockTime() % 24000;
			if (noite(level) && !v.isSleeping() && hora < 13800 && relogio - s.sentouNoite > 6000 && relogio % 20 == 0) {
				s.sentouNoite = relogio;
				RandomSource r = level.getRandom();
				// quarto pequeno: mede de parede a parede e reclama; fogueira: senta e esquenta as mãos; aldeão: conversa
				if (r.nextFloat() < 0.5f && medirQuarto(level, v, s, null)) return true;
				BlockPos fogo = Arredores.fogueira(level, v, 10);
				Lugares.Lugar perto = fogo == null ? null : Arredores.pertoDoFogo(level, v, fogo);
				if (perto != null) { irSentar(v, s, perto, false, 600 + r.nextInt(400), null); s.fogoAquecer = fogo; return true; }
				Villager outro = aldeaoPerto(level, v, 10);
				if (outro != null && r.nextFloat() < 0.5f) { comecarConversa(v, s, outro, 400 + r.nextInt(300)); return true; }
				Player p = level.getNearestPlayer(v, 8);
				Lugares.Lugar l = r.nextFloat() < 0.7f ? Lugares.assento(level, v, 10) : null;
				if (l != null) irSentar(v, s, l, true, 500 + r.nextInt(400), null);
				else if (p != null && (l = Lugares.chao(level, v, p.position())) != null) irSentar(v, s, l, false, 400 + r.nextInt(300), p.getUUID());
				else if (r.nextFloat() < 0.5f && (l = Lugares.encosto(level, v, 6)) != null) irSentar(v, s, l, false, 300 + r.nextInt(300), null);
				else if (r.nextFloat() < 0.5f && (l = Lugares.deitar(level, v, 5)) != null) irSentar(v, s, l, false, 300 + r.nextInt(300), null); // olhando as estrelas
				if (s.modo != Modo.EM_TERRA) return true;
			}
			if (!noite(level) && !v.isSleeping()) {
				s.modo = Modo.VOLTANDO; s.voltando = 0;
				// mais ou menos 1 dia em 8 acorda bêbado; às vezes conta o sonho
				RandomSource r = level.getRandom();
				s.bebado = r.nextFloat() < 0.12f;
				falar(level, v, s, s.bebado ? Falas.Tipo.BEBADO : r.nextFloat() < 0.35f ? Falas.Tipo.SONHO : Falas.Tipo.ACORDOU, null, true);
			}
			return true;
		}
		// de manhã: anda até o barco e embarca
		s.voltando++;
		if (v.isPassenger()) { s.modo = Modo.NAVEGANDO; return true; }
		if (roubado(level, v, s)) return true;
		if (barco == null || barco.hasPassenger(Pescador::ehPescador)) {
			barco = barcoDele(level, v, 64);
			if (barco == null) barco = barcoLivre(level, s.doca != null ? Vec3.atCenterOf(s.doca) : v.position(), 24);
			if (barco != null) s.barco = barco.getUUID();
		}
		if (barco == null) return s.voltando <= 2400;
		if (barco.isUnderWater()) { s.modo = Modo.BUSCANDO; s.voltando = 0; return true; } // afundou de noite: vai buscar com a corda
		// anda até a beira, do lado do barco, e sobe (o cliente anima o passo para dentro do barco)
		if (alcanca(v, barco, s.voltando)) { embarcar(level, v, barco, s); return true; }
		if (relogio < s.correrAte) return true; // correndo de um raio
		if (s.voltando % 10 == 1) {
			BlockPos beira = beira(level, barco);
			if (beira == null) beira = s.doca != null ? s.doca : barco.blockPosition();
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(beira, 0.6f, 0));
		}
		return true;
	}

	/**
	 * Já dá para subir no barco? Da terra, a até 2,4 blocos; da água, a até 1,8. Se não conseguir chegar mais
	 * perto, aceita 4 blocos depois de 20 s e, depois de 2 min enroscado, qualquer distância até 32.
	 */
	private static boolean alcanca(Villager v, AbstractBoat barco, int t) {
		double h = Math.pow(barco.getX() - v.getX(), 2) + Math.pow(barco.getZ() - v.getZ(), 2), dy = Math.abs(barco.getY() - v.getY());
		double perto = v.isInWater() ? 1.8 : 2.4;
		return (h < perto * perto && dy < 1.6) || (t > 400 && v.distanceToSqr(barco) < 4 * 4) || (t > 2400 && v.distanceToSqr(barco) < 32 * 32);
	}

	/** Bloco em terra firme, colado no barco (até 2,2 blocos dele), de onde ele sobe: o mais perto. */
	private static BlockPos beira(Level level, AbstractBoat barco) {
		BlockPos b = barco.blockPosition(), melhor = null;
		double best = 2.2 * 2.2;
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 2; dy++) {
			BlockPos q = b.offset(dx, dy, dz);
			if (!livre(level, q) || !livre(level, q.above()) || level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) continue;
			double d = Math.pow(q.getX() + 0.5 - barco.getX(), 2) + Math.pow(q.getZ() + 0.5 - barco.getZ(), 2);
			if (d < best) { best = d; melhor = q; }
		}
		return melhor;
	}

	private static void embarcar(ServerLevel level, Villager v, AbstractBoat barco, Estado s) {
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.startRiding(barco, true, true);
		marcarBarco(barco, v);
		if (!barco.hasPassenger(Pescador::ehPinto))
			for (Chicken c : level.getEntities(EntityTypes.CHICKEN, barco.getBoundingBox().inflate(16), Pescador::ehPinto)) {
				c.stopRiding();
				c.startRiding(barco, true, true);
				break;
			}
		s.modo = Modo.NAVEGANDO;
		s.doca = null;
		s.caminho = List.of(); s.idx = 0;
		s.timer = 200;
		s.ancora = null; s.sair = false;
	}

	/** Barco com baú sem pescador (pode ter o Pinto) mais próximo. */
	private static AbstractChestBoat barcoLivre(ServerLevel level, Vec3 perto, double raio) {
		AbstractChestBoat melhor = null;
		double best = raio * raio;
		for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(perto, perto).inflate(raio),
				e -> e instanceof AbstractChestBoat b && !b.hasPassenger(Pescador::ehPescador) && !b.hasPassenger(p -> p instanceof Player))) {
			double d = e.distanceToSqr(perto);
			if (d < best) { best = d; melhor = (AbstractChestBoat) e; }
		}
		return melhor;
	}

	private static boolean madeira(BlockState st) {
		return st.is(BlockTags.PLANKS) || st.is(BlockTags.WOODEN_SLABS) || st.is(BlockTags.WOODEN_STAIRS);
	}

	private static boolean livre(Level level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
	}

	/** Água a até 3 blocos do barril: devolve onde pisar ao lado do barril (ou em cima dele), ou null. */
	private static BlockPos pertoDoBarril(Level level, BlockPos agua, BlockPos barril) {
		if (Math.abs(agua.getX() - barril.getX()) + Math.abs(agua.getZ() - barril.getZ()) > 3 || Math.abs(agua.getY() - barril.getY()) > 3) return null;
		for (Direction d : Direction.Plane.HORIZONTAL)
			for (BlockPos q : new BlockPos[]{barril.relative(d), barril.relative(d).above()})
				if (livre(level, q) && livre(level, q.above()) && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) return q;
		return livre(level, barril.above()) && livre(level, barril.above(2)) ? barril.above() : null;
	}

	/** Doca: madeira na borda da água com espaço para ficar em pé. Devolve onde pisar, ou null. */
	private static BlockPos doca(Level level, BlockPos agua) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = agua.relative(d);
			for (BlockPos m : new BlockPos[]{n, n.above()})
				if (madeira(level.getBlockState(m)) && livre(level, m.above()) && livre(level, m.above(2))) return m.above();
		}
		return null;
	}

	/**
	 * Busca em largura pela água até a doca e monta o caminho até ela. A doca é a água perto do barril de
	 * trabalho dele (se tiver); senão, a madeira na beira da água mais próxima.
	 */
	private static boolean planejarDoca(ServerLevel level, AbstractBoat barco, Estado s) {
		s.caminho = List.of(); s.idx = 0;
		BlockPos ini = partida(level, barco);
		if (ini == null) return false;
		Map<BlockPos, BlockPos> pai = new HashMap<>();
		ArrayDeque<BlockPos> fila = new ArrayDeque<>();
		fila.add(ini); pai.put(ini, ini);
		while (!fila.isEmpty() && pai.size() < BUSCA_MAX * 2) {
			BlockPos p = fila.poll();
			BlockPos pisa = s.barril != null ? pertoDoBarril(level, p, s.barril) : doca(level, p);
			if (pisa != null) {
				s.doca = pisa;
				s.caminho = caminho(pai, ini, p);
				return true;
			}
			for (BlockPos q : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
				if (pai.containsKey(q) || q.distManhattan(s.casa) > LONGE_DE_CASA + 16 || !agua(level, q) || !livreDeEncalhe(s, q)) continue;
				pai.put(q, p); fila.add(q);
			}
		}
		return false;
	}

	// ---------- buscar o barco ----------

	/**
	 * Sem barco: procura o dele (ou um livre) a até RAIO_BARCO blocos e vai até ele, andando ou nadando.
	 * Se o barco estiver afundado, nada até a superfície em cima dele, amarra uma corda curta e puxa o barco
	 * até ele, boiando no lugar (não sai da água). Com o barco boiando e perto, solta a corda e embarca.
	 * Devolve false quando desiste.
	 */
	private static boolean buscar(ServerLevel level, Villager v, Estado s) {
		segurarVara(v);
		s.voltando++;
		if (v.isPassenger()) { s.modo = Modo.NAVEGANDO; return true; }
		if (roubado(level, v, s)) return true;
		AbstractBoat barco = s.barco != null && level.getEntity(s.barco) instanceof AbstractBoat b && !b.hasPassenger(Pescador::ehPescador) ? b : null;
		if (barco == null || barco.distanceToSqr(v) > RAIO_BARCO * RAIO_BARCO) {
			barco = barcoDele(level, v, RAIO_BARCO);
			if (barco == null) barco = barcoLivre(level, v.position(), RAIO_BARCO);
			if (barco != null) s.barco = barco.getUUID();
		}
		if (barco == null) return s.voltando <= 1200; // procura por 1 min
		boolean fundo = barco.isUnderWater();
		boolean corda = barco.isLeashed() && barco.getLeashHolder() == v;
		v.getLookControl().setLookAt(barco, 30f, 30f);
		if (!fundo) {
			if (corda) barco.removeLeash();
			if (alcanca(v, barco, s.voltando)) { embarcar(level, v, barco, s); return true; } // encostou: sobe
			// em terra, vai para a beira do lado do barco; se não tiver, entra na água e nada até ele
			BlockPos beira = v.isInWater() ? null : beira(level, barco);
			irAte(v, beira != null ? Vec3.atBottomCenterOf(beira) : barco.position(), s.voltando);
			return s.voltando <= 3600; // desiste depois de 3 min
		}
		// afundado: o ponto da superfície mais perto do barco (direto em cima, ou do lado se tiver algo por cima)
		Vec3 sup = superficie(level, barco);
		if (sup == null) return s.voltando <= 3600;
		double h = Math.pow(sup.x - v.getX(), 2) + Math.pow(sup.z - v.getZ(), 2);
		if (!corda && h < 1.5 * 1.5 && v.isInWater()) {
			barco.setLeashedTo(v, true); // corda curta no barco afundado
			s.puxando = 0;
			falar(level, v, s, Falas.Tipo.CORDA, null, false);
			corda = true;
		}
		if (!corda) { irAte(v, sup, s.voltando); return s.voltando <= 3600; }
		// boia no lugar e puxa a corda: o barco vem até embaixo dele e sobe (corda de ~1,5 bloco)
		s.puxando++;
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.getNavigation().stop();
		Vec3 para = new Vec3(v.getX(), sup.y - 0.2, v.getZ()).subtract(barco.position());
		double len = para.length();
		if (len > 0.3) barco.setDeltaMovement(para.scale(Math.min(0.12, 0.05 * len) / len).add(0, 0.04, 0));
		if (s.puxando % 40 == 0) level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.LEAD_TIED, SoundSource.NEUTRAL, 0.5f, 0.8f);
		if (s.puxando > 600) { // 30 s e nada: o barco solta de vez e sobe
			barco.setPos(sup.x, sup.y - 0.4, sup.z);
			s.puxando = 0;
		}
		return s.voltando <= 3600;
	}

	/** Anda até o ponto; na água vai nadando direto (sem dar a volta por terra). */
	private static void irAte(Villager v, Vec3 alvo, int t) {
		Estado e = estados.get(v.getUUID());
		if (e != null && relogio < e.correrAte) return; // correndo de um raio
		if (v.isInWater()) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			Vec3 d = new Vec3(alvo.x - v.getX(), 0, alvo.z - v.getZ());
			if (d.lengthSqr() > 0.25) {
				d = d.normalize();
				Vec3 m = v.getDeltaMovement();
				v.setDeltaMovement(m.x * 0.8 + d.x * 0.04, Math.max(m.y, v.getY() < alvo.y - 0.6 ? 0.04 : m.y), m.z * 0.8 + d.z * 0.04);
				v.setYRot((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG));
				v.yBodyRot = v.getYRot();
			}
		} else if (t % 10 == 1) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(alvo, 0.75f, 1));
	}

	/** Ponto na superfície da água mais perto do barco afundado (até 4 blocos para o lado, fora de coluna de bolhas), ou null. */
	private static Vec3 superficie(Level level, AbstractBoat barco) {
		BlockPos b = barco.blockPosition();
		BlockPos melhor = null;
		double best = Double.MAX_VALUE;
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 8; dy++) {
			BlockPos q = b.offset(dx, dy, dz);
			if (!agua(level, q)) continue;
			double d = dx * dx + dz * dz + dy * 0.1;
			if (d < best) { best = d; melhor = q; }
			break; // a superfície dessa coluna
		}
		return melhor == null ? null : new Vec3(melhor.getX() + 0.5, melhor.getY() + 0.9, melhor.getZ() + 0.5);
	}

	// ---------- o Pinto ----------

	/** O Pinto não vai embora: fora do barco, volta para o barco do Márcio (ou para perto dele) e sobe de novo. */
	private static void cuidarDoPinto(ServerLevel level) {
		for (Chicken c : level.getEntities(EntityTypes.CHICKEN, Pescador::ehPinto)) {
			if (c.isPassenger() || c.isLeashed() || !c.isAlive()) continue;
			Villager v = null;
			double best = 64 * 64;
			for (Villager x : level.getEntities(EntityTypes.VILLAGER, Pescador::ehPescador)) {
				double d = x.distanceToSqr(c);
				if (d < best) { best = d; v = x; }
			}
			if (v == null) continue;
			AbstractBoat barco = v.getVehicle() instanceof AbstractBoat b ? b : barcoDele(level, v, 64);
			Entity alvo = barco != null && !barco.isUnderWater() ? barco : v;
			double d = c.distanceToSqr(alvo);
			boolean cabe = barco != null && !barco.isUnderWater() && !barco.hasPassenger(Pescador::ehPinto)
					&& !barco.hasPassenger(p -> p instanceof Player) && barco.getPassengers().size() < 2;
			// encostou no barco, ou se perdeu longe (como os bichos de estimação): sobe em cima do baú
			if (cabe && (d < 2.5 * 2.5 || d > 24 * 24)) {
				c.getNavigation().stop();
				c.startRiding(barco, true, true);
			} else if (d > 3 * 3) c.getNavigation().moveTo(alvo, 1.2);
		}
	}

	// ---------- sentar, raio, hi five ----------

	/** Vai até o lugar e senta (ou se encosta) por 'ticks'; lendo um livro, ou de papo com alguém. */
	private static void irSentar(Villager v, Estado s, Lugares.Lugar l, boolean ler, int ticks, UUID papo) {
		if (s.modo != Modo.INDO_SENTAR && s.modo != Modo.SENTADO) s.antes = s.modo;
		s.lugar = l;
		s.lendo = ler;
		s.duracaoSentado = ticks;
		s.papoCom = papo;
		s.modo = Modo.INDO_SENTAR;
		s.voltando = 0;
	}

	private static void sentar(ServerLevel level, Villager v, Estado s) {
		if (s.modo == Modo.INDO_SENTAR) {
			s.voltando++;
			Lugares.Lugar l = s.lugar;
			double h = Math.pow(l.pe().x - v.getX(), 2) + Math.pow(l.pe().z - v.getZ(), 2);
			if (h < 0.7 * 0.7 && Math.abs(l.pe().y - v.getY()) < 1.3) {
				// chegou na frente do lugar: senta (o cliente vira, abaixa ou dá o pulinho até a altura do assento)
				if (l.tipo() == Lugares.VEICULO) {
					if (!l.veiculo().isAlive() || !l.veiculo().getPassengers().isEmpty() || !v.startRiding(l.veiculo(), true, true)) { pararDeSentar(v, s); return; }
				} else v.startRiding(Assento.em(level, l.tipo(), l.assento(), l.yaw(), v.position()), true, true);
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				v.getNavigation().stop();
				s.modo = Modo.SENTADO;
				s.sentadoAte = relogio + s.duracaoSentado;
				s.livroEm = s.lendo ? relogio + 30 : 0;
				s.proximoPapo = relogio + 60;
				if (s.fogoAquecer != null) reagir(v, Pacotes.Reacao.AQUECER, -1, Vec3.atBottomCenterOf(s.fogoAquecer));
				return;
			}
			if (s.voltando > 400) { pararDeSentar(v, s); return; } // não conseguiu chegar
			if (s.voltando % 5 == 1) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(l.pe(), 0.55f, 0));
			return;
		}
		// sentado
		if (!v.isPassenger() || v.getVehicle() instanceof AbstractBoat) { pararDeSentar(v, s); return; }
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (s.lendo && s.livroEm > 0) {
			// tira o livro do bolso: a mão vai ao bolso e o livro aparece na mão quando ela volta
			if (relogio == s.livroEm - 12) reagir(v, Pacotes.Reacao.LIVRO, -1, v.position());
			if (relogio >= s.livroEm) { v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOOK)); v.setDropChance(EquipmentSlot.MAINHAND, 0f); s.livroEm = 0; }
		}
		Player com = s.papoCom == null ? null : level.getPlayerByUUID(s.papoCom);
		if (com != null && com.distanceToSqr(v) < 10 * 10) v.getLookControl().setLookAt(com, 30f, 30f);
		if (relogio >= s.proximoPapo) {
			s.proximoPapo = relogio + 240 + level.getRandom().nextInt(200);
			if (com != null && com.distanceToSqr(v) < 10 * 10) falar(level, v, s, Falas.Tipo.PAPO, com.getName().getString(), true);
			else if (s.lendo && level.getRandom().nextFloat() < 0.3f) falar(level, v, s, Falas.Tipo.LEITURA, null, false);
		}
		boolean perigo = relogio % 10 == 0 && !level.getEntities((Entity) null, v.getBoundingBox().inflate(10), e -> e instanceof Enemy && e.isAlive()).isEmpty();
		if (relogio >= s.sentadoAte || perigo || (s.papoCom != null && com == null)) levantar(v, s);
	}

	/** Levanta e volta para o que estava fazendo (guarda o livro primeiro). */
	private static void levantar(Villager v, Estado s) {
		if (v.getMainHandItem().is(Items.BOOK)) {
			reagir(v, Pacotes.Reacao.GUARDA, -1, v.position());
			v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		v.stopRiding();
		pararDeSentar(v, s);
	}

	private static void pararDeSentar(Villager v, Estado s) {
		if (s.fogoAquecer != null) { reagir(v, Pacotes.Reacao.PARA, Pacotes.Reacao.AQUECER, v.position()); s.fogoAquecer = null; }
		s.modo = s.antes != null ? s.antes : Modo.EM_TERRA;
		s.antes = null;
		s.lugar = null;
		s.papoCom = null;
		s.voltando = 0;
		if (s.modo != Modo.EM_TERRA) segurarVara(v);
	}

	/** Raio caiu: quem estiver a até 24 blocos se assusta; em terra, levanta (se estiver sentado) e corre para longe. */
	public static void raio(ServerLevel level, Vec3 onde) {
		for (var en : estados.entrySet()) {
			if (!(level.getEntity(en.getKey()) instanceof Villager v) || v.distanceToSqr(onde) > 24 * 24 || v.isSleeping()) continue;
			Estado s = en.getValue();
			reagir(v, Pacotes.Reacao.SUSTO, -1, onde);
			falar(level, v, s, Falas.Tipo.RAIO, null, true);
			if (v.getVehicle() instanceof AbstractBoat) continue; // no barco só leva o susto
			if (s.modo == Modo.SENTADO || s.modo == Modo.INDO_SENTAR) levantar(v, s);
			Vec3 foge = v.position().subtract(onde).multiply(1, 0, 1);
			foge = foge.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : foge.normalize();
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(v.position().add(foge.scale(8)), 0.9f, 1));
			s.correrAte = relogio + 80;
		}
	}

	/** Levanta a mão para o "toca aqui", virado para quem ele cumprimentou. */
	private static void oferecerMao(ServerLevel level, Villager v, Estado s) {
		s.hiFiveEm = 0;
		Player p = s.hiFiveCom == null ? null : level.getPlayerByUUID(s.hiFiveCom);
		if (p == null || p.distanceToSqr(v) > 6 * 6 || s.boia != null) return;
		s.hiFiveAte = relogio + 100;
		s.olharEnt = p;
		s.olharAte = s.hiFiveAte;
		reagir(v, Pacotes.Reacao.OFERECE, p.getId(), p.getEyePosition());
		falar(level, v, s, Falas.Tipo.TOCA, null, true);
	}

	/** O jogador bateu no Márcio enquanto ele oferecia a mão: é um hi five (não machuca). */
	public static boolean hiFive(Player p, Entity e) {
		if (!(e instanceof Villager v) || !ehPescador(v) || !(v.level() instanceof ServerLevel level)) return false;
		Estado s = estados.get(v.getUUID());
		if (s == null || relogio > s.hiFiveAte || p.distanceToSqr(v) > 4.5 * 4.5) return false;
		s.hiFiveAte = 0;
		reagir(v, Pacotes.Reacao.BATE, p.getId(), p.getEyePosition());
		Vec3 meio = v.getEyePosition().add(p.getEyePosition()).scale(0.5).add(0, 0.25, 0);
		level.playSound(null, meio.x, meio.y, meio.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.8f, 1.6f);
		level.sendParticles(ParticleTypes.CRIT, meio.x, meio.y, meio.z, 8, 0.1, 0.1, 0.1, 0.15);
		falar(level, v, s, Falas.Tipo.BATEU, null, true);
		return true;
	}

	private static void reagir(Villager v, int tipo, int alvo, Vec3 onde) {
		Pacotes.Reacao r = new Pacotes.Reacao(v.getId(), tipo, alvo, onde.x, onde.y, onde.z);
		for (ServerPlayer p : PlayerLookup.tracking(v)) ServerPlayNetworking.send(p, r);
	}

	// ---------- arredores: fogo, armadura, chuva, quarto, conversa ----------

	/** Reage ao que tem em volta (a cada segundo): pegando fogo, fogo perto, armadura nova de alguém, chuva. */
	private static void arredores(ServerLevel level, Villager v, Estado s) {
		boolean aPe = v.getVehicle() == null && !v.isSleeping();
		// pegando fogo: grita e corre para a água
		if (v.isOnFire() && relogio >= s.fogoEm) {
			s.fogoEm = relogio + 60;
			falar(level, v, s, Falas.Tipo.FOGO, null, true);
			if (aPe) {
				Vec3 agua = Arredores.agua(level, v, 10);
				correrPara(v, s, agua != null ? agua : v.position().add(level.getRandom().nextDouble() * 8 - 4, 0, level.getRandom().nextDouble() * 8 - 4), 60);
			}
		}
		// fogo perto (fora a fogueira em que ele mesmo está se esquentando): protege o rosto e se afasta
		if (relogio >= s.calorEm && s.fogoAquecer == null && !v.isOnFire()) {
			Vec3 fogo = Arredores.calor(level, v, 2.5);
			if (fogo != null) {
				s.calorEm = relogio + 120;
				reagir(v, Pacotes.Reacao.CALOR, -1, fogo);
				falar(level, v, s, Falas.Tipo.QUENTE, null, false);
				if (aPe) { Vec3 sai = v.position().subtract(fogo).multiply(1, 0, 1); correrPara(v, s, v.position().add(sai.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : sai.normalize().scale(3)), 40); }
			}
		}
		// armadura nova (melhor que a de antes) de quem está perto: olha de cima a baixo e elogia
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(v) > 8 * 8) continue;
			String agora = Arredores.armadura(p), antes = s.armaduras.put(p.getUUID(), agora);
			if (antes != null && !antes.equals(agora) && Arredores.valor(agora) > Arredores.valor(antes) && relogio >= s.armaduraEm) {
				s.armaduraEm = relogio + 200;
				reagir(v, Pacotes.Reacao.ARMADURA, p.getId(), p.position());
				falar(level, v, s, Falas.Tipo.ARMADURA, p.getName().getString(), true);
			}
		}
		// chuva em terra, sem teto: reclama de vez em quando (o cliente põe a mão na cabeça)
		if (aPe && relogio >= s.chuvaEm && level.isRainingAt(v.blockPosition().above())) {
			s.chuvaEm = relogio + 2400;
			falar(level, v, s, Falas.Tipo.CHUVA, null, false);
		}
	}

	private static void correrPara(Villager v, Estado s, Vec3 alvo, int ticks) {
		v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(alvo, 0.9f, 0));
		s.correrAte = relogio + ticks;
	}

	/**
	 * Quarto pequeno (até 8 blocos de chão ligados à cama): vai até uma ponta, atravessa contando os passos até a
	 * outra e reclama. 'cama' null usa a cama dele (memória HOME do aldeão). Devolve false se o quarto é grande.
	 */
	private static boolean medirQuarto(ServerLevel level, Villager v, Estado s, BlockPos cama) {
		if (cama == null) cama = v.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.dimension() == level.dimension()).map(g -> g.pos()).orElse(null);
		if (cama == null) return false;
		List<BlockPos> chao = Arredores.quarto(level, cama);
		BlockPos[] pontas = chao.isEmpty() || chao.size() > 8 ? null : Arredores.pontas(chao);
		if (pontas == null) return false;
		if (s.modo != Modo.MEDINDO) s.antes = s.modo;
		s.medir = pontas;
		s.medirIdx = 0;
		s.modo = Modo.MEDINDO;
		s.voltando = 0;
		return true;
	}

	private static void medir(ServerLevel level, Villager v, Estado s) {
		s.voltando++;
		Vec3 alvo = Vec3.atBottomCenterOf(s.medir[s.medirIdx]);
		boolean chegou = Math.pow(alvo.x - v.getX(), 2) + Math.pow(alvo.z - v.getZ(), 2) < 0.45 * 0.45;
		if (chegou && s.medirIdx == 0) { // numa ponta: agora atravessa devagar, contando (o cliente olha os pés e conta nos dedos)
			s.medirIdx = 1;
			reagir(v, Pacotes.Reacao.MEDIR, -1, alvo);
		} else if (chegou || s.voltando > 500) {
			reagir(v, Pacotes.Reacao.PARA, Pacotes.Reacao.MEDIR, alvo);
			if (chegou) {
				double d = Math.sqrt(s.medir[0].distSqr(s.medir[1]));
				falar(level, v, s, Falas.Tipo.QUARTO, String.valueOf(Math.max(1, Math.round(d * 1.6))), true);
			}
			s.modo = s.antes != null ? s.antes : Modo.EM_TERRA;
			s.antes = null;
			return;
		}
		if (s.voltando % 5 == 1) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(alvo, s.medirIdx == 0 ? 0.55f : 0.32f, 0));
	}

	/** Aldeão acordado mais perto (até 'raio'), que não esteja dormindo nem trabalhando de barco. */
	private static Villager aldeaoPerto(ServerLevel level, Villager v, int raio) {
		Villager melhor = null;
		double best = raio * raio;
		for (Villager o : level.getEntities(EntityTypes.VILLAGER, v.getBoundingBox().inflate(raio), o -> o != v && !o.isSleeping() && !o.isPassenger())) {
			double d = o.distanceToSqr(v);
			if (d < best) { best = d; melhor = o; }
		}
		return melhor;
	}

	private static void comecarConversa(Villager v, Estado s, LivingEntity outro, int ticks) {
		if (s.modo != Modo.CONVERSANDO) s.antes = s.modo;
		s.modo = Modo.CONVERSANDO;
		s.conversaCom = outro.getUUID();
		s.conversaAte = relogio + ticks;
		s.proximaVez = relogio + 30;
		s.vezDele = true;
		s.conversaComecou = false;
		s.voltando = 0;
	}

	/**
	 * Conversando: chega perto, fica de frente e os dois se olham; um fala de cada vez. Se o outro também for um
	 * NPC nosso, ele fala as frases dele; um aldeão comum responde com os sons de aldeão.
	 */
	private static void conversar(ServerLevel level, Villager v, Estado s) {
		Entity e = s.conversaCom == null ? null : level.getEntity(s.conversaCom);
		boolean acabou = !(e instanceof LivingEntity outro) || !outro.isAlive() || relogio > s.conversaAte || outro.distanceToSqr(v) > 14 * 14
				|| (outro instanceof Villager ov && ov.isSleeping()) || v.isPassenger();
		if (acabou) {
			if (s.conversaComecou) reagir(v, Pacotes.Reacao.PARA, Pacotes.Reacao.CONVERSA, v.position());
			s.modo = s.antes != null ? s.antes : Modo.EM_TERRA;
			s.antes = null;
			s.conversaCom = null;
			return;
		}
		LivingEntity outro = (LivingEntity) e;
		s.voltando++;
		if (outro.distanceToSqr(v) > 2.3 * 2.3) { // chega perto primeiro
			if (s.voltando % 10 == 1) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(outro, 0.55f, 2));
			return;
		}
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.getNavigation().stop();
		v.getLookControl().setLookAt(outro, 30f, 30f);
		if (outro instanceof Villager ov) { // o outro para e olha para ele
			ov.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			ov.getNavigation().stop();
			ov.getLookControl().setLookAt(v, 30f, 30f);
		}
		if (!s.conversaComecou) { reagir(v, Pacotes.Reacao.CONVERSA, outro.getId(), outro.getEyePosition()); s.conversaComecou = true; }
		if (relogio < s.proximaVez) return;
		RandomSource r = level.getRandom();
		if (s.vezDele) falar(level, v, s, Falas.Tipo.CONVERSA, null, true);
		else {
			Estado o = estados.get(outro.getUUID());
			if (o != null && outro instanceof Villager ov && ehPescador(ov)) falar(level, ov, o, Falas.Tipo.CONVERSA, null, true);
			else level.playSound(null, outro.getX(), outro.getEyeY(), outro.getZ(), r.nextBoolean() ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 0.9f + r.nextFloat() * 0.2f);
		}
		s.vezDele = !s.vezDele;
		s.proximaVez = relogio + 70 + r.nextInt(60);
	}

	// ---------- fuga ----------

	/** Monstro mais perto do barco, a até 'raio' blocos, ou null. */
	private static Entity monstro(ServerLevel level, AbstractBoat barco, double raio) {
		Entity melhor = null;
		double best = raio * raio;
		for (Entity e : level.getEntities((Entity) null, barco.getBoundingBox().inflate(raio), e -> e instanceof Enemy && e.isAlive())) {
			double d = e.distanceToSqr(barco);
			if (d < best) { best = d; melhor = e; }
		}
		return melhor;
	}

	/** Viu um monstro: larga a pesca e rema rápido para a doca (ou para longe, se não tiver doca). */
	private static void fugir(ServerLevel level, Villager v, AbstractBoat barco, Estado s, Entity m) {
		largarBoia(s);
		s.animFim = 0;
		s.modo = Modo.FUGINDO;
		s.calma = 0;
		if (!planejarDoca(level, barco, s)) planejar(level, barco, s, level.getRandom(), m.position());
		v.getLookControl().setLookAt(m, 30f, 30f);
		falar(level, v, s, Falas.Tipo.MEDO, null, true);
	}

	/** Fugindo: rema até a doca e espera lá até passar 10 s sem monstro por perto. */
	private static void fugindo(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		Entity m = relogio % 10 == 0 ? monstro(level, barco, 16) : null;
		if (relogio % 10 == 0) s.calma = m == null ? s.calma + 10 : 0;
		if (s.calma >= 200) { s.modo = Modo.NAVEGANDO; s.caminho = List.of(); s.idx = 0; return; }
		if (m != null) v.getLookControl().setLookAt(m, 30f, 30f);
		if (s.idx >= s.caminho.size()) { frear(barco, 0.3); return; } // chegou: espera atracado
		if (remar(level, v, barco, s, r) && !planejarDoca(level, barco, s) && m != null)
			planejar(level, barco, s, r, m.position());
	}

	// ---------- vida: falas, olhar, animações ----------

	/** Cumprimenta quem chega perto (uma vez a cada 5 min por jogador) e às vezes brinca com o Pinto. */
	private static void vida(ServerLevel level, Villager v, Estado s) {
		// apanhou: reclama (o cliente faz ele recuar para o lado contrário da pancada)
		if (v.hurtTime > 0 && relogio >= s.doeuEm) { s.doeuEm = relogio + 100; falar(level, v, s, Falas.Tipo.APANHOU, null, true); }
		if (s.hiFiveEm > 0 && relogio >= s.hiFiveEm) oferecerMao(level, v, s);
		if (relogio % 20 != 0) return;
		arredores(level, v, s);
		RandomSource r = level.getRandom();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(v) > 8 * 8) continue;
			Integer ult = s.cumprimentou.get(p.getUUID());
			if (ult != null && relogio - ult < 6000) continue;
			if (falar(level, v, s, Falas.Tipo.OI, p.getName().getString(), false)) {
				s.cumprimentou.put(p.getUUID(), relogio);
				s.olharEnt = p;
				s.olharAte = relogio + 60;
				// às vezes oferece a mão para um "toca aqui" logo depois do oi
				if (r.nextFloat() < 0.35f && s.boia == null) { s.hiFiveEm = relogio + 40; s.hiFiveCom = p.getUUID(); }
				if (relogio >= s.proximaAnim && s.boia == null && relogio >= s.animFim && v.getVehicle() instanceof AbstractBoat b)
					comecarAnim(level, v, b, s, r, AnimMarcio.ACENAR);
			}
			return;
		}
		// de vez em quando: piada, brincadeira com o Pinto ou bobagem de bêbado
		if (relogio % 200 == 0 && r.nextFloat() < 0.3f && v.getVehicle() instanceof AbstractBoat b) {
			float f = r.nextFloat();
			Falas.Tipo tipo = s.bebado && r.nextBoolean() ? Falas.Tipo.BEBADO
					: f < 0.1f ? Falas.Tipo.CANCAO // canta um pedacinho
					: f < 0.45f && b.hasPassenger(Pescador::ehPinto) ? Falas.Tipo.PINTO : Falas.Tipo.PIADA;
			falar(level, v, s, tipo, null, false);
		}
	}

	/**
	 * Balão de fala nos jogadores que veem o Márcio. No máximo uma fala a cada 40 a 90 s
	 * ('forcar' ignora esse intervalo) e só se tiver alguém a menos de 32 blocos.
	 */
	private static boolean falar(ServerLevel level, Villager v, Estado s, Falas.Tipo tipo, String nome, boolean forcar) {
		if (v.isSleeping()) return false; // não fala dormindo
		// passando a noite (no barco sem doca ou indo para a cama): só fala se for urgente (medo, roubo)
		if (!forcar && (s.modo == Modo.PARADO_NOITE || (s.modo == Modo.EM_TERRA && noite(level)))) return false;
		if (!forcar && relogio < s.proximaFala) return false;
		if (level.getNearestPlayer(v, 32) == null) return false;
		Falas.Linha l = Falas.frase(tipo, level.getRandom(), nome);
		Pacotes.Fala fala = new Pacotes.Fala(v.getId(), Falas.balao(l.texto()), l.som() == null ? "" : l.som(), Falas.humor(tipo));
		for (ServerPlayer p : PlayerLookup.tracking(v)) ServerPlayNetworking.send(p, fala);
		s.proximaFala = relogio + 800 + level.getRandom().nextInt(1000);
		return true;
	}

	/** Vira a cabeça para jogadores perto, às vezes para um ponto qualquer; senão olha para onde rema. */
	private static void olhar(ServerLevel level, Villager v, Estado s, RandomSource r) {
		if (relogio >= s.olharAte) {
			s.olharEnt = null;
			s.olharPonto = null;
			float f = r.nextFloat();
			Player p = level.getNearestPlayer(v, 10);
			if (p != null && f < 0.6f) s.olharEnt = p;
			else if (f < 0.85f)
				s.olharPonto = v.getEyePosition().add((r.nextDouble() - 0.5) * 12, r.nextDouble() * 3 - 1, (r.nextDouble() - 0.5) * 12);
			s.olharAte = relogio + 40 + r.nextInt(80);
		}
		if (s.olharEnt != null && s.olharEnt.isAlive() && s.olharEnt.distanceToSqr(v) < 16 * 16) v.getLookControl().setLookAt(s.olharEnt, 30f, 30f);
		else if (s.olharPonto != null) v.getLookControl().setLookAt(s.olharPonto.x, s.olharPonto.y, s.olharPonto.z);
	}

	/** Começa uma animação especial ('qual' = 0 sorteia). Uma a cada 1 a 3 min. */
	private static boolean comecarAnim(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r, int qual) {
		Player p = level.getNearestPlayer(v, 12);
		boolean pinto = barco.hasPassenger(Pescador::ehPinto);
		int a = qual;
		if (a == AnimMarcio.NADA) {
			List<Integer> op = new ArrayList<>(List.of(AnimMarcio.ESPREGUICAR, AnimMarcio.COCAR));
			if (p != null) op.add(AnimMarcio.ACENAR);
			if (pinto) { op.add(AnimMarcio.CARINHO); op.add(AnimMarcio.CARINHO); }
			a = op.get(r.nextInt(op.size()));
		}
		if ((a == AnimMarcio.ACENAR && p == null) || (a == AnimMarcio.CARINHO && !pinto)) return false;
		s.anim = a;
		s.animFim = relogio + AnimMarcio.duracao(a);
		s.proximaAnim = relogio + 1200 + r.nextInt(2400);
		Pacotes.Anim pacote = new Pacotes.Anim(v.getId(), a);
		for (ServerPlayer pl : PlayerLookup.tracking(v)) ServerPlayNetworking.send(pl, pacote);
		if (a == AnimMarcio.ACENAR) { s.olharEnt = p; s.olharAte = s.animFim; }
		if (a == AnimMarcio.ESPREGUICAR && r.nextFloat() < 0.5f) falar(level, v, s, Falas.Tipo.BOCEJO, null, false);
		if (a == AnimMarcio.CARINHO) falar(level, v, s, Falas.Tipo.CARINHO, null, false);
		return true;
	}

	/** Durante a animação o barco para e ele olha para o que importa. */
	private static void animando(ServerLevel level, Villager v, AbstractBoat barco, Estado s) {
		frear(barco, 0.6);
		int t = AnimMarcio.duracao(s.anim) - (s.animFim - relogio);
		switch (s.anim) {
			case AnimMarcio.CARINHO -> {
				for (Entity pinto : barco.getPassengers()) {
					if (!ehPinto(pinto)) continue;
					v.getLookControl().setLookAt(pinto, 30f, 30f);
					if (t % 12 == 6) level.sendParticles(ParticleTypes.HEART, pinto.getX(), pinto.getY() + 0.7, pinto.getZ(), 1, 0.2, 0.1, 0.2, 0);
					if (t == 20) level.playSound(null, pinto.getX(), pinto.getY(), pinto.getZ(), SoundEvents.CHICKEN_AMBIENT_BABY, SoundSource.NEUTRAL, 0.6f, 1.2f);
				}
			}
			case AnimMarcio.ESPREGUICAR -> v.getLookControl().setLookAt(v.getX(), v.getEyeY() + 4, v.getZ()); // olha para o céu
			default -> olhar(level, v, s, level.getRandom());
		}
	}

	// ---------- barco do Márcio ----------

	/** Marca o barco com baú como dele (tag com o UUID e nome "Barco do Márcio"). */
	private static void marcarBarco(AbstractBoat barco, Villager v) {
		if (!(barco instanceof AbstractChestBoat)) return;
		barco.addTag(TAG_BARCO);
		barco.addTag(TAG_DONO + v.getUUID());
		if (!barco.hasCustomName()) barco.setCustomName(Component.literal("Barco do Márcio"));
	}

	/** Barco marcado com o UUID desse pescador, mais próximo. */
	private static AbstractChestBoat barcoDele(ServerLevel level, Villager v, double raio) {
		String tag = TAG_DONO + v.getUUID();
		AbstractChestBoat melhor = null;
		double best = raio * raio;
		for (Entity e : level.getEntities((Entity) null, v.getBoundingBox().inflate(raio),
				e -> e instanceof AbstractChestBoat && e.entityTags().contains(tag))) {
			double d = e.distanceToSqr(v);
			if (d < best) { best = d; melhor = (AbstractChestBoat) e; }
		}
		return melhor;
	}

	/** Alguém está no barco dele? Então vai atrás pedindo de volta. */
	private static boolean roubado(ServerLevel level, Villager v, Estado s) {
		if (v.isSleeping() || v.isPassenger()) return false;
		AbstractBoat b = s.barco != null && level.getEntity(s.barco) instanceof AbstractBoat x ? x : barcoDele(level, v, 64);
		if (b == null) return false;
		for (Entity p : b.getPassengers())
			if (p instanceof Player ladrao && !ladrao.isSpectator()) {
				s.barco = b.getUUID();
				s.ladrao = ladrao.getUUID();
				s.modo = Modo.PERSEGUINDO;
				s.proximaQueixa = 0;
				largarBoia(s);
				return true;
			}
		return false;
	}

	/** Segue quem pegou o barco, reclamando, até devolver (descer do barco). */
	private static void perseguir(ServerLevel level, Villager v, Estado s) {
		Player ladrao = s.ladrao == null ? null : level.getPlayerByUUID(s.ladrao);
		String tag = TAG_DONO + v.getUUID();
		boolean comBarco = ladrao != null && ladrao.getVehicle() instanceof AbstractBoat b && b.entityTags().contains(tag);
		if (!comBarco) {
			s.modo = Modo.VOLTANDO;
			s.voltando = 0;
			s.ladrao = null;
			falar(level, v, s, Falas.Tipo.DEVOLVEU, null, true);
			return;
		}
		segurarVara(v);
		v.getLookControl().setLookAt(ladrao, 30f, 30f);
		if (relogio % 10 == 0) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(ladrao, 0.75f, 2));
		if (relogio >= s.proximaQueixa && falar(level, v, s, Falas.Tipo.ROUBO, ladrao.getName().getString(), true))
			s.proximaQueixa = relogio + 300; // reclama a cada 15 s
	}

	// ---------- /bauchefe marcio testar ----------

	/** Nomes aceitos pelo /bauchefe marcio testar. */
	public static final List<String> TESTES = List.of("tudo", "espreguicar", "cocar", "acenar", "comemorar", "carinho",
			"pesca", "peixedificil", "vazio", "nadar", "levantar", "fala", "susto", "raio", "hifive", "subir", "sentar", "livro",
			"chao", "encostar", "deitar", "fogueira", "medir", "conversa", "fogo");
	/** Testes que levam ele para terra (descendo do barco, se estiver nele). */
	private static final List<String> TESTES_TERRA = List.of("subir", "sentar", "livro", "chao", "encostar", "deitar", "fogueira", "medir", "conversa", "fogo");
	private static final List<String> TESTES_TUDO = List.of("levantar", "espreguicar", "cocar", "acenar", "carinho",
			"pesca", "peixedificil", "vazio", "comemorar", "fala", "nadar");

	/** Faz o pescador mais próximo mostrar uma animação (ou todas, em sequência). */
	public static String testar(ServerLevel level, Vec3 origem, String qual) {
		Villager v = null;
		double best = 64 * 64;
		for (Villager c : level.getEntities(EntityTypes.VILLAGER, Pescador::ehPescador)) {
			double d = c.distanceToSqr(origem);
			if (d < best) { best = d; v = c; }
		}
		if (!TESTES.contains(qual)) return "§cNão conheço \"" + qual + "\". Opções: " + String.join(", ", TESTES);
		if (v == null) return "§cNenhum pescador num raio de 64 blocos";
		Villager dono = v;
		if (TESTES_TERRA.contains(qual)) {
			Estado s = estados.computeIfAbsent(v.getUUID(), u -> { Estado n = new Estado(); n.casa = dono.blockPosition(); return n; });
			return testarTerra(level, v, s, qual, origem);
		}
		if (qual.equals("raio")) { // raio de verdade (só visual, não queima nada) a 7 blocos dele
			net.minecraft.world.entity.LightningBolt raio = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
			if (raio == null) return "§cnão deu para criar o raio";
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			raio.snapTo(v.getX() + Math.cos(a) * 7, v.getY(), v.getZ() + Math.sin(a) * 7);
			raio.setVisualOnly(true);
			level.addFreshEntity(raio);
			return "";
		}
		if (!(v.getVehicle() instanceof AbstractBoat barco)) return "§cO pescador precisa estar no barco";
		Estado s = estados.computeIfAbsent(v.getUUID(), u -> { Estado n = new Estado(); n.casa = dono.blockPosition(); return n; });
		s.barco = barco.getUUID();
		s.modo = Modo.NAVEGANDO;
		if (qual.equals("tudo")) {
			s.demo = new ArrayDeque<>(TESTES_TUDO);
			s.demoProx = relogio + 20;
			return "§6Testando tudo: " + String.join(", ", TESTES_TUDO) + " §7(chegue perto para o aceno)";
		}
		return executar(level, v, barco, s, qual);
	}

	/** "tudo": quando ele está à toa no barco, roda o próximo teste da fila. */
	private static void demo(ServerLevel level, Villager v, Estado s) {
		// não pesca nem faz animação sozinho no meio do teste (o timer também conta a mordida: só mexe sem boia)
		if (s.boia == null) s.timer = Math.max(s.timer, 100);
		s.proximaAnim = Math.max(s.proximaAnim, relogio + 600);
		if (!(v.getVehicle() instanceof AbstractBoat barco) || s.boia != null || relogio < s.animFim || relogio < s.demoProx) return;
		String qual = s.demo.poll();
		if (qual == null) { s.demo = null; avisar(level, v, "§6[teste] fim"); return; }
		avisar(level, v, "§6[teste] §f" + qual + " §7" + executar(level, v, barco, s, qual));
		s.demoProx = relogio + 50;
	}

	private static void avisar(ServerLevel level, Villager v, String msg) {
		for (ServerPlayer p : level.players()) if (p.distanceToSqr(v) < 48 * 48) p.sendSystemMessage(Component.literal(msg));
	}

	private static String executar(ServerLevel level, Villager v, AbstractBoat barco, Estado s, String qual) {
		RandomSource r = level.getRandom();
		largarBoia(s);
		s.animFim = 0;
		switch (qual) {
			case "espreguicar", "cocar", "acenar", "comemorar", "carinho" -> {
				int a = switch (qual) {
					case "espreguicar" -> AnimMarcio.ESPREGUICAR;
					case "cocar" -> AnimMarcio.COCAR;
					case "acenar" -> AnimMarcio.ACENAR;
					case "comemorar" -> AnimMarcio.COMEMORAR;
					default -> AnimMarcio.CARINHO;
				};
				if (!comecarAnim(level, v, barco, s, r, a))
					return a == AnimMarcio.ACENAR ? "§cprecisa de alguém a até 12 blocos" : "§cprecisa do Pinto no barco";
			}
			case "pesca", "peixedificil", "vazio" -> {
				s.testeFisga = !qual.equals("vazio");
				s.testeDificil = qual.equals("peixedificil");
				s.testeRapido = true;
				comecarPesca(level, v, barco, s, r);
				if (s.boia == null) return "§cnão achei água livre do lado do barco";
			}
			case "nadar" -> {
				// pula na água ao lado do barco e volta depois de 6 s
				BlockPos agua = null;
				for (int raio = 2; raio <= 4 && agua == null; raio++)
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos q = BlockPos.containing(barco.getX(), barco.getY() - 0.3, barco.getZ()).relative(d, raio);
						if (agua(level, q)) { agua = q; break; }
					}
				if (agua == null) return "§cnão achei água por perto";
				v.stopRiding();
				v.teleportTo(agua.getX() + 0.5, agua.getY() + 0.4, agua.getZ() + 0.5);
				s.remontarEm = relogio + 120;
			}
			case "levantar" -> {
				// desce do barco e sobe de novo (levantar e sentar)
				v.stopRiding();
				s.remontarEm = relogio + 50;
			}
			case "susto" -> { // susto de alguma coisa à frente e à esquerda dele
				double rad = v.getYRot() * Mth.DEG_TO_RAD;
				reagir(v, Pacotes.Reacao.SUSTO, -1, v.position().add(-Mth.sin(rad) * 4 + Mth.cos(rad) * 3, 1, Mth.cos(rad) * 4 + Mth.sin(rad) * 3));
				falar(level, v, s, Falas.Tipo.MEDO, null, true);
			}
			case "hifive" -> {
				Player p = level.getNearestPlayer(v, 6);
				if (p == null) return "§cprecisa de alguém a até 6 blocos (depois é só bater nele)";
				s.hiFiveCom = p.getUUID();
				oferecerMao(level, v, s);
			}
			case "fala" -> {
				Falas.Tipo[] tipos = {Falas.Tipo.PIADA, Falas.Tipo.PINTO, Falas.Tipo.SONHO, Falas.Tipo.CANCAO};
				falar(level, v, s, tipos[r.nextInt(tipos.length)], null, true);
			}
			default -> { return "§c?"; }
		}
		return "";
	}

	/**
	 * Testes em terra: desce do barco para a beira mais perto (do lado de trás, no "subir", para ver ele dar a
	 * volta no barco) e vai sentar, ler, sentar no chão ou se encostar; no fim volta e sobe no barco de novo.
	 */
	private static String testarTerra(ServerLevel level, Villager v, Estado s, String qual, Vec3 origem) {
		AbstractBoat barco = v.getVehicle() instanceof AbstractBoat b ? b : s.barco != null && level.getEntity(s.barco) instanceof AbstractBoat b2 ? b2 : null;
		if (barco != null && v.getVehicle() == barco) {
			BlockPos beira = qual.equals("subir") ? beiraAtras(level, barco) : beira(level, barco);
			if (beira == null) beira = beiraAtras(level, barco); // teste: aceita terra um pouco mais longe
			if (beira == null) return "§cnão achei terra firme colada no barco";
			largarBoia(s);
			s.animFim = 0;
			v.stopRiding();
			v.teleportTo(beira.getX() + 0.5, beira.getY(), beira.getZ() + 0.5);
			s.barco = barco.getUUID();
		}
		if (barco == null) return "§co pescador precisa de um barco por perto";
		s.modo = Modo.VOLTANDO; // ao terminar, volta para o barco
		s.voltando = 0;
		if (qual.equals("subir")) return "";
		switch (qual) {
			case "fogo" -> { v.igniteForSeconds(4f); return ""; }
			case "medir" -> { // a cama mais perto (até 24 blocos)
				BlockPos cama = null;
				for (BlockPos q : BlockPos.betweenClosed(v.blockPosition().offset(-24, -3, -24), v.blockPosition().offset(24, 3, 24)))
					if (level.getBlockState(q).is(BlockTags.BEDS) && (cama == null || q.distSqr(v.blockPosition()) < cama.distSqr(v.blockPosition()))) cama = q.immutable();
				if (cama == null) return "§cnão achei cama a até 24 blocos";
				return medirQuarto(level, v, s, cama) ? "§7(quarto pequeno: medindo)" : "§c(o quarto dessa cama é grande: " + Arredores.quarto(level, cama).size() + " blocos de chão)";
			}
			case "fogueira" -> {
				BlockPos fogo = Arredores.fogueira(level, v, 16);
				Lugares.Lugar l = fogo == null ? null : Arredores.pertoDoFogo(level, v, fogo);
				if (l == null) return "§cnão achei fogueira acesa (com chão em volta) a até 16 blocos";
				irSentar(v, s, l, false, 300, null);
				s.fogoAquecer = fogo;
				return "";
			}
			case "conversa" -> {
				Villager outro = aldeaoPerto(level, v, 16);
				if (outro == null) return "§cnão achei aldeão acordado a até 16 blocos";
				comecarConversa(v, s, outro, 300);
				return "";
			}
			default -> {}
		}
		Player p = level.getNearestPlayer(v, 12);
		Lugares.Lugar l = switch (qual) {
			case "chao" -> Lugares.chao(level, v, p != null ? p.position() : origem);
			case "encostar" -> Lugares.encosto(level, v, 16);
			case "deitar" -> Lugares.deitar(level, v, 6);
			default -> Lugares.assento(level, v, 16); // no teste procura mais longe (de noite, sozinho, são 10 blocos)
		};
		if (l == null) return "§cnão achei lugar para " + qual + " por perto";
		irSentar(v, s, l, qual.equals("livro"), 300, qual.equals("chao") && p != null ? p.getUUID() : null);
		return "§7(" + switch (l.tipo()) { case Lugares.VEICULO -> "carrinho"; case Lugares.CHAO -> "chão"; case Lugares.ENCOSTO -> "parede"; case Lugares.DEITADO -> "deitado"; default -> "assento"; }
				+ String.format(" a %.1f blocos, altura %.2f)", Math.sqrt(l.pe().distanceToSqr(v.position())), l.assento().y - l.pe().y);
	}

	/** Beira em terra do lado de trás (popa) do barco, para o teste de subir dando a volta. */
	private static BlockPos beiraAtras(Level level, AbstractBoat barco) {
		double rad = barco.getYRot() * Mth.DEG_TO_RAD;
		Vec3 tras = barco.position().add(Mth.sin(rad) * 1.2, 0, -Mth.cos(rad) * 1.2);
		BlockPos melhor = null;
		double best = Double.MAX_VALUE;
		BlockPos b = barco.blockPosition();
		for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -1; dy <= 2; dy++) {
			BlockPos q = b.offset(dx, dy, dz);
			if (Lugares.piso(level, q) == null) continue;
			double d = Vec3.atBottomCenterOf(q).distanceToSqr(tras.x, q.getY(), tras.z);
			if (d < best) { best = d; melhor = q; }
		}
		return melhor;
	}

	/** Volta para o barco depois de um teste fora dele. */
	private static void remontar(ServerLevel level, Villager v, Estado s) {
		s.remontarEm = 0;
		if (s.barco != null && level.getEntity(s.barco) instanceof AbstractBoat barco) embarcar(level, v, barco, s);
	}

	// ---------- /bauchefe marcio ----------

	/** Troca o barco do pescador mais próximo por um barco com baú e monta ele e a galinha. */
	public static String trocarBarco(ServerLevel level, Vec3 origem) {
		Villager v = null;
		double best = 64 * 64;
		for (Villager c : level.getEntities(EntityTypes.VILLAGER, Pescador::ehPescador)) {
			double d = c.distanceToSqr(origem);
			if (d < best) { best = d; v = c; }
		}
		if (v == null) return "§cNenhum pescador num raio de 64 blocos";
		if (!(v.getVehicle() instanceof AbstractBoat barco)) return "§cO pescador não está num barco";

		Chicken galinha = null;
		for (Entity p : barco.getPassengers()) if (p instanceof Chicken c) galinha = c;
		if (galinha == null) {
			best = 24 * 24;
			for (Chicken c : level.getEntities(EntityTypes.CHICKEN, v.getBoundingBox().inflate(24), Pescador::ehPinto)) {
				double d = c.distanceToSqr(v);
				if (d < best) { best = d; galinha = c; }
			}
		}

		AbstractChestBoat novo;
		if (barco instanceof AbstractChestBoat jaTem) novo = jaTem;
		else {
			// mesma madeira: oak_boat -> oak_chest_boat, bamboo_raft -> bamboo_chest_raft
			Identifier tipo = BuiltInRegistries.ENTITY_TYPE.getKey(barco.getType());
			String path = tipo.getPath().replace("_boat", "_chest_boat").replace("_raft", "_chest_raft");
			Entity e = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath(tipo.getNamespace(), path))
					.orElse(EntityTypes.OAK_CHEST_BOAT).create(level, EntitySpawnReason.COMMAND);
			if (!(e instanceof AbstractChestBoat cb)) return "§cNão deu para criar o barco com baú";
			novo = cb;
			novo.snapTo(barco.getX(), barco.getY(), barco.getZ(), barco.getYRot(), 0);
			barco.ejectPassengers();
			barco.discard();
			level.addFreshEntity(novo);
		}
		if (v.getVehicle() != novo) { v.stopRiding(); v.startRiding(novo, true, true); }
		if (galinha != null && galinha.getVehicle() != novo) { galinha.stopRiding(); galinha.startRiding(novo, true, true); }
		profissao(level, v);
		Estado s = estados.get(v.getUUID());
		if (s != null) { largarBoia(s); s.modo = Modo.NAVEGANDO; s.caminho = List.of(); s.idx = 0; }
		return "§6Márcio no barco com baú" + (galinha == null ? " §7(sem galinha \"Pinto\" por perto)" : " com o Pinto em cima do baú");
	}

	// ---------- navegação (estilo peixe) ----------

	/** Superfície de água boa para o barco (coluna de bolhas não: puxa o barco para o fundo ou joga para cima). */
	static boolean agua(Level level, BlockPos p) {
		return level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p.above()).isAir() && !level.getBlockState(p).is(Blocks.BUBBLE_COLUMN);
	}

	/**
	 * Escolhe um destino aleatório alcançável pela água e vai até ele pelo meio do riacho:
	 * busca com custo (Dijkstra) em que andar colado na margem custa mais.
	 */
	private static void planejar(ServerLevel level, AbstractBoat barco, Estado s, RandomSource r, Vec3 longeDe) {
		s.caminho = List.of(); s.idx = 0;
		BlockPos ini = partida(level, barco);
		if (ini == null) return;
		Map<BlockPos, BlockPos> pai = new HashMap<>();
		Map<BlockPos, Integer> passos = new HashMap<>();
		Map<BlockPos, Double> custo = new HashMap<>();
		PriorityQueue<BlockPos> fila = new PriorityQueue<>(Comparator.comparingDouble(custo::get));
		custo.put(ini, 0.0); passos.put(ini, 0); fila.add(ini);
		List<BlockPos> candidatos = new ArrayList<>();
		Set<BlockPos> feito = new HashSet<>();
		// se ele já está longe de casa (fugiu, buscou o barco), o limite cresce até onde ele está, para poder voltar
		int limite = Math.max(LONGE_DE_CASA, ini.distManhattan(s.casa) + 8);
		while (!fila.isEmpty() && feito.size() < BUSCA_MAX) {
			BlockPos p = fila.poll();
			if (!feito.add(p)) continue;
			int d = passos.get(p);
			if (d >= 8 && d <= 28) candidatos.add(p);
			for (BlockPos q : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
				if (feito.contains(q) || q.distManhattan(s.casa) > limite || !agua(level, q) || !livreDeEncalhe(s, q)) continue;
				double c = custo.get(p) + 1 + (9 - largura(level, q)) * 0.7; // margem perto = mais caro
				if (c < custo.getOrDefault(q, Double.MAX_VALUE)) {
					custo.put(q, c); passos.put(q, d + 1); pai.put(q, p); fila.add(q);
				}
			}
		}
		if (candidatos.isEmpty() && !s.evitar.isEmpty()) {
			// talvez os encalhes recentes fecharam o canal: esquece e tenta de novo uma vez
			s.evitar.clear();
			planejar(level, barco, s, r, longeDe);
			if (!s.caminho.isEmpty()) return;
		}
		Vec3 anc = s.ancora;
		if (s.sair && (anc == null || feito.stream().noneMatch(p -> p.distToCenterSqr(anc.x, p.getY() + 0.5, anc.z) > 4.5 * 4.5))) {
			s.sair = false; s.ancoraT = relogio; // a água toda é pequena: não tem para onde sair, segue a vida ali
		}
		BlockPos alvo;
		if (s.sair || candidatos.isEmpty()) {
			// beco, canal estreito ou água curta: a água larga alcançável mais perto (a pelo menos 5 blocos e, se
			// estava preso, fora de onde ficou preso); se não tiver, a água mais longe que dá para chegar.
			// Dá meia-volta e sai dali.
			alvo = null;
			int melhor = Integer.MAX_VALUE;
			for (BlockPos p : feito) {
				int d = passos.get(p);
				boolean fora = !s.sair || p.distToCenterSqr(anc.x, p.getY() + 0.5, anc.z) > 4.5 * 4.5;
				if (d >= 5 && d < melhor && fora && largura(level, p) >= 6) { melhor = d; alvo = p; }
			}
			if (alvo == null) for (BlockPos p : feito) if (passos.get(p) >= 2 && (alvo == null || passos.get(p) > passos.get(alvo))) alvo = p;
			if (alvo == null) return; // só tem água onde ele está
		} else if (longeDe != null) { // fugindo: o ponto mais longe do perigo
			candidatos.sort(Comparator.comparingDouble(p -> -p.distToCenterSqr(longeDe)));
			alvo = candidatos.get(0);
		} else {
			// prefere pontos com água dos lados (meio do riacho)
			candidatos.sort(Comparator.comparingInt(p -> -largura(level, p)));
			alvo = candidatos.get(r.nextInt(Math.max(1, candidatos.size() / 2)));
		}
		s.caminho = caminho(pai, ini, alvo);
	}

	/** Refaz o caminho de 'ini' até 'alvo' (todos os blocos; o remar escolhe para onde mirar). */
	private static List<BlockPos> caminho(Map<BlockPos, BlockPos> pai, BlockPos ini, BlockPos alvo) {
		LinkedList<BlockPos> c = new LinkedList<>();
		for (BlockPos p = alvo; p != null && !p.equals(ini); p = pai.get(p)) c.addFirst(p);
		if (c.isEmpty()) c.add(alvo);
		return new ArrayList<>(c);
	}

	private static int largura(Level level, BlockPos p) {
		int n = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (agua(level, p.offset(dx, 0, dz))) n++;
		return n;
	}
}

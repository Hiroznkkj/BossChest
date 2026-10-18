package br.hirozn.bauchefe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Villagers com "pescador" no nome, dentro de um barco, passeiam pela água e pescam sozinhos.
 * Navegação no estilo da IA dos peixes: escolhe um ponto aleatório de água e vai até ele
 * por um caminho que só passa por água (busca em largura na superfície).
 * À noite atracam numa doca de madeira e dormem; de manhã voltam para o barco.
 */
public final class Pescador {
	private static final int LONGE_DE_CASA = 48, BUSCA_MAX = 900;
	private static final double VELOCIDADE = 0.07;

	private enum Modo { NAVEGANDO, INDO_DOCA, PARADO_NOITE, EM_TERRA, VOLTANDO }

	private static final class Estado {
		BlockPos casa;
		List<BlockPos> caminho = List.of();
		int idx, timer;
		Vec3 ultimaPos = Vec3.ZERO;
		Modo modo = Modo.NAVEGANDO;
		// pesca
		Boia boia;
		int faseT;
		boolean fisgou;
		// noite
		UUID barco;
		BlockPos doca; // onde ele pisa na doca
		int voltando;
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
			for (Iterator<Map.Entry<UUID, Estado>> it = estados.entrySet().iterator(); it.hasNext(); ) {
				var en = it.next();
				Estado s = en.getValue();
				Entity e = level.getEntity(en.getKey());
				if (!(e instanceof Villager v) || !ehPescador(v)) {
					if (relogio % 100 == 0) { largarBoia(s); it.remove(); }
					continue;
				}
				if (relogio % 100 == 0) chapeu(level, v);
				if (s.modo == Modo.EM_TERRA || s.modo == Modo.VOLTANDO) { if (!emTerra(level, v, s)) it.remove(); continue; }
				if (!(v.getVehicle() instanceof AbstractBoat barco)) {
					largarBoia(s);
					if (relogio % 100 == 0) it.remove();
					continue;
				}
				s.barco = barco.getUUID();
				tick(level, v, barco, s);
			}
		});
	}

	public static boolean ehPescador(Entity e) {
		return e instanceof Villager && e.hasCustomName() && e.getCustomName().getString().toLowerCase(Locale.ROOT).contains("pescador");
	}

	public static boolean ehPinto(Entity e) {
		return e instanceof Chicken && e.hasCustomName() && e.getCustomName().getString().toLowerCase(Locale.ROOT).contains("pinto");
	}

	/** Barco com baú do pescador (ou só com o Pinto, enquanto ele dorme): aceita 2 e põe a galinha em cima do baú. */
	public static boolean barcoDoMarcio(AbstractBoat b) {
		if (!(b instanceof AbstractChestBoat)) return false;
		for (Entity p : b.getPassengers()) if (ehPescador(p) || ehPinto(p)) return true;
		return false;
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
				AbstractChestBoat b = barcoLivre(level, v.position(), 24);
				if (b == null) continue;
				Estado s = new Estado();
				s.casa = b.blockPosition();
				s.barco = b.getUUID();
				s.modo = Modo.VOLTANDO;
				estados.put(v.getUUID(), s);
			}
		}
	}

	/** Chapéu de palha: profissão farmer no nível 5, com XP para não perder a profissão. */
	private static void chapeu(ServerLevel level, Villager v) {
		VillagerData d = v.getVillagerData();
		if (!d.profession().is(VillagerProfession.FARMER) || d.level() < 5)
			v.setVillagerData(d.withProfession(level.registryAccess(), VillagerProfession.FARMER).withLevel(5));
		if (v.getVillagerXp() <= 0) v.setVillagerXp(1);
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
		s.timer--;
		if (s.boia != null) { pescar(level, v, barco, s, r); return; } // termina a pesca mesmo se anoitecer
		if (noite(level)) { noturno(level, v, barco, s); return; }
		if (s.modo != Modo.NAVEGANDO) { s.modo = Modo.NAVEGANDO; s.caminho = List.of(); s.idx = 0; }

		if (s.idx >= s.caminho.size()) {
			if (s.timer <= 0 && r.nextFloat() < 0.6f) { comecarPesca(level, v, barco, s, r); return; }
			planejar(level, barco, s, r);
			if (s.caminho.isEmpty()) { comecarPesca(level, v, barco, s, r); return; }
		}
		if (remar(level, v, barco, s, r)) { s.caminho = List.of(); s.idx = 0; }
	}

	/** Rema até o próximo ponto do caminho. Devolve true se encalhou. */
	private static boolean remar(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		BlockPos alvo = s.caminho.get(s.idx);
		double dx = alvo.getX() + 0.5 - barco.getX(), dz = alvo.getZ() + 0.5 - barco.getZ();
		if (dx * dx + dz * dz < 1.5) { s.idx++; return false; }

		// vira suavemente na direção do próximo ponto e rema para a frente
		float querido = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float yaw = barco.getYRot() + Mth.clamp(Mth.wrapDegrees(querido - barco.getYRot()), -5f, 5f);
		barco.setYRot(yaw);
		barco.setYHeadRot(yaw);
		double rad = yaw * Mth.DEG_TO_RAD;
		Vec3 m = barco.getDeltaMovement();
		barco.setDeltaMovement(-Mth.sin(rad) * VELOCIDADE, m.y, Mth.cos(rad) * VELOCIDADE);
		v.getLookControl().setLookAt(alvo.getX() + 0.5, barco.getY() + 0.6, alvo.getZ() + 0.5);
		if (relogio % 30 == 0) level.playSound(null, barco.getX(), barco.getY(), barco.getZ(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.4f, 0.9f + r.nextFloat() * 0.2f);

		// encalhou?
		if (relogio % 40 == 0) {
			boolean preso = barco.position().distanceToSqr(s.ultimaPos) < 0.05;
			s.ultimaPos = barco.position();
			return preso;
		}
		return false;
	}

	private static void frear(AbstractBoat barco, double f) {
		Vec3 m = barco.getDeltaMovement();
		barco.setDeltaMovement(m.x * f, m.y, m.z * f);
	}

	// ---------- pesca ----------

	private static void comecarPesca(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		double rad = barco.getYRot() * Mth.DEG_TO_RAD;
		Vec3 frente = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad)), lado = new Vec3(frente.z, 0, -frente.x);
		Vec3 alvo = null;
		// a vara fica na mão direita: prefere jogar para a direita e para a frente
		for (Vec3 d : new Vec3[]{lado.scale(-1).add(frente.scale(0.5)), frente, lado.scale(-1), lado, frente.scale(-1)}) {
			Vec3 p = barco.position().add(d.normalize().scale(3.5));
			BlockPos bp = BlockPos.containing(p.x, barco.getY() - 0.3, p.z);
			if (agua(level, bp)) { alvo = new Vec3(p.x, bp.getY() + level.getFluidState(bp).getHeight(level, bp) - 0.05, p.z); break; }
		}
		if (alvo == null) { s.timer = 200; planejar(level, barco, s, r); return; }
		Boia b = new Boia(BauChefe.BOIA, level);
		b.snapTo(alvo.x, alvo.y, alvo.z, 0, 0);
		b.setDono(v);
		b.setFase(AnimPesca.PREPARA);
		level.addFreshEntity(b);
		s.boia = b;
		s.faseT = 0;
		barco.setDeltaMovement(0, barco.getDeltaMovement().y, 0);
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
					s.timer = 100 + r.nextInt(200); // 5 a 15 s até morder
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
					s.fisgou = r.nextFloat() < 0.7f;
					level.playSound(null, b.x, b.y, b.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35f, 1 + (r.nextFloat() - r.nextFloat()) * 0.4f);
					level.sendParticles(ParticleTypes.SPLASH, b.x, b.y + 0.1, b.z, 12, 0.2, 0, 0.2, 0.1);
					level.sendParticles(ParticleTypes.BUBBLE, b.x, b.y - 0.2, b.z, 8, 0.2, 0.1, 0.2, 0.1);
				}
			}
			case AnimPesca.MORDIDA -> {
				if (s.faseT % 4 == 0) level.sendParticles(ParticleTypes.SPLASH, b.x, b.y + 0.1, b.z, 3, 0.1, 0, 0.1, 0.05);
				if (s.faseT >= AnimPesca.DUR_MORDIDA) {
					mudar(s, AnimPesca.PUXANDO);
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.5f, 1f);
					if (s.fisgou) peixeVoando(level, barco, b, r);
				}
			}
			case AnimPesca.PUXANDO -> {
				if (s.faseT >= AnimPesca.DUR_PUXANDO) {
					boia.discard();
					s.boia = null;
					s.timer = 400 + r.nextInt(400); // rema 20 a 40 s antes da próxima pesca
					s.caminho = List.of(); s.idx = 0;
				}
			}
			default -> largarBoia(s);
		}
	}

	/** O peixe salta da água até o barco (só visual) e vai para o baú. */
	private static void peixeVoando(ServerLevel level, AbstractBoat barco, Vec3 de, RandomSource r) {
		ItemStack peixe = peixeAleatorio(r);
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
		if (s.modo == Modo.NAVEGANDO) s.modo = planejarDoca(level, barco, s) ? Modo.INDO_DOCA : Modo.PARADO_NOITE;
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
		segurarVara(v);
		AbstractBoat barco = s.barco != null && level.getEntity(s.barco) instanceof AbstractBoat b ? b : null;
		if (barco != null && !barco.hasPassenger(Pescador::ehPescador)) frear(barco, 0); // barco atracado
		if (s.modo == Modo.EM_TERRA) {
			if (!noite(level) && !v.isSleeping()) { s.modo = Modo.VOLTANDO; s.voltando = 0; }
			return true;
		}
		// de manhã: anda até o barco e embarca
		s.voltando++;
		if (v.isPassenger()) { s.modo = Modo.NAVEGANDO; return true; }
		if (barco == null || barco.hasPassenger(Pescador::ehPescador)) {
			barco = barcoLivre(level, s.doca != null ? Vec3.atCenterOf(s.doca) : v.position(), 24);
			if (barco != null) s.barco = barco.getUUID();
		}
		if (barco == null) return s.voltando <= 2400;
		double d = v.distanceToSqr(barco);
		// perto do barco embarca; se ficar enroscado por 2 min, embarca de qualquer jeito
		if (d < 2.5 * 2.5 || (s.voltando > 2400 && d < 32 * 32)) { embarcar(level, v, barco, s); return true; }
		if (s.voltando % 10 == 1) {
			BlockPos alvo = s.doca != null ? s.doca : barco.blockPosition();
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(alvo, 0.6f, 0));
		}
		return true;
	}

	private static void embarcar(ServerLevel level, Villager v, AbstractBoat barco, Estado s) {
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.startRiding(barco, true, true);
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
	}

	/** Barco com baú sem pescador (pode ter o Pinto) mais próximo. */
	private static AbstractChestBoat barcoLivre(ServerLevel level, Vec3 perto, double raio) {
		AbstractChestBoat melhor = null;
		double best = raio * raio;
		for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(perto, perto).inflate(raio),
				e -> e instanceof AbstractChestBoat b && !b.hasPassenger(Pescador::ehPescador))) {
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

	/** Doca: madeira na borda da água com espaço para ficar em pé. Devolve onde pisar, ou null. */
	private static BlockPos doca(Level level, BlockPos agua) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = agua.relative(d);
			for (BlockPos m : new BlockPos[]{n, n.above()})
				if (madeira(level.getBlockState(m)) && livre(level, m.above()) && livre(level, m.above(2))) return m.above();
		}
		return null;
	}

	/** Busca em largura pela água até a doca mais próxima e monta o caminho até ela. */
	private static boolean planejarDoca(ServerLevel level, AbstractBoat barco, Estado s) {
		s.caminho = List.of(); s.idx = 0;
		BlockPos ini = BlockPos.containing(barco.getX(), barco.getY() - 0.3, barco.getZ());
		if (!agua(level, ini)) return false;
		Map<BlockPos, BlockPos> pai = new HashMap<>();
		ArrayDeque<BlockPos> fila = new ArrayDeque<>();
		fila.add(ini); pai.put(ini, ini);
		while (!fila.isEmpty() && pai.size() < BUSCA_MAX * 2) {
			BlockPos p = fila.poll();
			BlockPos pisa = doca(level, p);
			if (pisa != null) {
				s.doca = pisa;
				LinkedList<BlockPos> c = new LinkedList<>();
				for (BlockPos q = p; !q.equals(ini); q = pai.get(q)) c.addFirst(q);
				List<BlockPos> pontos = new ArrayList<>();
				for (int i = 1; i < c.size(); i += 2) pontos.add(c.get(i));
				if (!c.isEmpty() && (pontos.isEmpty() || !pontos.get(pontos.size() - 1).equals(p))) pontos.add(p);
				s.caminho = pontos;
				return true;
			}
			for (BlockPos q : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
				if (pai.containsKey(q) || q.distManhattan(s.casa) > LONGE_DE_CASA + 16 || !agua(level, q)) continue;
				pai.put(q, p); fila.add(q);
			}
		}
		return false;
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
		chapeu(level, v);
		Estado s = estados.get(v.getUUID());
		if (s != null) { largarBoia(s); s.modo = Modo.NAVEGANDO; s.caminho = List.of(); s.idx = 0; }
		return "§6Márcio no barco com baú" + (galinha == null ? " §7(sem galinha \"Pinto\" por perto)" : " com o Pinto em cima do baú");
	}

	// ---------- navegação (estilo peixe) ----------

	static boolean agua(Level level, BlockPos p) {
		return level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p.above()).isAir();
	}

	/** Busca em largura pela superfície da água e escolhe um destino aleatório alcançável. */
	private static void planejar(ServerLevel level, AbstractBoat barco, Estado s, RandomSource r) {
		s.caminho = List.of(); s.idx = 0;
		BlockPos ini = BlockPos.containing(barco.getX(), barco.getY() - 0.3, barco.getZ());
		if (!agua(level, ini)) return;
		Map<BlockPos, BlockPos> pai = new HashMap<>();
		Map<BlockPos, Integer> dist = new HashMap<>();
		ArrayDeque<BlockPos> fila = new ArrayDeque<>();
		fila.add(ini); dist.put(ini, 0);
		List<BlockPos> candidatos = new ArrayList<>();
		while (!fila.isEmpty() && dist.size() < BUSCA_MAX) {
			BlockPos p = fila.poll();
			int d = dist.get(p);
			if (d >= 8 && d <= 28) candidatos.add(p);
			for (BlockPos q : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
				if (dist.containsKey(q) || q.distManhattan(s.casa) > LONGE_DE_CASA || !agua(level, q)) continue;
				dist.put(q, d + 1); pai.put(q, p); fila.add(q);
			}
		}
		if (candidatos.isEmpty()) return;
		// prefere pontos com água dos lados (meio do riacho)
		candidatos.sort(Comparator.comparingInt(p -> -largura(level, p)));
		BlockPos alvo = candidatos.get(r.nextInt(Math.max(1, candidatos.size() / 2)));
		LinkedList<BlockPos> c = new LinkedList<>();
		for (BlockPos p = alvo; p != null && !p.equals(ini); p = pai.get(p)) c.addFirst(p);
		List<BlockPos> pontos = new ArrayList<>();
		for (int i = 1; i < c.size(); i += 2) pontos.add(c.get(i)); // um ponto a cada 2 blocos
		if (pontos.isEmpty() || !pontos.get(pontos.size() - 1).equals(alvo)) pontos.add(alvo);
		s.caminho = pontos;
	}

	private static int largura(Level level, BlockPos p) {
		int n = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (agua(level, p.offset(dx, 0, dz))) n++;
		return n;
	}
}

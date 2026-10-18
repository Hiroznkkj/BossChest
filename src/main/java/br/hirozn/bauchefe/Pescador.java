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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.chicken.Chicken;
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

	private enum Modo { NAVEGANDO, INDO_DOCA, PARADO_NOITE, EM_TERRA, VOLTANDO, PERSEGUINDO }

	/** Marca do barco do Márcio (tag da entidade) e do dono dele. */
	public static final String TAG_BARCO = "bauchefe.barco_do_marcio", TAG_DONO = "bauchefe.dono.";

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
		// vida: falas, olhar, animações, barco roubado
		int proximaFala = 200, proximaAnim = 1200, anim, animFim, olharAte, proximaQueixa;
		Entity olharEnt;
		Vec3 olharPonto;
		final Map<UUID, Integer> cumprimentou = new HashMap<>();
		UUID ladrao;
		boolean bebado; // de vez em quando acorda bêbado: fala bobagem e rema torto o dia todo
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
				vida(level, v, s);
				if (s.modo == Modo.PERSEGUINDO) { perseguir(level, v, s); continue; }
				if (s.modo == Modo.EM_TERRA || s.modo == Modo.VOLTANDO) { if (!emTerra(level, v, s)) it.remove(); continue; }
				if (!(v.getVehicle() instanceof AbstractBoat barco)) {
					largarBoia(s);
					if (roubado(level, v, s)) continue;
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
				AbstractChestBoat b = barcoDele(level, v, 64);
				if (b == null) b = barcoLivre(level, v.position(), 24);
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
		if (relogio % 100 == 0) marcarBarco(barco, v);
		s.timer--;
		if (s.boia != null) { pescar(level, v, barco, s, r); return; } // termina a pesca mesmo se anoitecer
		if (relogio < s.animFim) { animando(level, v, barco, s); return; }
		if (noite(level)) { noturno(level, v, barco, s); return; }
		if (s.modo != Modo.NAVEGANDO) { s.modo = Modo.NAVEGANDO; s.caminho = List.of(); s.idx = 0; }

		if (s.idx >= s.caminho.size()) {
			// parado entre um trecho e outro: às vezes faz uma animação à toa
			if (relogio >= s.proximaAnim && comecarAnim(level, v, barco, s, r, 0)) return;
			if (s.timer <= 0 && r.nextFloat() < 0.6f) { comecarPesca(level, v, barco, s, r); return; }
			planejar(level, barco, s, r);
			if (s.caminho.isEmpty()) { comecarPesca(level, v, barco, s, r); return; }
		}
		if (remar(level, v, barco, s, r)) { s.caminho = List.of(); s.idx = 0; }
		olhar(level, v, s, r);
	}

	/** Rema até o próximo ponto do caminho. Devolve true se encalhou. */
	private static boolean remar(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		BlockPos alvo = s.caminho.get(s.idx);
		double dx = alvo.getX() + 0.5 - barco.getX(), dz = alvo.getZ() + 0.5 - barco.getZ();
		if (dx * dx + dz * dz < 1.5) { s.idx++; return false; }

		// vira suavemente na direção do próximo ponto e rema para a frente
		float querido = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		if (s.bebado) querido += 35f * Mth.sin(relogio * 0.04f); // rema em zigue-zague
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
					if (s.fisgou) {
						boolean raro = peixeVoando(level, barco, b, r);
						falar(level, v, s, raro ? Falas.Tipo.RARO : Falas.Tipo.PEIXE, null, false);
					} else falar(level, v, s, Falas.Tipo.VAZIO, null, false);
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
	private static boolean peixeVoando(ServerLevel level, AbstractBoat barco, Vec3 de, RandomSource r) {
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
			s.modo = planejarDoca(level, barco, s) ? Modo.INDO_DOCA : Modo.PARADO_NOITE;
			falar(level, v, s, s.modo == Modo.INDO_DOCA ? Falas.Tipo.NOITE : Falas.Tipo.SEM_DOCA, null, false);
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
		segurarVara(v);
		AbstractBoat barco = s.barco != null && level.getEntity(s.barco) instanceof AbstractBoat b ? b : null;
		if (barco != null && !barco.hasPassenger(Pescador::ehPescador)) frear(barco, 0); // barco atracado
		if (s.modo == Modo.EM_TERRA) {
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

	// ---------- vida: falas, olhar, animações ----------

	/** Cumprimenta quem chega perto (uma vez a cada 5 min por jogador) e às vezes brinca com o Pinto. */
	private static void vida(ServerLevel level, Villager v, Estado s) {
		if (relogio % 20 != 0) return;
		RandomSource r = level.getRandom();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(v) > 8 * 8) continue;
			Integer ult = s.cumprimentou.get(p.getUUID());
			if (ult != null && relogio - ult < 6000) continue;
			if (falar(level, v, s, Falas.Tipo.OI, p.getName().getString(), false)) {
				s.cumprimentou.put(p.getUUID(), relogio);
				s.olharEnt = p;
				s.olharAte = relogio + 60;
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
		if (!forcar && relogio < s.proximaFala) return false;
		if (level.getNearestPlayer(v, 32) == null) return false;
		Falas.Linha l = Falas.frase(tipo, level.getRandom(), nome);
		Pacotes.Fala fala = new Pacotes.Fala(v.getId(), Falas.balao(l.texto()), l.som() == null ? "" : l.som());
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

package br.hirozn.bauchefe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Villagers com "pescador" no nome, dentro de um barco, passeiam pela água e pescam sozinhos.
 * Navegação no estilo da IA dos peixes: escolhe um ponto aleatório de água e vai até ele
 * por um caminho que só passa por água (busca em largura na superfície).
 */
public final class Pescador {
	private static final int LONGE_DE_CASA = 48, BUSCA_MAX = 900;
	private static final double VELOCIDADE = 0.07;

	private static final class Estado {
		BlockPos casa;
		List<BlockPos> caminho = List.of();
		int idx, timer, parado;
		boolean pescando;
		Vec3 boia, ultimaPos = Vec3.ZERO;
	}

	private static final Map<UUID, Estado> estados = new HashMap<>();
	private static final List<ItemEntity> peixesVoando = new ArrayList<>();
	private static int relogio;

	private Pescador() {}

	public static void registrar() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD) return;
			relogio++;
			peixesVoando.removeIf(e -> { if (e.tickCount > 25 || !e.isAlive()) { e.discard(); return true; } return false; });
			if (relogio % 100 == 0) procurar(level);
			for (Iterator<Map.Entry<UUID, Estado>> it = estados.entrySet().iterator(); it.hasNext(); ) {
				var en = it.next();
				Entity e = level.getEntity(en.getKey());
				if (!(e instanceof Villager v) || !(v.getVehicle() instanceof AbstractBoat barco) || !ehPescador(v)) {
					if (relogio % 100 == 0) it.remove();
					continue;
				}
				tick(level, v, barco, en.getValue());
			}
		});
	}

	static boolean ehPescador(Villager v) {
		return v.hasCustomName() && v.getCustomName().getString().toLowerCase(Locale.ROOT).contains("pescador");
	}

	private static void procurar(ServerLevel level) {
		for (Villager v : level.getEntities(EntityTypes.VILLAGER, v -> ehPescador(v) && v.getVehicle() instanceof AbstractBoat))
			estados.computeIfAbsent(v.getUUID(), u -> { Estado s = new Estado(); s.casa = v.blockPosition(); return s; });
	}

	// ---------- comportamento ----------

	private static void tick(ServerLevel level, Villager v, AbstractBoat barco, Estado s) {
		RandomSource r = level.getRandom();
		if (!v.getMainHandItem().is(Items.FISHING_ROD)) {
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
			v.setDropChance(EquipmentSlot.MAINHAND, 0f);
		}
		s.timer--;
		if (s.pescando) { pescar(level, v, barco, s, r); return; }

		if (s.idx >= s.caminho.size()) {
			if (s.timer <= 0 && r.nextFloat() < 0.6f) { comecarPesca(level, barco, s, r); return; }
			planejar(level, barco, s, r);
			if (s.caminho.isEmpty()) { comecarPesca(level, barco, s, r); return; }
		}
		BlockPos alvo = s.caminho.get(s.idx);
		double dx = alvo.getX() + 0.5 - barco.getX(), dz = alvo.getZ() + 0.5 - barco.getZ();
		if (dx * dx + dz * dz < 1.5) { s.idx++; return; }

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

		// encalhou? recalcula
		if (relogio % 40 == 0) {
			if (barco.position().distanceToSqr(s.ultimaPos) < 0.05) { s.caminho = List.of(); s.idx = 0; }
			s.ultimaPos = barco.position();
		}
	}

	private static void comecarPesca(ServerLevel level, AbstractBoat barco, Estado s, RandomSource r) {
		double rad = barco.getYRot() * Mth.DEG_TO_RAD;
		Vec3 frente = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad)), lado = new Vec3(frente.z, 0, -frente.x);
		s.boia = null;
		for (Vec3 d : new Vec3[]{lado, lado.scale(-1), frente, frente.scale(-1)}) {
			Vec3 p = barco.position().add(d.scale(3));
			BlockPos bp = BlockPos.containing(p.x, barco.getY() - 0.3, p.z);
			if (agua(level, bp)) { s.boia = new Vec3(p.x, bp.getY() + 0.9, p.z); break; }
		}
		if (s.boia == null) { s.timer = 200; planejar(level, barco, s, r); return; }
		s.pescando = true;
		s.timer = 300 + r.nextInt(300); // 15 a 30 s
		barco.setDeltaMovement(0, barco.getDeltaMovement().y, 0);
		level.playSound(null, barco.getX(), barco.getY(), barco.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5f, 0.4f / (r.nextFloat() * 0.4f + 0.8f));
		level.sendParticles(ParticleTypes.SPLASH, s.boia.x, s.boia.y, s.boia.z, 6, 0.1, 0, 0.1, 0.05);
	}

	private static void pescar(ServerLevel level, Villager v, AbstractBoat barco, Estado s, RandomSource r) {
		Vec3 m = barco.getDeltaMovement();
		barco.setDeltaMovement(m.x * 0.5, m.y, m.z * 0.5);
		Vec3 b = s.boia;
		v.getLookControl().setLookAt(b.x, b.y, b.z);
		if (relogio % 20 == 0) level.sendParticles(ParticleTypes.BUBBLE_POP, b.x, b.y - 0.1, b.z, 1, 0.05, 0, 0.05, 0);
		if (s.timer < 70 && s.timer > 20) { // peixe chegando: rastro de bolhas
			double t = (s.timer - 20) / 50.0;
			level.sendParticles(ParticleTypes.FISHING, b.x + t * 2, b.y - 0.05, b.z + t * 1.5, 1, 0, 0, 0, 0);
		}
		if (s.timer == 20) {
			level.playSound(null, b.x, b.y, b.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35f, 1 + (r.nextFloat() - r.nextFloat()) * 0.4f);
			level.sendParticles(ParticleTypes.SPLASH, b.x, b.y, b.z, 12, 0.2, 0, 0.2, 0.1);
			level.sendParticles(ParticleTypes.BUBBLE, b.x, b.y - 0.3, b.z, 8, 0.2, 0.1, 0.2, 0.1);
			if (r.nextFloat() < 0.6f) { // fisgou: o peixe salta até o barco
				ItemStack peixe = new ItemStack(r.nextFloat() < 0.7f ? Items.COD : Items.SALMON);
				ItemEntity ie = new ItemEntity(level, b.x, b.y + 0.2, b.z, peixe);
				Vec3 d = barco.position().add(0, 1.2, 0).subtract(b);
				ie.setDeltaMovement(d.x * 0.1, d.y * 0.1 + Math.sqrt(d.length()) * 0.08, d.z * 0.1);
				ie.setPickUpDelay(Integer.MAX_VALUE);
				level.addFreshEntity(ie);
				peixesVoando.add(ie);
				level.playSound(null, barco.getX(), barco.getY(), barco.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.5f, 1f);
			}
		}
		if (s.timer <= 0) {
			s.pescando = false;
			s.timer = 400 + r.nextInt(400); // rema 20 a 40 s antes da próxima pesca
			s.caminho = List.of(); s.idx = 0;
		}
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

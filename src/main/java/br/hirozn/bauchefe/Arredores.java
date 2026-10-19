package br.hirozn.bauchefe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * O que tem em volta de um NPC, lido do mundo (para qualquer NPC, não só o Márcio): fogo perto, fogueira acesa,
 * água para se apagar, o tamanho do quarto da cama dele e a armadura de alguém.
 */
public final class Arredores {
	private Arredores() {}

	/** Fogo de verdade a até 'raio' blocos (fogo, lava, fogueira acesa bem perto): o mais perto, ou null. */
	public static Vec3 calor(Level level, LivingEntity npc, double raio) {
		BlockPos c = npc.blockPosition();
		int r = Mth.ceil(raio);
		Vec3 melhor = null;
		double best = raio * raio;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, 2, r))) {
			BlockState st = level.getBlockState(p);
			boolean fogo = st.is(BlockTags.FIRE) || level.getFluidState(p).is(FluidTags.LAVA);
			boolean fogueira = st.is(BlockTags.CAMPFIRES) && st.getValue(CampfireBlock.LIT);
			if (!fogo && !fogueira) continue;
			Vec3 q = Vec3.atCenterOf(p);
			double d = q.distanceToSqr(npc.position());
			if (fogueira && d > 1.3 * 1.3) continue; // fogueira só esquenta demais se estiver colado nela
			if (d < best) { best = d; melhor = q; }
		}
		return melhor;
	}

	/** Fogueira acesa a até 'raio' blocos, ou null. */
	public static BlockPos fogueira(Level level, LivingEntity npc, int raio) {
		BlockPos c = npc.blockPosition(), melhor = null;
		double best = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-raio, -2, -raio), c.offset(raio, 2, raio))) {
			BlockState st = level.getBlockState(p);
			if (!st.is(BlockTags.CAMPFIRES) || !st.getValue(CampfireBlock.LIT)) continue;
			double d = p.distSqr(c);
			if (d < best) { best = d; melhor = p.immutable(); }
		}
		return melhor;
	}

	/** Lugar no chão perto da fogueira (1,5 a 2,5 blocos dela), o mais perto do NPC, virado para o fogo. */
	public static Lugares.Lugar pertoDoFogo(Level level, LivingEntity npc, BlockPos fogo) {
		Lugares.Lugar melhor = null;
		double best = Double.MAX_VALUE;
		for (BlockPos q : BlockPos.betweenClosed(fogo.offset(-3, -1, -3), fogo.offset(3, 1, 3))) {
			double df = Math.sqrt(Math.pow(q.getX() - fogo.getX(), 2) + Math.pow(q.getZ() - fogo.getZ(), 2));
			if (df < 1.5 || df > 2.6) continue;
			Double piso = Lugares.piso(level, q);
			if (piso == null) continue;
			Vec3 a = new Vec3(q.getX() + 0.5, piso, q.getZ() + 0.5);
			double d = a.distanceToSqr(npc.position());
			if (d >= best) continue;
			best = d;
			float yaw = (float) (Mth.atan2(-(fogo.getX() + 0.5 - a.x), fogo.getZ() + 0.5 - a.z) * Mth.RAD_TO_DEG);
			melhor = new Lugares.Lugar(Lugares.CHAO, a, yaw, a, null);
		}
		return melhor;
	}

	/** Água (superfície) a até 'raio' blocos para se apagar, ou null. */
	public static Vec3 agua(Level level, LivingEntity npc, int raio) {
		BlockPos c = npc.blockPosition();
		Vec3 melhor = null;
		double best = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-raio, -2, -raio), c.offset(raio, 1, raio))) {
			if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) continue;
			double d = p.distSqr(c);
			if (d < best) { best = d; melhor = Vec3.atBottomCenterOf(p); }
		}
		return melhor;
	}

	/**
	 * O chão do quarto onde fica a cama 'cama': os blocos em que dá para ficar em pé, ligados à cama, na mesma
	 * altura, sem passar de porta fechada ou parede (até 64 blocos e 8 de distância). Lista vazia se não deu.
	 */
	public static List<BlockPos> quarto(Level level, BlockPos cama) {
		List<BlockPos> chao = new ArrayList<>();
		Deque<BlockPos> fila = new ArrayDeque<>();
		Set<BlockPos> visto = new HashSet<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos q = cama.relative(d);
			if (Lugares.piso(level, q) != null) { fila.add(q); visto.add(q); }
		}
		while (!fila.isEmpty() && chao.size() < 64) {
			BlockPos p = fila.poll();
			Double piso = Lugares.piso(level, p);
			if (piso == null) continue;
			chao.add(p);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos q = p.relative(d);
				if (visto.contains(q) || q.distManhattan(cama) > 8) continue;
				visto.add(q);
				Double outro = Lugares.piso(level, q);
				if (outro != null && Math.abs(outro - piso) < 0.6) fila.add(q);
			}
		}
		return chao;
	}

	/** Os dois pontos mais distantes do chão do quarto (para medir de parede a parede), ou null. */
	public static BlockPos[] pontas(List<BlockPos> chao) {
		if (chao.size() < 2) return null;
		BlockPos a = chao.get(0), b = a;
		for (BlockPos p : chao) if (p.distSqr(chao.get(0)) > a.distSqr(chao.get(0))) a = p;
		for (BlockPos p : chao) if (p.distSqr(a) > b.distSqr(a)) b = p;
		return a.equals(b) ? null : new BlockPos[]{a, b};
	}

	/** Armadura de um ser vivo, para notar quando muda (ids das 4 peças). */
	public static String armadura(LivingEntity e) {
		StringBuilder sb = new StringBuilder();
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack it = e.getItemBySlot(s);
			sb.append(it.isEmpty() ? "-" : it.getItem().toString()).append(';');
		}
		return sb.toString();
	}

	/** Quão boa é a armadura (pelo material no nome das peças): para elogiar só quando melhora. */
	public static int valor(String armadura) {
		int v = 0;
		for (String p : armadura.split(";")) {
			if (p.equals("-") || p.isEmpty()) continue;
			v += p.contains("netherite") ? 6 : p.contains("diamond") ? 5 : p.contains("iron") ? 3 : p.contains("chainmail") ? 3
					: p.contains("gold") ? 2 : p.contains("turtle") ? 3 : 1;
		}
		return v;
	}
}

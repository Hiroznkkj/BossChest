package br.hirozn.bauchefe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Onde um NPC pode sentar, sentar no chão ou se encostar, achado pelo formato dos blocos (não por uma lista):
 * qualquer bloco cuja parte do meio tenha o topo entre 0,3 e 0,75 de altura, com espaço para a cabeça e um
 * lugar para ficar em pé na frente, serve de assento: degrau, laje, cama, cadeira e banco de mods. Se o bloco
 * tem uma parte mais alta de um lado (encosto), ele senta de costas para ela. Carrinho de mina vazio também serve.
 * A altura do assento é medida a partir do chão onde ele pisa: muito baixo não é cadeira, alto demais ele não sobe.
 */
public final class Lugares {
	public static final int CADEIRA = 1, CHAO = 2, ENCOSTO = 3, VEICULO = 4, DEITADO = 5;
	/** Altura do assento acima do chão onde ele pisa (blocos): entre isso ele senta; acima de 0,7 sobe num pulinho. */
	public static final double ALTURA_MIN = 0.25, ALTURA_MAX = 1.1;

	/** tipo, ponto do assento (superfície), para onde fica virado, onde fica em pé antes, e o veículo (se for). */
	public record Lugar(int tipo, Vec3 assento, float yaw, Vec3 pe, Entity veiculo) {}

	private Lugares() {}

	/** O assento livre mais perto (até 'raio' blocos), ou null. */
	public static Lugar assento(ServerLevel level, LivingEntity npc, int raio) {
		BlockPos c = npc.blockPosition();
		Lugar melhor = null;
		double best = Double.MAX_VALUE;
		for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) for (int dy = -2; dy <= 2; dy++) {
			BlockPos p = c.offset(dx, dy, dz);
			Lugar l = cadeira(level, p);
			if (l == null) continue;
			double d = l.pe().distanceToSqr(npc.position());
			if (d < best && !ocupado(level, l.assento())) { best = d; melhor = l; }
		}
		for (Entity e : level.getEntities(npc, npc.getBoundingBox().inflate(raio), e -> e instanceof Minecart && e.getPassengers().isEmpty())) {
			double d = e.distanceToSqr(npc) + 4; // bloco de assento ganha empate
			if (d < best) { best = d; melhor = new Lugar(VEICULO, e.position(), e.getYRot(), npc.position(), e); }
		}
		return melhor;
	}

	/** Esse bloco é um assento? Devolve o lugar (virado de costas para o encosto, se tiver), ou null. */
	public static Lugar cadeira(Level level, BlockPos p) {
		BlockState st = level.getBlockState(p);
		if (perigoso(st) || !level.getFluidState(p).isEmpty()) return null;
		VoxelShape sh = st.getCollisionShape(level, p);
		if (sh.isEmpty()) return null;
		double topo = -1, area = 0;
		for (AABB b : sh.toAabbs())
			if (b.minX < 0.45 && b.maxX > 0.55 && b.minZ < 0.45 && b.maxZ > 0.55 && b.maxY > topo) { topo = b.maxY; area = (b.maxX - b.minX) * (b.maxZ - b.minZ); } // cobre o meio com folga (o encosto do degrau começa no meio)
		if (topo < 0.3 || topo > 0.75 || area < 0.3) return null; // baixo demais, alto demais (bloco cheio) ou estreito
		double y = p.getY() + topo;
		// espaço para o corpo sentado (1,3 bloco acima do assento, no meio); o próprio bloco não conta (o encosto é dele)
		AABB corpo = new AABB(p.getX() + 0.3, y + 0.02, p.getZ() + 0.3, p.getX() + 0.7, y + 1.3, p.getZ() + 0.7);
		for (BlockPos q : new BlockPos[]{p.above(), p.above(2)})
			for (AABB b : level.getBlockState(q).getCollisionShape(level, q).toAabbs()) if (b.move(q).intersects(corpo)) return null;
		// encosto: uma parte do bloco bem mais alta que o assento, deslocada para um lado
		Direction encosto = null;
		for (AABB b : sh.toAabbs()) {
			if (b.maxY < topo + 0.3) continue;
			double cx = (b.minX + b.maxX) / 2 - 0.5, cz = (b.minZ + b.maxZ) / 2 - 0.5;
			if (Math.max(Math.abs(cx), Math.abs(cz)) < 0.15) continue;
			encosto = Math.abs(cx) > Math.abs(cz) ? (cx > 0 ? Direction.EAST : Direction.WEST) : (cz > 0 ? Direction.SOUTH : Direction.NORTH);
		}
		// onde ficar em pé: na frente (do lado contrário do encosto) ou em qualquer lado livre
		Direction[] lados = encosto != null ? new Direction[]{encosto.getOpposite()}
				: new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		for (Direction d : lados)
			for (int sobe = 0; sobe >= -1; sobe--) {
				BlockPos q = p.relative(d).above(sobe);
				Double piso = piso(level, q);
				if (piso == null) continue;
				double h = y - piso;
				if (h < ALTURA_MIN || h > ALTURA_MAX) continue;
				return new Lugar(CADEIRA, new Vec3(p.getX() + 0.5, y, p.getZ() + 0.5), d.toYRot(), new Vec3(q.getX() + 0.5, piso, q.getZ() + 0.5), null);
			}
		return null;
	}

	/** Lugar no chão, onde ele está, para sentar de perna cruzada virado para 'olhar' (ou null se não for chão firme). */
	public static Lugar chao(Level level, LivingEntity npc, Vec3 olhar) {
		Double piso = piso(level, npc.blockPosition());
		if (piso == null || Math.abs(piso - npc.getY()) > 0.3) return null;
		float yaw = olhar == null ? npc.getYRot() : (float) (Mth.atan2(-(olhar.x - npc.getX()), olhar.z - npc.getZ()) * Mth.RAD_TO_DEG);
		return new Lugar(CHAO, new Vec3(npc.getX(), piso, npc.getZ()), yaw, npc.position(), null);
	}

	/**
	 * Parede para se encostar (até 'raio' blocos): chão firme com alguma coisa atrás na altura das costas, medida
	 * pela face de verdade do bloco do lado: parede cheia, escada de mão, cerca, muro, vidro fino. Ele fica com as
	 * costas encostadas nessa face (no caso da escada de mão, praticamente dentro do bloco dela).
	 */
	public static Lugar encosto(Level level, LivingEntity npc, int raio) {
		BlockPos c = npc.blockPosition();
		Lugar melhor = null;
		double best = Double.MAX_VALUE;
		for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) for (int dy = -1; dy <= 1; dy++) {
			BlockPos q = c.offset(dx, dy, dz);
			Double piso = piso(level, q);
			if (piso == null || piso - q.getY() > 0.1) continue; // chão inteiro, sem degrau no pé
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos parede = q.relative(d);
				double perto = face(level, parede, d), cima = face(level, parede.above(), d);
				if (perto > 0.85 || cima > 0.85) continue; // nada na altura das costas
				double recua = 0.5 + Math.max(perto, cima) - 0.32; // do meio do bloco até a face, menos meia grossura do corpo
				Vec3 pe = new Vec3(q.getX() + 0.5 + d.getStepX() * recua, piso, q.getZ() + 0.5 + d.getStepZ() * recua);
				double dist = pe.distanceToSqr(npc.position());
				if (dist < best && !ocupado(level, pe)) { best = dist; melhor = new Lugar(ENCOSTO, pe, d.getOpposite().toYRot(), pe, null); }
			}
		}
		return melhor;
	}

	/**
	 * Quanto (0..1) a face do bloco 'b' que dá para o lado de onde viemos (contrário de 'd') está recuada, contando
	 * só partes de pelo menos meio bloco de altura; 9 se não tem nada.
	 */
	private static double face(Level level, BlockPos b, Direction d) {
		double melhor = 9;
		for (AABB x : level.getBlockState(b).getCollisionShape(level, b).toAabbs()) {
			if (x.maxY - x.minY < 0.5) continue;
			double n = switch (d) { case EAST -> x.minX; case WEST -> 1 - x.maxX; case SOUTH -> x.minZ; default -> 1 - x.maxZ; };
			melhor = Math.min(melhor, n);
		}
		return melhor;
	}

	/**
	 * Lugar para deitar de costas (até 'raio' blocos): dois blocos de chão firme na mesma altura, lado a lado,
	 * com espaço em cima; ele deita com a cabeça para o lado do segundo bloco.
	 */
	public static Lugar deitar(Level level, LivingEntity npc, int raio) {
		BlockPos c = npc.blockPosition();
		Lugar melhor = null;
		double best = Double.MAX_VALUE;
		for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) for (int dy = -1; dy <= 1; dy++) {
			BlockPos q = c.offset(dx, dy, dz);
			Double piso = piso(level, q);
			if (piso == null) continue;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				Double outro = piso(level, q.relative(d));
				if (outro == null || Math.abs(outro - piso) > 0.05) continue;
				// meio do corpo na divisa dos dois blocos, virado para 'd' (a cabeça vai para trás quando ele deita)
				Vec3 a = new Vec3(q.getX() + 0.5 + d.getStepX() * 0.5, piso, q.getZ() + 0.5 + d.getStepZ() * 0.5);
				double dist = a.distanceToSqr(npc.position());
				if (dist < best && !ocupado(level, a)) { best = dist; melhor = new Lugar(DEITADO, a, d.getOpposite().toYRot(), a, null); }
			}
		}
		return melhor;
	}

	/** Altura do chão onde dá para ficar em pé nesse bloco (pés e cabeça livres), ou null. */
	public static Double piso(Level level, BlockPos q) {
		if (!level.getFluidState(q).isEmpty()) return null;
		VoxelShape aqui = level.getBlockState(q).getCollisionShape(level, q);
		double base = aqui.isEmpty() ? -1 : aqui.max(Direction.Axis.Y);
		if (base > 0.55) return null; // tem coisa no lugar dos pés
		if (base <= 0) {
			BlockPos b = q.below();
			VoxelShape baixo = level.getBlockState(b).getCollisionShape(level, b);
			if (baixo.isEmpty() || baixo.max(Direction.Axis.Y) < 0.9) return null; // sem chão firme (ou buraco)
			base = 0;
		}
		double y = q.getY() + base;
		AABB corpo = new AABB(q.getX() + 0.3, y + 0.05, q.getZ() + 0.3, q.getX() + 0.7, y + 1.8, q.getZ() + 0.7);
		for (BlockPos r : new BlockPos[]{q, q.above(), q.above(2)})
			for (AABB b : level.getBlockState(r).getCollisionShape(level, r).toAabbs()) if (b.move(r).intersects(corpo)) return null;
		return y;
	}

	/** Já tem alguém sentado ali? */
	private static boolean ocupado(Level level, Vec3 p) {
		return !level.getEntities((Entity) null, new AABB(p, p).inflate(0.4), e -> e instanceof Assento).isEmpty();
	}

	/** Superfícies que parecem assento mas machucam ou não fazem sentido (fogueira, serra, bolo, magma...). */
	private static boolean perigoso(BlockState st) {
		return st.is(BlockTags.CAMPFIRES) || st.is(BlockTags.CANDLE_CAKES) || st.is(Blocks.CAKE) || st.is(Blocks.STONECUTTER)
				|| st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.HOPPER) || st.is(Blocks.SCULK_SENSOR) || st.is(Blocks.CALIBRATED_SCULK_SENSOR)
				|| st.is(Blocks.SCULK_SHRIEKER) || st.is(Blocks.DAYLIGHT_DETECTOR) || st.is(Blocks.ENCHANTING_TABLE) || st.is(Blocks.BREWING_STAND);
	}
}

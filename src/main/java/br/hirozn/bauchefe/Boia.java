package br.hirozn.bauchefe;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Boia do pescador. O FishingHook vanilla exige um Player como dono, então esta é própria.
 * No servidor ela fica parada no ponto da água e só troca de fase; no cliente cada fase é animada
 * localmente (arco, balanço, mordida, volta), a partir do momento em que a fase chegou.
 */
public class Boia extends Entity {
	private static final EntityDataAccessor<Integer> DONO = SynchedEntityData.defineId(Boia.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FASE = SynchedEntityData.defineId(Boia.class, EntityDataSerializers.INT);

	/** Só no cliente: boia de cada villager (id do dono), para o render dele achar a fase. */
	private static final Int2ObjectMap<Boia> porDono = new Int2ObjectOpenHashMap<>();

	// cliente: fase local e posição visual (a entidade em si fica parada no ponto da água)
	private int faseCli = AnimPesca.NADA, inicioCli;
	private Vec3 vis, visO, deFase, lancada;
	private boolean visivel, visivelO;

	public Boia(EntityType<? extends Boia> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(DONO, -1);
		b.define(FASE, AnimPesca.NADA);
	}

	public void setDono(Entity e) { entityData.set(DONO, e.getId()); }
	public int getFase() { return entityData.get(FASE); }
	public void setFase(int f) { entityData.set(FASE, f); }

	public LivingEntity getDono() {
		return level().getEntity(entityData.get(DONO)) instanceof LivingEntity l ? l : null;
	}

	/** Boia viva do villager com esse id (só no cliente), ou null. */
	public static Boia doDono(int id) {
		Boia b = porDono.get(id);
		if (b != null && b.isRemoved()) { porDono.remove(id); return null; }
		return b;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> acc) {
		super.onSyncedDataUpdated(acc);
		if (FASE.equals(acc) && level().isClientSide()) mudouFase();
	}

	private void mudouFase() {
		faseCli = getFase();
		inicioCli = tickCount;
		deFase = vis;
	}

	/** Ticks desde que a fase atual chegou no cliente. */
	public float tempoFase(float partial) { return tickCount - inicioCli + partial; }
	public int faseCliente() { return faseCli; }

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			if (getDono() == null || tickCount > 6000) discard(); // segurança: dono sumiu
			return;
		}
		LivingEntity dono = getDono();
		if (dono == null) return;
		porDono.put(dono.getId(), this);
		if (faseCli != getFase()) mudouFase();
		visO = vis; visivelO = visivel;
		animar(dono);
		if (visO == null) { visO = vis; visivelO = visivel; }
	}

	private void animar(LivingEntity dono) {
		int t = tickCount - inicioCli;
		Vec3 agua = position();
		Vec3 ponta = AnimPesca.ponta(dono, 1f, faseCli, t);
		switch (faseCli) {
			case AnimPesca.VOO -> {
				if (t < AnimPesca.SOLTA) { vis = ponta; visivel = false; lancada = null; break; }
				if (lancada == null) lancada = ponta;
				vis = AnimPesca.arco(lancada, agua, (t - AnimPesca.SOLTA) / (float) (AnimPesca.DUR_VOO - AnimPesca.SOLTA), 1.2);
				visivel = true;
			}
			case AnimPesca.BOIANDO -> {
				// quique ao cair na água e depois balanço suave
				double y = -0.18 * Math.exp(-t / 6.0) * Math.sin(t * 0.6) + 0.03 * Math.sin(t * 0.15);
				vis = suavizar(agua.add(0, y, 0), t, 3);
				visivel = true;
			}
			case AnimPesca.MORDIDA -> {
				// afunda de uma vez e treme enquanto o peixe puxa
				double y = -0.32 * AnimPesca.outCubic(t / 3f) + 0.06 * Math.sin(t * 1.8);
				vis = suavizar(agua.add(0, y, 0), t, 2);
				visivel = true;
			}
			case AnimPesca.PUXANDO -> {
				Vec3 de = deFase != null ? deFase : agua;
				visivel = t < AnimPesca.VOLTA;
				vis = visivel ? AnimPesca.arco(de, ponta, AnimPesca.inOutCubic(t / (float) AnimPesca.VOLTA), 0.8) : ponta;
			}
			default -> { vis = ponta; visivel = false; } // PREPARA: ainda na vara
		}
	}

	/** Nos primeiros ticks da fase, mistura com a posição de onde a boia estava (sem saltos). */
	private Vec3 suavizar(Vec3 alvo, int t, int ticks) {
		if (deFase == null || t >= ticks) return alvo;
		return deFase.lerp(alvo, AnimPesca.inOutSine(t / (float) ticks));
	}

	/** Posição visual interpolada (cliente), ou null se a boia ainda está na vara. */
	public Vec3 visual(float partial) {
		if (vis == null || !(visivel || visivelO)) return null;
		return visO == null ? vis : visO.lerp(vis, partial);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }

	@Override
	public boolean isPickable() { return false; }

	@Override
	public boolean shouldBeSaved() { return false; }

	@Override
	protected void readAdditionalSaveData(ValueInput in) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {}
}

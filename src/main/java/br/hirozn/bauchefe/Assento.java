package br.hirozn.bauchefe;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Assento invisível: fica na superfície onde o NPC senta (degrau, laje, cadeira de mod, o chão) e o NPC
 * monta nele, como fazem os mods de cadeira. Assim o cérebro vanilla não arrasta ele dali e o cliente sabe
 * exatamente a altura do assento (a posição desta entidade). Some sozinho quando fica vazio.
 */
public class Assento extends Entity {
	/** Lugares.CADEIRA, CHAO ou ENCOSTO: o cliente escolhe a pose por isso e pela altura até o chão. */
	private static final EntityDataAccessor<Integer> TIPO = SynchedEntityData.defineId(Assento.class, EntityDataSerializers.INT);
	/** De onde ele veio (para levantar e sair para o mesmo lado). */
	private Vec3 saida;

	public Assento(EntityType<? extends Assento> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static Assento em(ServerLevel level, int tipo, Vec3 pos, float yaw, Vec3 saida) {
		Assento a = new Assento(BauChefe.ASSENTO, level);
		a.snapTo(pos.x, pos.y, pos.z, yaw, 0);
		a.saida = saida;
		a.entityData.set(TIPO, tipo);
		level.addFreshEntity(a);
		return a;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(TIPO, Lugares.CADEIRA); }

	public int tipo() { return entityData.get(TIPO); }

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) return;
		if (tickCount > 5 && getPassengers().isEmpty()) { discard(); return; }
		for (Entity p : getPassengers()) if (p instanceof LivingEntity l) { l.setYBodyRot(getYRot()); l.yBodyRotO = getYRot(); }
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return Vec3.ZERO; // os pés (a origem do NPC) ficam na superfície do assento; o cliente senta o corpo
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }

	/** Sentado não dá para virar o corpo: a cabeça olha até 100° para cada lado. */
	@Override
	public void onPassengerTurned(Entity passenger) {
		if (passenger instanceof LivingEntity l) l.setYBodyRot(getYRot());
		float delta = Mth.wrapDegrees(passenger.getYRot() - getYRot()), lim = Mth.clamp(delta, -100f, 100f);
		passenger.yRotO += lim - delta;
		passenger.setYRot(passenger.getYRot() + lim - delta);
		passenger.setYHeadRot(passenger.getYRot());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return saida != null ? saida : super.getDismountLocationForPassenger(passenger);
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

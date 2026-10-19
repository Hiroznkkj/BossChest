package br.hirozn.bauchefe.mixin;

import br.hirozn.bauchefe.Pescador;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Barco com baú do Márcio: ele na frente e a galinha sentada em cima do baú (também quando ela fica sozinha à noite). */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
	// em cima do baú (topo do baú menos uns 4 px, senão a galinha fica flutuando); o baú fica meio bloco atrás do centro
	private static final double BAUCHEFE$TOPO_BAU = 0.6875, BAUCHEFE$ATRAS = -0.5;

	@Inject(method = "getPassengerAttachmentPoint", at = @At("RETURN"), cancellable = true)
	private void bauchefe$galinhaNoBau(Entity passenger, EntityDimensions dim, float scale, CallbackInfoReturnable<Vec3> cir) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		if (!Pescador.barcoDoMarcio(self)) return;
		float rad = -self.getYRot() * (float) (Math.PI / 180.0);
		if (passenger instanceof Chicken) cir.setReturnValue(new Vec3(0, BAUCHEFE$TOPO_BAU, BAUCHEFE$ATRAS).yRot(rad));
		else if (self.getPassengers().size() > 1) cir.setReturnValue(new Vec3(0, cir.getReturnValue().y, 0.15).yRot(rad));
	}

	// no barco do Márcio só entram ele, o Pinto e jogador "roubando" (o barco puxa bichos que encostam nele,
	// e com ele dormindo sobrava lugar para outra galinha)
	@Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
	private void bauchefe$soGalinha(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		if (Pescador.barcoDoMarcio(self) && !Pescador.podeEntrar(self, passenger)) cir.setReturnValue(false);
	}

	// a galinha fica virada para trás (olhando a água atrás do barco), e não de lado como os bichos no barco cheio
	@Inject(method = "calculatePassengerBodyYRot", at = @At("HEAD"), cancellable = true)
	private void bauchefe$galinhaPraFrente(Entity passenger, CallbackInfoReturnable<Float> cir) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		if (passenger instanceof Chicken && Pescador.barcoDoMarcio(self)) cir.setReturnValue(self.getYRot() + 180f);
	}
}

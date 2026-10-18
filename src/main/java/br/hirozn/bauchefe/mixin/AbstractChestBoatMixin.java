package br.hirozn.bauchefe.mixin;

import br.hirozn.bauchefe.Pescador;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Barco com baú aceita 2 passageiros só quando o primeiro é o pescador. */
@Mixin(AbstractChestBoat.class)
public abstract class AbstractChestBoatMixin {
	@Inject(method = "getMaxPassengers", at = @At("HEAD"), cancellable = true)
	private void bauchefe$doisNoBarco(CallbackInfoReturnable<Integer> cir) {
		if (Pescador.barcoDoMarcio((AbstractChestBoat) (Object) this)) cir.setReturnValue(2);
	}
}

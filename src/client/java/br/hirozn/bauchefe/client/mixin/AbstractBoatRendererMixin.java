package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Boia;
import br.hirozn.bauchefe.Pescador;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O barco balança enquanto o Márcio briga com um peixe difícil (usa a inclinação das bolhas da vanilla). */
@Mixin(AbstractBoatRenderer.class)
public abstract class AbstractBoatRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;Lnet/minecraft/client/renderer/entity/state/BoatRenderState;F)V", at = @At("TAIL"))
	private void bauchefe$balanca(AbstractBoat barco, BoatRenderState st, float partial, CallbackInfo ci) {
		for (Entity p : barco.getPassengers()) {
			if (!Pescador.ehPescador(p)) continue;
			Boia b = Boia.doDono(p.getId());
			if (b == null || b.faseCliente() != AnimPesca.LUTA) continue;
			float t = b.tempoFase(partial);
			st.bubbleAngle += AnimPesca.luta(t) * (7f * Mth.sin(t * 0.45f) + 2f * Mth.sin(t * 1.3f));
		}
	}
}

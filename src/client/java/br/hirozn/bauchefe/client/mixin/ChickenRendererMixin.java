package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.Asas;
import br.hirozn.bauchefe.Pescador;
import br.hirozn.bauchefe.client.EstadoPinto;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guarda no render state se é o Pinto sentado no baú e há quanto tempo ganha carinho. */
@Mixin(ChickenRenderer.class)
public abstract class ChickenRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/chicken/Chicken;Lnet/minecraft/client/renderer/entity/state/ChickenRenderState;F)V", at = @At("TAIL"))
	private void bauchefe$pinto(Chicken c, ChickenRenderState st, float partial, CallbackInfo ci) {
		EstadoPinto ep = (EstadoPinto) st;
		boolean sentado = Pescador.ehPinto(c) && c.isPassenger() && !c.isBaby();
		long desde = Asas.carinhoDesde(c.getId());
		float t = desde == 0 ? -1f : (c.level().getGameTime() - desde) + partial; // subtrai em long antes de virar float
		ep.bauchefe$set(sentado, t >= 0 && t < AnimMarcio.duracao(AnimMarcio.CARINHO) ? t : -1f, AnimMarcio.fase(c.getId()));
	}
}

package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.client.EstadoPescador;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Braços do pescador seguem a animação de pesca (arremesso, espera, mordida, puxada). */
@Mixin(VillagerModel.class)
public abstract class VillagerModelMixin {
	@Shadow @Final private ModelPart arms;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V", at = @At("TAIL"))
	private void bauchefe$bracos(VillagerRenderState st, CallbackInfo ci) {
		EstadoPescador ep = (EstadoPescador) st;
		if (!ep.bauchefe$pescador()) return;
		arms.xRot = AnimPesca.braco(ep.bauchefe$fase(), ep.bauchefe$tempo());
	}
}

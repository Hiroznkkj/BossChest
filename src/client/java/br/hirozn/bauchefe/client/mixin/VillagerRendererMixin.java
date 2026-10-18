package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Boia;
import br.hirozn.bauchefe.Pescador;
import br.hirozn.bauchefe.client.EstadoPescador;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pescador: guarda a fase da boia no render state e segura a vara na mão (não deitada nos braços). */
@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V", at = @At("TAIL"))
	private void bauchefe$pesca(Villager v, VillagerRenderState st, float partial, CallbackInfo ci) {
		EstadoPescador ep = (EstadoPescador) st;
		boolean pescador = Pescador.ehPescador(v) && !st.isBaby;
		if (!pescador) { ep.bauchefe$set(false, AnimPesca.NADA, 0); return; }
		Boia b = Boia.doDono(v.getId());
		if (b == null) ep.bauchefe$set(true, AnimPesca.NADA, 0);
		else ep.bauchefe$set(true, b.faseCliente(), b.tempoFase(partial));
		Minecraft.getInstance().getItemModelResolver().updateForLiving(st.heldItem, v.getMainHandItem(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, v);
	}
}

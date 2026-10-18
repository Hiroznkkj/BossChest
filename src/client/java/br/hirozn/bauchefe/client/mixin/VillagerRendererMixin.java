package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.Pescador;
import br.hirozn.bauchefe.client.DadosMarcio;
import br.hirozn.bauchefe.client.EstadoPescador;
import br.hirozn.bauchefe.client.MarcioCliente;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pescador: guarda pesca, animação, olhos e fala no render state e segura a vara na mão (não deitada nos braços). */
@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V", at = @At("TAIL"))
	private void bauchefe$pesca(Villager v, VillagerRenderState st, float partial, CallbackInfo ci) {
		DadosMarcio d = ((EstadoPescador) st).bauchefe$dados();
		d.pescador = false;
		d.fala = null;
		if (!Pescador.ehPescador(v) || st.isBaby) return;
		MarcioCliente.extrair(v, st, partial, d);
		Minecraft.getInstance().getItemModelResolver().updateForLiving(st.heldItem, v.getMainHandItem(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, v);
	}
}

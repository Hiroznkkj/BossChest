package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.Pescador;
import br.hirozn.bauchefe.client.MarcioRenderState;
import br.hirozn.bauchefe.client.MarcioRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** O pescador é desenhado pelo MarcioRenderer (modelo de jogador com a skin dele) no lugar do villager. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Unique private MarcioRenderer bauchefe$marcio;

	@Inject(method = "onResourceManagerReload", at = @At("TAIL"))
	private void bauchefe$criar(ResourceManager rm, CallbackInfo ci, @Local EntityRendererProvider.Context ctx) {
		bauchefe$marcio = new MarcioRenderer(ctx);
	}

	@Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void bauchefe$entidade(Entity e, CallbackInfoReturnable<EntityRenderer> cir) {
		if (bauchefe$marcio != null && e instanceof Villager v && !v.isBaby() && Pescador.ehPescador(v)) cir.setReturnValue(bauchefe$marcio);
	}

	@Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void bauchefe$estado(EntityRenderState st, CallbackInfoReturnable<EntityRenderer> cir) {
		if (bauchefe$marcio != null && st instanceof MarcioRenderState) cir.setReturnValue(bauchefe$marcio);
	}
}

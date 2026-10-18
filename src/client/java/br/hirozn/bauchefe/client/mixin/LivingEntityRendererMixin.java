package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.Balao;
import br.hirozn.bauchefe.client.DadosMarcio;
import br.hirozn.bauchefe.client.EstadoPescador;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Balão de fala do pescador, desenhado depois do corpo (fora da transformação do modelo). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
	private void bauchefe$balao(LivingEntityRenderState st, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera, CallbackInfo ci) {
		if (!(st instanceof VillagerRenderState vs)) return;
		DadosMarcio d = ((EstadoPescador) vs).bauchefe$dados();
		if (!d.pescador || d.fala == null) return;
		double altura = st.boundingBoxHeight;
		Balao.desenhar(d.fala, d.falaTempos, d.falaSeg, d.falaAlpha, altura, pose, out, camera, 0xF000F0);
	}
}

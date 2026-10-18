package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.EstadoPescador;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vara do pescador na mão direita, como o ItemInHandLayer do jogador (mesmas rotações),
 * presa ao "arms" do modelo, então acompanha a animação dos braços.
 */
@Mixin(CrossedArmsItemLayer.class)
public abstract class CrossedArmsItemLayerMixin {
	@Inject(method = "applyTranslation", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void bauchefe$varaNaMao(HoldingEntityRenderState st, PoseStack pose, CallbackInfo ci) {
		if (!(st instanceof VillagerRenderState vs) || !((EstadoPescador) vs).bauchefe$dados().pescador) return;
		EntityModel model = ((CrossedArmsItemLayer) (Object) this).getParentModel();
		((VillagerLikeModel) model).translateToArms(st, pose);
		pose.translate(-5 / 16f, 0f, 0f); // do centro do "arms" até o braço direito
		pose.rotateDegrees(Axis.XP, -90f);
		pose.rotateDegrees(Axis.YP, 180f);
		pose.translate(1 / 16f, 0.125f, -0.375f); // braço do villager é mais curto que o do jogador
		ci.cancel();
	}
}

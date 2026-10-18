package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.EstadoPescador;
import br.hirozn.bauchefe.client.PoseMarcio;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vara do pescador na mão direita, com as mesmas transformações do ItemInHandLayer do jogador. */
@Mixin(CrossedArmsItemLayer.class)
public abstract class CrossedArmsItemLayerMixin {
	@Inject(method = "applyTranslation", at = @At("HEAD"), cancellable = true)
	private void bauchefe$varaNaMao(HoldingEntityRenderState st, PoseStack pose, CallbackInfo ci) {
		if (!(st instanceof VillagerRenderState vs) || !((EstadoPescador) vs).bauchefe$dados().pescador) return;
		ModelPart root = ((Model<?>) ((CrossedArmsItemLayer<?, ?>) (Object) this).getParentModel()).root();
		if (!root.hasChild(PoseMarcio.BRACO_D)) return;
		root.translateAndRotate(pose);
		root.getChild(PoseMarcio.BRACO_D).translateAndRotate(pose);
		pose.rotateDegrees(Axis.XP, -90f);
		pose.rotateDegrees(Axis.YP, 180f);
		pose.translate(1 / 16f, 2 / 16f, -10 / 16f);
		ci.cancel();
	}
}

package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.AnimMarcio;
import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.client.EstadoPinto;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pinto sentado no baú (virado para trás): corpo baixo, pezinhos para a frente balançando,
 * cabeça balançando e às vezes bicando o ar. No carinho levanta a cabeça para a mão e se inclina.
 */
@Mixin(AdultChickenModel.class)
public abstract class AdultChickenModelMixin {
	@Shadow @Final private ModelPart head;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ChickenRenderState;)V", at = @At("TAIL"))
	private void bauchefe$sentado(ChickenRenderState st, CallbackInfo ci) {
		EstadoPinto ep = (EstadoPinto) st;
		if (!ep.bauchefe$sentado()) return;
		ModelPart root = ((AdultChickenModel) (Object) this).root();
		ModelPart pd = root.getChild("right_leg"), pe = root.getChild("left_leg");
		float age = st.ageInTicks, r = ep.bauchefe$fase();
		root.y += 2.5f; // agachada no baú
		pd.xRot = -1.25f + 0.25f * Mth.sin(age * 0.12f + r);
		pe.xRot = -1.25f + 0.25f * Mth.sin(age * 0.12f + r + 1.6f);
		// cabeça: balança devagar e, de vez em quando, umas bicadinhas no ar
		float bica = Mth.clamp(Mth.sin(age * 0.031f + r) * 4f - 3f, 0f, 1f);
		head.xRot += 0.08f * Mth.sin(age * 0.09f + r) + 0.3f * bica * Math.max(0f, Mth.sin(age * 0.6f));
		head.zRot += 0.12f * Mth.sin(age * 0.07f + r * 2f);
		float t = ep.bauchefe$carinho();
		if (t >= 0) {
			float w = AnimMarcio.peso(AnimMarcio.CARINHO, t);
			head.xRot = Mth.lerp(w, head.xRot, -0.5f);                 // empina a cabeça para a mão
			head.zRot = Mth.lerp(w, head.zRot, 0.3f * Mth.sin(t * 0.25f)); // e se esfrega de um lado para o outro
			root.y -= w * 0.8f * AnimPesca.inOutSine(Math.abs(Mth.sin(t * 0.2f))); // estufa toda feliz
		}
	}
}

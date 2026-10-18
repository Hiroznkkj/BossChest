package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.DadosMarcio;
import br.hirozn.bauchefe.client.EstadoPescador;
import br.hirozn.bauchefe.client.PoseMarcio;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pescador: troca os braços cruzados pelos dois braços e aplica a pose (PoseMarcio). */
@Mixin(VillagerModel.class)
public abstract class VillagerModelMixin {
	@Shadow @Final private ModelPart head;
	@Shadow @Final private ModelPart rightLeg;
	@Shadow @Final private ModelPart leftLeg;
	@Shadow @Final private ModelPart arms;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V", at = @At("TAIL"))
	private void bauchefe$bracos(VillagerRenderState st, CallbackInfo ci) {
		DadosMarcio d = ((EstadoPescador) st).bauchefe$dados();
		ModelPart root = ((VillagerModel) (Object) this).root();
		if (!root.hasChild(PoseMarcio.BRACO_D)) return; // modelo trocado por outro mod
		ModelPart bd = root.getChild(PoseMarcio.BRACO_D), be = root.getChild(PoseMarcio.BRACO_E);
		arms.visible = !d.pescador;
		bd.visible = d.pescador;
		be.visible = d.pescador;
		if (!d.pescador) return;
		PoseMarcio.aplicar(d, st.ageInTicks, root, head, root.getChild("body"), bd, be, rightLeg, leftLeg);
	}
}

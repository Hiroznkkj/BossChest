package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.PoseMarcio;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.npc.VillagerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dois braços separados no modelo do villager, no formato do pillager (pivô no ombro, 12 px),
 * mas com as UVs do próprio villager (braço em 44,22 e mão no pedaço dos braços cruzados em 40,38),
 * então a pele e as roupas da profissão continuam batendo. Só aparecem no pescador (VillagerModelMixin).
 */
@Mixin(VillagerModel.class)
public abstract class VillagerMeshMixin {

	@Inject(method = "createBodyModel", at = @At("RETURN"))
	private static void bauchefe$doisBracos(CallbackInfoReturnable<MeshDefinition> cir) {
		PartDefinition root = cir.getReturnValue().getRoot();
		root.addOrReplaceChild(PoseMarcio.BRACO_D, CubeListBuilder.create()
						.texOffs(44, 22).addBox(-3f, -2f, -2f, 4f, 8f, 4f)
						.texOffs(40, 38).addBox(-3f, 6f, -2f, 4f, 4f, 4f),
				PartPose.offset(-5f, 2f, 0f));
		root.addOrReplaceChild(PoseMarcio.BRACO_E, CubeListBuilder.create()
						.texOffs(44, 22).mirror().addBox(-1f, -2f, -2f, 4f, 8f, 4f)
						.texOffs(40, 38).mirror().addBox(-1f, 6f, -2f, 4f, 4f, 4f),
				PartPose.offset(5f, 2f, 0f));
	}
}

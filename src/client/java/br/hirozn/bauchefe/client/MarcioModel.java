package br.hirozn.bauchefe.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Modelo de jogador (braços largos) com a pose do Márcio por cima da humanoide. */
public class MarcioModel extends HumanoidModel<MarcioRenderState> {
	public MarcioModel(ModelPart root) {
		super(root);
		// a camada de fora (chapéu, mangas, calça, jaqueta) é desenhada em 3D pela Camada3D
		hat.visible = false;
		for (String[] p : new String[][]{{"body", "jacket"}, {"right_arm", "right_sleeve"}, {"left_arm", "left_sleeve"},
				{"right_leg", "right_pants"}, {"left_leg", "left_pants"}}) {
			ModelPart pai = root.getChild(p[0]);
			if (pai.hasChild(p[1])) pai.getChild(p[1]).visible = false;
		}
	}

	@Override
	public void setupAnim(MarcioRenderState st) {
		super.setupAnim(st);
		PoseMarcio.aplicar(st.bauchefe$dados(), st.ageInTicks, root, head, body, rightArm, leftArm, rightLeg, leftLeg);
	}
}

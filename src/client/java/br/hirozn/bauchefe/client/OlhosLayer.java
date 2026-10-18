package br.hirozn.bauchefe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Olhos vivos do pescador: redesenha os olhos por cima do rosto, com a pupila deslizando
 * conforme o olhar e a pálpebra descendo quando ele pisca. Usa pixels da própria textura
 * do villager (branco do olho, íris e pele), então combina com o rosto.
 */
public class OlhosLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
	private static final Identifier TEXTURA = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
	private static final RenderType RENDER = RenderTypes.entityCutout(TEXTURA);
	// pixels da villager.png (64x64): rosto vai de u 8 a 16, v 8 a 18; olhos na linha v 14
	private static final int BRANCO_U = 9, IRIS_U = 10, PELE_U = 8, OLHO_V = 14;
	// no espaço da cabeça (pixels): olhos de y -4 a -3, cada um com 2 px, de |x| 1 a 3; rosto em z -4
	private static final float OLHO_Y = -4f, ROSTO_Z = -4f;

	public OlhosLayer(RenderLayerParent<VillagerRenderState, VillagerModel> pai) {
		super(pai);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector out, int luz, VillagerRenderState st, float yRot, float xRot) {
		DadosMarcio d = ((EstadoPescador) st).bauchefe$dados();
		if (!d.pescador || st.isInvisible) return;
		pose.pushPose();
		VillagerModel m = getParentModel();
		m.root().translateAndRotate(pose);
		m.getHead().translateAndRotate(pose);
		// um pouco à frente do rosto; mais longe da câmera precisa de mais folga para não piscar (z-fighting)
		float z = ROSTO_Z - 0.03f - d.distancia / 400f;
		float px = d.pupilaX, py = d.pupilaY, lid = d.palpebra;
		out.submitCustomGeometry(pose, RENDER, (p, buf) -> {
			for (int lado = -1; lado <= 1; lado += 2) {
				float x0 = lado < 0 ? -3f : 1f; // olho de x0 a x0+2
				quad(buf, p, luz, x0, OLHO_Y, x0 + 2, OLHO_Y + 1, z, BRANCO_U);
				// pupila de 1 px: na parte de dentro do olho e desliza até meio pixel para cada lado
				float cx = lado * 1.5f + px, cy = OLHO_Y + 0.5f + py;
				float x1 = Math.max(x0, cx - 0.5f), x2 = Math.min(x0 + 2, cx + 0.5f);
				float y1 = Math.max(OLHO_Y, cy - 0.5f), y2 = Math.min(OLHO_Y + 1, cy + 0.5f);
				if (x2 > x1 && y2 > y1) quad(buf, p, luz, x1, y1, x2, y2, z - 0.01f, IRIS_U);
				// pálpebra desce de cima
				if (lid > 0.02f) quad(buf, p, luz, x0, OLHO_Y, x0 + 2, OLHO_Y + lid, z - 0.02f, PELE_U);
			}
		});
		pose.popPose();
	}

	/** Retângulo (em pixels da cabeça) pintado com um pixel da textura. */
	private static void quad(VertexConsumer b, PoseStack.Pose p, int luz, float x1, float y1, float x2, float y2, float z, int u) {
		float uu = (u + 0.5f) / 64f, vv = (OLHO_V + 0.5f) / 64f;
		float s = 1 / 16f;
		v(b, p, luz, x1 * s, y2 * s, z * s, uu, vv);
		v(b, p, luz, x2 * s, y2 * s, z * s, uu, vv);
		v(b, p, luz, x2 * s, y1 * s, z * s, uu, vv);
		v(b, p, luz, x1 * s, y1 * s, z * s, uu, vv);
	}

	private static void v(VertexConsumer b, PoseStack.Pose p, int luz, float x, float y, float z, float u, float v) {
		b.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, 0f, 0f, -1f);
	}
}

package br.hirozn.bauchefe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Olhos vivos do Márcio (ideia dos olhos do Fresh Animations). Na skin dele os olhos são dois traços escuros
 * de 2x1 pixel na linha 12 (rosto: colunas 9-10 e 13-14). Esta camada cobre cada olho com a pele de baixo,
 * redesenha o traço deslocado para onde ele olha (até meio pixel, chegando antes da cabeça) e desce a
 * pálpebra (a cor da pele de cima) quando ele pisca ou aperta os olhos.
 */
public class OlhosMarcio extends RenderLayer<MarcioRenderState, MarcioModel> {
	private static final RenderType RENDER = RenderTypes.entityCutout(MarcioRenderer.SKIN);
	// pixels da skin: olho na linha 12, pele acima (11) e abaixo (13); rosto começa na coluna 8
	private static final int OLHO_V = 12, CIMA_V = 11, BAIXO_V = 13;
	private static final int[] COLUNAS = {9, 13}; // primeira coluna de cada olho
	// no espaço da cabeça (pixels): linha 12 da skin = y -4 a -3; coluna c = x (c - 12); rosto em z -4
	private static final float OLHO_Y = -4f, ROSTO_Z = -4f;

	public OlhosMarcio(RenderLayerParent<MarcioRenderState, MarcioModel> pai) {
		super(pai);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector out, int luz, MarcioRenderState st, float yRot, float xRot) {
		DadosMarcio d = st.bauchefe$dados();
		if (st.isInvisible) return;
		pose.pushPose();
		MarcioModel m = getParentModel();
		m.root().translateAndRotate(pose);
		m.head.translateAndRotate(pose);
		float z = ROSTO_Z - 0.03f - d.distancia / 400f; // mais longe, mais folga contra o z-fighting
		float ox = d.olhoX, oy = d.olhoY, lid = d.palpebra;
		out.submitCustomGeometry(pose, RENDER, (p, buf) -> {
			for (int c0 : COLUNAS) {
				float x0 = c0 - 12;
				for (int i = 0; i < 2; i++) quad(buf, p, luz, x0 + i, OLHO_Y, x0 + i + 1, OLHO_Y + 1, z, c0 + i, BAIXO_V); // apaga o olho
				// o traço, deslocado (sem sair do lugar do olho)
				float xa = Math.max(x0 - 0.5f, x0 + ox), ya = OLHO_Y + Math.max(-0.3f, Math.min(0.3f, oy));
				quad(buf, p, luz, xa, ya, xa + 2, ya + 1, z - 0.01f, c0, OLHO_V);
				// pálpebra descendo de cima
				if (lid > 0.02f)
					for (int i = 0; i < 2; i++) quad(buf, p, luz, x0 + i - 0.5f * (i == 0 ? 1 : 0), OLHO_Y - 0.3f, x0 + i + 1 + 0.5f * i, OLHO_Y + 1.3f * lid, z - 0.02f, c0 + i, CIMA_V);
			}
		});
		pose.popPose();
	}

	/** Retângulo (em pixels da cabeça) pintado com um pixel da skin. */
	private static void quad(VertexConsumer b, PoseStack.Pose p, int luz, float x1, float y1, float x2, float y2, float z, int pu, int pv) {
		float u = (pu + 0.5f) / 64f, v = (pv + 0.5f) / 64f, s = 1 / 16f;
		vert(b, p, luz, x1 * s, y2 * s, z * s, u, v);
		vert(b, p, luz, x2 * s, y2 * s, z * s, u, v);
		vert(b, p, luz, x2 * s, y1 * s, z * s, u, v);
		vert(b, p, luz, x1 * s, y1 * s, z * s, u, v);
	}

	private static void vert(VertexConsumer b, PoseStack.Pose p, int luz, float x, float y, float z, float u, float v) {
		b.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, 0f, 0f, -1f);
	}
}

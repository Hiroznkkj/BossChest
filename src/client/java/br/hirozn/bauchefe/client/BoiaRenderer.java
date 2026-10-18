package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Boia;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Boia do pescador: mesma textura e linha do FishingHookRenderer, saindo da ponta da vara do villager. */
public class BoiaRenderer extends EntityRenderer<Boia, BoiaRenderer.Estado> {
	private static final Identifier TEXTURA = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutCull(TEXTURA);

	public static class Estado extends EntityRenderState {
		boolean visivel;
		Vec3 desloc = Vec3.ZERO, linha = Vec3.ZERO; // da entidade até a boia visual, e da boia até a ponta da vara
	}

	public BoiaRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public Estado createRenderState() { return new Estado(); }

	@Override
	protected boolean affectedByCulling(Boia e) { return false; } // a boia visual sai de perto da entidade

	@Override
	public void extractRenderState(Boia e, Estado st, float partial) {
		super.extractRenderState(e, st, partial);
		LivingEntity dono = e.getDono();
		Vec3 vis = e.visual(partial);
		st.visivel = dono != null && vis != null;
		if (!st.visivel) return;
		st.desloc = vis.subtract(e.getPosition(partial));
		Vec3 ponta = AnimPesca.ponta(dono, partial, e.faseCliente(), e.tempoFase(partial), e.lutou());
		st.linha = ponta.subtract(vis.add(0, 0.25, 0));
	}

	@Override
	public void submit(Estado st, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		if (st.visivel) {
			pose.pushPose();
			pose.translate(st.desloc.x, st.desloc.y, st.desloc.z);
			pose.pushPose();
			pose.scale(0.5f, 0.5f, 0.5f);
			pose.rotate(camera.orientation);
			int luz = st.lightCoords;
			out.submitCustomGeometry(pose, RENDER_TYPE, (p, buf) -> {
				vertice(buf, p, luz, 0f, 0, 0, 1);
				vertice(buf, p, luz, 1f, 0, 1, 1);
				vertice(buf, p, luz, 1f, 1, 1, 0);
				vertice(buf, p, luz, 0f, 1, 0, 0);
			});
			pose.popPose();
			float xa = (float) st.linha.x, ya = (float) st.linha.y, za = (float) st.linha.z;
			float largura = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
			out.submitCustomGeometry(pose, RenderTypes.lines(), (p, buf) -> {
				for (int i = 0; i < 16; i++) {
					float a0 = i / 16f, a1 = (i + 1) / 16f;
					linha(xa, ya, za, buf, p, a0, a1, largura);
					linha(xa, ya, za, buf, p, a1, a0, largura);
				}
			});
			pose.popPose();
		}
		super.submit(st, pose, out, camera);
	}

	private static void vertice(VertexConsumer b, PoseStack.Pose p, int luz, float x, int y, int u, int v) {
		b.addVertex(p, x - 0.5f, y - 0.5f, 0f).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, 0f, 1f, 0f);
	}

	/** Linha com a curva (barriga) da vanilla. */
	private static void linha(float xa, float ya, float za, VertexConsumer b, PoseStack.Pose p, float a, float prox, float largura) {
		float x = xa * a, y = ya * (a * a + a) * 0.5f + 0.25f, z = za * a;
		float nx = xa * prox - x, ny = ya * (prox * prox + prox) * 0.5f + 0.25f - y, nz = za * prox - z;
		float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		b.addVertex(p, x, y, z).setColor(0xFF000000).setNormal(p, nx / len, ny / len, nz / len).setLineWidth(largura);
	}
}

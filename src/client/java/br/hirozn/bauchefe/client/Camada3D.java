package br.hirozn.bauchefe.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import br.hirozn.bauchefe.BauChefe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Camada de fora da skin em 3D: cada pixel visível do chapéu, mangas e calça vira um cubinho
 * de 1 pixel saindo da peça (como o mod 3D Skin Layers, de tr7zw, mas com código próprio).
 * Os cubinhos são montados uma vez, lendo a skin, e acompanham as peças animadas.
 */
public class Camada3D extends RenderLayer<MarcioRenderState, MarcioModel> {
	private static final RenderType RENDER = RenderTypes.entityCutout(MarcioRenderer.SKIN);

	/** Uma peça da skin: caixa da peça (pixels, no espaço dela) e onde fica a camada de fora na textura. */
	private record Peca(float x0, float y0, float z0, int w, int h, int d, int u, int v, float dentro, float fora) {}

	// peças do modelo de jogador (braços largos)
	private static final Peca CABECA = new Peca(-4, -8, -4, 8, 8, 8, 32, 0, 0.3f, 0.8f);
	private static final Peca CORPO = new Peca(-4, 0, -2, 8, 12, 4, 16, 32, 0.2f, 0.6f);
	private static final Peca BRACO_D = new Peca(-3, -2, -2, 4, 12, 4, 40, 32, 0.2f, 0.6f);
	private static final Peca BRACO_E = new Peca(-1, -2, -2, 4, 12, 4, 48, 48, 0.2f, 0.6f);
	private static final Peca PERNA_D = new Peca(-2, 0, -2, 4, 12, 4, 0, 32, 0.2f, 0.6f);
	private static final Peca PERNA_E = new Peca(-2, 0, -2, 4, 12, 4, 0, 48, 0.2f, 0.6f);

	/** Cubinhos de cada peça: {x0,y0,z0,x1,y1,z1,u,v} em pixels. Null até ler a skin. */
	private static float[][][] voxels;

	public Camada3D(RenderLayerParent<MarcioRenderState, MarcioModel> pai) {
		super(pai);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector out, int luz, MarcioRenderState st, float yRot, float xRot) {
		if (st.isInvisible) return;
		if (voxels == null) voxels = montar();
		MarcioModel m = getParentModel();
		ModelPart[] partes = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
		for (int i = 0; i < partes.length; i++) {
			float[][] vs = voxels[i];
			if (vs.length == 0 || !partes[i].visible) continue;
			pose.pushPose();
			m.root().translateAndRotate(pose);
			partes[i].translateAndRotate(pose);
			out.submitCustomGeometry(pose, RENDER, (p, buf) -> {
				for (float[] c : vs) cubo(buf, p, luz, c);
			});
			pose.popPose();
		}
	}

	/** Lê a skin e monta os cubinhos de todas as peças. */
	private static float[][][] montar() {
		Peca[] pecas = {CABECA, CORPO, BRACO_D, BRACO_E, PERNA_D, PERNA_E};
		float[][][] r = new float[pecas.length][][];
		try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(MarcioRenderer.SKIN).open();
		     NativeImage img = NativeImage.read(in)) {
			for (int i = 0; i < pecas.length; i++) r[i] = voxelsDa(img, pecas[i]);
		} catch (Exception e) {
			BauChefe.LOG.warn("[Baú Chefe] Não deu para ler a skin do Márcio para a camada 3D", e);
			for (int i = 0; i < pecas.length; i++) r[i] = new float[0][];
		}
		return r;
	}

	/**
	 * Para cada face da caixa, segue o mesmo recorte de UV do ModelPart.Cube (topo, base, lados, frente, trás)
	 * e cria um cubinho por pixel opaco, encostado na face e saindo para fora.
	 */
	private static float[][] voxelsDa(NativeImage img, Peca p) {
		List<float[]> l = new ArrayList<>();
		int u = p.u, v = p.v, w = p.w, h = p.h, d = p.d;
		float x0 = p.x0, y0 = p.y0, z0 = p.z0, x1 = x0 + w, y1 = y0 + h, z1 = z0 + d, a = p.dentro, f = p.fora;
		// topo (y mínimo; no modelo y cresce para baixo): u+d..u+d+w, v..v+d
		for (int i = 0; i < w; i++) for (int j = 0; j < d; j++)
			add(l, img, u + d + i, v + j, x0 + i, y0 - f, z1 - j - 1, x0 + i + 1, y0 + a, z1 - j);
		// base (y máximo): u+d+w..u+d+2w, v..v+d
		for (int i = 0; i < w; i++) for (int j = 0; j < d; j++)
			add(l, img, u + d + w + i, v + j, x0 + i, y1 - a, z1 - j - 1, x0 + i + 1, y1 + f, z1 - j);
		for (int j = 0; j < h; j++) {
			int vv = v + d + j;
			float ya = y0 + j, yb = y0 + j + 1;
			// lado x mínimo: u..u+d, de z máximo para z mínimo
			for (int i = 0; i < d; i++) add(l, img, u + i, vv, x0 - f, ya, z1 - i - 1, x0 + a, yb, z1 - i);
			// frente (z mínimo): u+d..u+d+w
			for (int i = 0; i < w; i++) add(l, img, u + d + i, vv, x0 + i, ya, z0 - f, x0 + i + 1, yb, z0 + a);
			// lado x máximo: u+d+w..u+2d+w, de z mínimo para z máximo
			for (int i = 0; i < d; i++) add(l, img, u + d + w + i, vv, x1 - a, ya, z0 + i, x1 + f, yb, z0 + i + 1);
			// trás (z máximo): u+2d+w..u+2d+2w, de x máximo para x mínimo
			for (int i = 0; i < w; i++) add(l, img, u + 2 * d + w + i, vv, x1 - i - 1, ya, z1 - a, x1 - i, yb, z1 + f);
		}
		return l.toArray(new float[0][]);
	}

	private static void add(List<float[]> l, NativeImage img, int pu, int pv, float xa, float ya, float za, float xb, float yb, float zb) {
		if (pu < 0 || pv < 0 || pu >= img.getWidth() || pv >= img.getHeight()) return;
		if ((img.getPixel(pu, pv) >>> 24) < 128) return; // transparente: sem cubinho
		l.add(new float[]{xa, ya, za, xb, yb, zb, (pu + 0.5f) / 64f, (pv + 0.5f) / 64f});
	}

	/** Cubinho com as 6 faces pintadas com o pixel da skin. */
	private static void cubo(VertexConsumer b, PoseStack.Pose p, int luz, float[] c) {
		float s = 1 / 16f, xa = c[0] * s, ya = c[1] * s, za = c[2] * s, xb = c[3] * s, yb = c[4] * s, zb = c[5] * s, u = c[6], v = c[7];
		quad(b, p, luz, u, v, 0, 0, -1, xa, ya, za, xb, ya, za, xb, yb, za, xa, yb, za);
		quad(b, p, luz, u, v, 0, 0, 1, xa, ya, zb, xa, yb, zb, xb, yb, zb, xb, ya, zb);
		quad(b, p, luz, u, v, -1, 0, 0, xa, ya, za, xa, yb, za, xa, yb, zb, xa, ya, zb);
		quad(b, p, luz, u, v, 1, 0, 0, xb, ya, za, xb, ya, zb, xb, yb, zb, xb, yb, za);
		quad(b, p, luz, u, v, 0, -1, 0, xa, ya, za, xa, ya, zb, xb, ya, zb, xb, ya, za);
		quad(b, p, luz, u, v, 0, 1, 0, xa, yb, za, xb, yb, za, xb, yb, zb, xa, yb, zb);
	}

	private static void quad(VertexConsumer b, PoseStack.Pose p, int luz, float u, float v, float nx, float ny, float nz,
	                         float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
		b.addVertex(p, x1, y1, z1).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, nx, ny, nz);
		b.addVertex(p, x2, y2, z2).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, nx, ny, nz);
		b.addVertex(p, x3, y3, z3).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, nx, ny, nz);
		b.addVertex(p, x4, y4, z4).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, nx, ny, nz);
	}
}

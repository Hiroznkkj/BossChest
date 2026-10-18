package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.BauChefe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Balão de fala de quadrinho acima da cabeça. Visual e textura portados do mod
 * Notable Bubble Text (NBT), de Mrbysco (licença MIT), adaptados para a API de render do 26.3.
 */
public final class Balao {
	private static final RenderType RENDER = RenderTypes.entityTranslucent(BauChefe.id("textures/entity/balao.png"));
	private static final int LARGURA_MAX = 120, TEX = 32;

	private Balao() {}

	/**
	 * 'palavras': a fala em palavras; 'tempos': segundo em que cada uma é dita (null = sem áudio);
	 * 'seg': quanto já passou. Efeito karaokê: cada palavra começa cinza e escurece até o preto
	 * quando ele fala. 'altura': topo da entidade (blocos).
	 */
	public static void desenhar(String[] palavras, float[] tempos, float seg, float alpha, double altura,
	                            PoseStack pose, SubmitNodeCollector out, CameraRenderState camera, int luz) {
		if (alpha <= 0.02f || palavras.length == 0) return;
		Font font = Minecraft.getInstance().font;
		int espaco = font.width(" ");
		int[] larg = new int[palavras.length], linha = new int[palavras.length], xPal = new int[palavras.length];
		List<Integer> largLinha = new ArrayList<>();
		int x = 0, n = 0;
		for (int i = 0; i < palavras.length; i++) {
			larg[i] = font.width(palavras[i]);
			if (x > 0 && x + espaco + larg[i] > LARGURA_MAX) { largLinha.add(x); n++; x = 0; }
			if (x > 0) x += espaco;
			linha[i] = n;
			xPal[i] = x;
			x += larg[i];
		}
		largLinha.add(x);
		int w = 0;
		for (int l : largLinha) w = Math.max(w, l);
		int linhas = largLinha.size(), h = font.lineHeight * linhas;
		float a = alpha;
		// cresce um pouquinho ao aparecer
		float esc = 0.025f * (0.85f + 0.15f * Mth.clamp(alpha * 2f, 0f, 1f));

		pose.pushPose();
		// altura fixa (já com o espaço do nome), para o balão não pular quando o nome aparece
		pose.translate(0.0, altura + 0.8 + 0.1 * linhas, 0.0);
		pose.rotate(camera.orientation);
		pose.scale(esc, -esc, esc);
		final int fw = w, fh = h;
		out.submitCustomGeometry(pose, RENDER, (p, buf) -> moldura(buf, p, fw, fh, a, luz));
		// texto no mesmo plano do balão; o polygon offset puxa ele para a frente (como nas placas)
		int alfa = Math.max(26, (int) (a * 255)) << 24;
		for (int i = 0; i < palavras.length; i++) {
			// 0 = ainda não falada (cinza), 1 = já falada (preto); a troca leva 0,3 s
			float dita = tempos == null || i >= tempos.length ? 1f : Mth.clamp((seg - tempos[i]) / 0.3f, 0f, 1f);
			int cinza = (int) Mth.lerp(AnimPesca.inOutSine(dita), 0x9A, 0x00);
			int cor = alfa | cinza << 16 | cinza << 8 | cinza;
			float lx = -largLinha.get(linha[i]) / 2f + xPal[i];
			out.submitText(pose, lx, -h / 2f + linha[i] * font.lineHeight, Component.literal(palavras[i]).getVisualOrderText(), false,
					Font.DisplayMode.POLYGON_OFFSET, luz, cor, 0, 0);
		}
		pose.popPose();
	}

	/** Moldura de 9 partes com rabinho (mesmo recorte da textura do NBT). Centrada em (0,0). */
	private static void moldura(VertexConsumer b, PoseStack.Pose p, int w, int h, float a, int luz) {
		float x0 = -w / 2f, y0 = -h / 2f;
		float m = h - 6;
		img(b, p, x0, y0 - 3, 7, 0, 1, 6, w, 6, a, luz);                 // topo
		img(b, p, x0 - 6, y0 + 3, 0, 7, 6, 1, 6, m, a, luz);             // esquerda
		img(b, p, x0, y0 + 3, 7, 7, 1, 1, w, m, a, luz);                 // meio
		img(b, p, x0 + w, y0 + 3, 9, 7, 6, 1, 6, m, a, luz);             // direita
		img(b, p, x0, y0 + 3 + m, 7, 9, 1, 6, w, 6, a, luz);             // base
		img(b, p, x0 - 6, y0 - 3, 0, 0, 6, 6, 6, 6, a, luz);             // cantos
		img(b, p, x0 + w, y0 - 3, 9, 0, 6, 6, 6, 6, a, luz);
		img(b, p, x0 - 6, y0 + 3 + m, 0, 9, 6, 6, 6, 6, a, luz);
		img(b, p, x0 + w, y0 + 3 + m, 9, 9, 6, 6, 6, 6, a, luz);
		img(b, p, -2.5f, y0 + 3 + m + 5, 0, 16, 5, 5, 5, 5, a, luz);     // rabinho apontando para a cabeça
	}

	private static void img(VertexConsumer b, PoseStack.Pose p, float x, float y, float u, float v, float uw, float vh, float rw, float rh, float a, int luz) {
		float s = 1f / TEX;
		vert(b, p, x, y + rh, u * s, (v + vh) * s, a, luz);
		vert(b, p, x + rw, y + rh, (u + uw) * s, (v + vh) * s, a, luz);
		vert(b, p, x + rw, y, (u + uw) * s, v * s, a, luz);
		vert(b, p, x, y, u * s, v * s, a, luz);
	}

	private static void vert(VertexConsumer b, PoseStack.Pose p, float x, float y, float u, float v, float a, int luz) {
		b.addVertex(p, x, y, 0f).setColor(1f, 1f, 1f, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(p, 0f, 0f, 1f);
	}
}

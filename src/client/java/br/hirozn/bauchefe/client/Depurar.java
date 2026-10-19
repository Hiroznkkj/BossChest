package br.hirozn.bauchefe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * /marcioanim colisao: desenha o que o resolvedor vê. A caixa de cada parte do corpo (verde, ou vermelha se
 * estiver entrando num bloco), as caixas de colisão dos blocos em volta (cinza), cada apoio (linha amarela do
 * ponto do corpo até a superfície que ele achou) e, em cima da cabeça, quanto do corpo estava dentro de blocos
 * antes e depois de resolver.
 */
public final class Depurar {
	private Depurar() {}

	public static boolean ligado() { return Resolver.retratar; }

	public static void ligar(boolean sim) { Resolver.retratar = sim; Resolver.retratos.clear(); }

	/** Chamado na hora de juntar os desenhos de debug do quadro (LevelExtractorMixin). */
	public static void desenhar() {
		if (!Resolver.retratar) return;
		try { desenharTudo(); }
		catch (RuntimeException e) { // nunca derruba o jogo por causa do debug
			br.hirozn.bauchefe.BauChefe.LOG.warn("[Baú Chefe] Não deu para desenhar as colisões", e);
			ligar(false);
		}
	}

	private static void desenharTudo() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		for (var en : Resolver.retratos.int2ObjectEntrySet()) {
			Entity ent = mc.level.getEntity(en.getIntKey());
			if (ent == null) continue;
			Resolver.Retrato r = en.getValue();
			for (AABB b : r.mundo()) Gizmos.cuboid(b, GizmoStyle.stroke(0x60C0C0C0));
			for (int o = 0; o < r.caixas().length; o++) {
				Colisao.Caixa c = r.caixas()[o];
				int cor = r.dentro()[o] > 1e-4 ? 0xFFFF3030 : 0xFF30FF60;
				for (int i = 0; i < 8; i++) for (int k = 0; k < 3; k++) {
					int j = i | (1 << k);
					if (j != i) Gizmos.line(c.canto(i), c.canto(j), cor, 2f).setAlwaysOnTop();
				}
			}
			for (Vec3[] a : r.apoios()) {
				Gizmos.line(a[0], a[1], 0xFFFFE040, 3f).setAlwaysOnTop();
				Gizmos.point(a[1], 0xFFFFE040, 6f).setAlwaysOnTop();
			}
			Gizmos.billboardText(String.format("dentro: %.1f px -> %.1f px", r.antes(), r.depois()),
					ent.position().add(0, ent.getBbHeight() + 0.9, 0), TextGizmo.Style.forColorAndCentered(0xFFFFFFFF)).setAlwaysOnTop();
		}
		Resolver.retratos.clear(); // o modelo enche de novo ao desenhar (quem saiu da tela some do debug)
	}
}

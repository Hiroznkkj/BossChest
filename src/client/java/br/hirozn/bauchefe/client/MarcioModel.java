package br.hirozn.bauchefe.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Modelo de jogador (braços largos) com a pose do Márcio por cima da humanoide.
 * Toda a pose final passa por um "arrasto" (como o drag do Fresh Animations): cada ângulo segue o
 * alvo aos poucos, então a troca de uma ação para outra nunca dá salto e a cabeça vira macia.
 */
public class MarcioModel extends HumanoidModel<MarcioRenderState> {
	/** Quanto o valor anda em direção ao alvo por tick (0..1): corpo e cabeça (a cabeça é mais macia). */
	private static final float SEGUE = 0.45f, SEGUE_CABECA = 0.25f;
	private static final int N = 28;
	private static final Int2ObjectMap<float[]> pose = new Int2ObjectOpenHashMap<>(); // id -> valores + tempo
	private final Esqueleto esqueleto;
	private static boolean avisou;

	public MarcioModel(ModelPart root) {
		super(root);
		Ik.medir(rightArm, rightLeg); // comprimento de braço e perna pelos cubos do modelo
		esqueleto = new Esqueleto(root, head, body, rightArm, leftArm, rightLeg, leftLeg);
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
		DadosMarcio d = st.bauchefe$dados();
		PoseMarcio.aplicar(d, st.ageInTicks, root, head, body, rightArm, leftArm, rightLeg, leftLeg);
		arrastar(d);
		// por último (depois da suavização, que é o que aparece): o corpo no mundo de verdade, com apoios e
		// sem nenhuma parte dentro de bloco
		try { Resolver.resolver(esqueleto, d); }
		catch (RuntimeException e) { if (!avisou) { avisou = true; br.hirozn.bauchefe.BauChefe.LOG.warn("[Baú Chefe] Resolvedor da pose falhou", e); } }
	}

	/** Mistura a pose deste quadro com a do quadro anterior, pelo tempo que passou. */
	private void arrastar(DadosMarcio d) {
		float[] alvo = ler();
		float[] p = pose.get(d.id);
		if (p == null || d.agora - p[N] > 40 || d.agora < p[N]) { // primeira vez ou voltou a aparecer: sem arrasto
			p = new float[N + 1];
			System.arraycopy(alvo, 0, p, 0, N);
			pose.put(d.id, p);
		}
		float dt = Mth.clamp(d.agora - p[N], 0f, 5f);
		float k = 1f - (float) Math.pow(1f - SEGUE, dt), kc = 1f - (float) Math.pow(1f - SEGUE_CABECA, dt);
		for (int i = 0; i < N; i++) p[i] = Mth.lerp(i < 4 ? kc : k, p[i], alvo[i]);
		p[N] = d.agora;
		escrever(p);
	}

	private float[] ler() {
		return new float[]{
				head.xRot, head.yRot, head.zRot, head.y,
				body.xRot, body.yRot, body.zRot,
				rightArm.xRot, rightArm.yRot, rightArm.zRot, rightArm.y,
				leftArm.xRot, leftArm.yRot, leftArm.zRot, leftArm.y,
				rightLeg.xRot, rightLeg.yRot, rightLeg.zRot,
				leftLeg.xRot, leftLeg.yRot, leftLeg.zRot,
				root.x, root.y, root.z, root.xRot, root.yRot, root.zRot, 0f};
	}

	private void escrever(float[] p) {
		head.xRot = p[0]; head.yRot = p[1]; head.zRot = p[2]; head.y = p[3];
		body.xRot = p[4]; body.yRot = p[5]; body.zRot = p[6];
		rightArm.xRot = p[7]; rightArm.yRot = p[8]; rightArm.zRot = p[9]; rightArm.y = p[10];
		leftArm.xRot = p[11]; leftArm.yRot = p[12]; leftArm.zRot = p[13]; leftArm.y = p[14];
		rightLeg.xRot = p[15]; rightLeg.yRot = p[16]; rightLeg.zRot = p[17];
		leftLeg.xRot = p[18]; leftLeg.yRot = p[19]; leftLeg.zRot = p[20];
		root.x = p[21]; root.y = p[22]; root.z = p[23]; root.xRot = p[24]; root.yRot = p[25]; root.zRot = p[26];
	}
}

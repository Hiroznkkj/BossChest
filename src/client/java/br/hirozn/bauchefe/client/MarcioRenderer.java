package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.BauChefe;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * O Márcio desenhado como jogador, com a skin dele e a camada de fora em 3D.
 * Por baixo continua sendo o villager (cérebro, cama, profissão); só o visual muda.
 */
public class MarcioRenderer extends HumanoidMobRenderer<Villager, MarcioRenderState, MarcioModel> {
	public static final Identifier SKIN = BauChefe.id("textures/entity/marcio.png");

	public MarcioRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new MarcioModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new Camada3D(this));
		addLayer(new OlhosMarcio(this));
	}

	@Override
	public Identifier getTextureLocation(MarcioRenderState st) { return SKIN; }

	@Override
	public MarcioRenderState createRenderState() { return new MarcioRenderState(); }

	@Override
	public void extractRenderState(Villager v, MarcioRenderState st, float partial) {
		super.extractRenderState(v, st, partial);
		DadosMarcio d = st.bauchefe$dados();
		MarcioCliente.extrair(v, st, partial, d);
		// nado deitado: usa a braçada de crawl do jogador (HumanoidModel) e a rotação abaixo
		st.swimAmount = d.nado;
		st.isVisuallySwimming = d.nado > 0.3f;
	}

	/** Quando ele muda de lugar de uma vez (embarcar, descer na doca), o corpo vem do lugar antigo num pulinho. */
	@Override
	public Vec3 getRenderOffset(MarcioRenderState st) {
		DadosMarcio d = st.bauchefe$dados();
		return super.getRenderOffset(st).add(d.saltoX, d.saltoY, d.saltoZ);
	}

	/** Nadando: deita de barriga para baixo girando em volta da linha d'água (~1,1 bloco acima dos pés). */
	@Override
	protected void setupRotations(MarcioRenderState st, PoseStack pose, float bodyRot, float scale) {
		super.setupRotations(st, pose, bodyRot, scale);
		float n = st.bauchefe$dados().nado;
		if (n <= 0) return;
		pose.translate(0f, 1.1f, 0f);
		pose.rotateDegrees(Axis.XP, Mth.lerp(n, 0f, -85f));
		pose.translate(0f, -1.1f, 0f);
	}
}

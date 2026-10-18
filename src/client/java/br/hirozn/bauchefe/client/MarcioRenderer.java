package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.BauChefe;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * O Márcio desenhado como jogador, com a skin dele e a camada de fora em 3D.
 * Por baixo continua sendo o villager (cérebro, cama, profissão); só o visual muda.
 */
public class MarcioRenderer extends HumanoidMobRenderer<Villager, MarcioRenderState, MarcioModel> {
	public static final Identifier SKIN = BauChefe.id("textures/entity/marcio.png");

	public MarcioRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new MarcioModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new Camada3D(this));
	}

	@Override
	public Identifier getTextureLocation(MarcioRenderState st) { return SKIN; }

	@Override
	public MarcioRenderState createRenderState() { return new MarcioRenderState(); }

	@Override
	public void extractRenderState(Villager v, MarcioRenderState st, float partial) {
		super.extractRenderState(v, st, partial);
		MarcioCliente.extrair(v, st, partial, st.bauchefe$dados());
	}
}

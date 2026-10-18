package br.hirozn.bauchefe.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state do Márcio desenhado como jogador. */
public class MarcioRenderState extends HumanoidRenderState implements EstadoPescador {
	private final DadosMarcio dados = new DadosMarcio();

	@Override
	public DadosMarcio bauchefe$dados() { return dados; }
}

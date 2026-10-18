package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.DadosMarcio;
import br.hirozn.bauchefe.client.EstadoPescador;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Guarda a fase da pesca, a animação especial, olhos e fala no render state do villager. */
@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateMixin implements EstadoPescador {
	@Unique private final DadosMarcio bauchefe$dados = new DadosMarcio();

	@Override public DadosMarcio bauchefe$dados() { return bauchefe$dados; }
}

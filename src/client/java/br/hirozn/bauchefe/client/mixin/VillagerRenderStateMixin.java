package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.EstadoPescador;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Guarda a fase da pesca e o tempo dela (com partialTick) no render state do villager. */
@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateMixin implements EstadoPescador {
	@Unique private boolean bauchefe$pescador;
	@Unique private int bauchefe$fase;
	@Unique private float bauchefe$tempo;

	@Override public boolean bauchefe$pescador() { return bauchefe$pescador; }
	@Override public int bauchefe$fase() { return bauchefe$fase; }
	@Override public float bauchefe$tempo() { return bauchefe$tempo; }

	@Override
	public void bauchefe$set(boolean pescador, int fase, float tempo) {
		bauchefe$pescador = pescador;
		bauchefe$fase = fase;
		bauchefe$tempo = tempo;
	}
}

package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.EstadoPinto;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChickenRenderState.class)
public abstract class ChickenRenderStateMixin implements EstadoPinto {
	@Unique private boolean bauchefe$sentado;
	@Unique private float bauchefe$carinho = -1f, bauchefe$fase;

	@Override public boolean bauchefe$sentado() { return bauchefe$sentado; }
	@Override public float bauchefe$carinho() { return bauchefe$carinho; }
	@Override public float bauchefe$fase() { return bauchefe$fase; }

	@Override
	public void bauchefe$set(boolean sentado, float carinho, float fase) {
		bauchefe$sentado = sentado;
		bauchefe$carinho = carinho;
		bauchefe$fase = fase;
	}
}

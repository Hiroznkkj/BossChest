package br.hirozn.bauchefe.mixin;

import br.hirozn.bauchefe.Asas;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Galinha num barco não fica batendo as asas (a vanilla bate sempre que não está no chão).
 * Só bate quando o Márcio faz carinho.
 */
@Mixin(Chicken.class)
public abstract class ChickenMixin {
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void bauchefe$asasNoBarco(CallbackInfo ci) {
		Chicken c = (Chicken) (Object) this;
		if (!c.isPassenger()) return;
		if (Asas.batendo(c.getId(), c.level().getGameTime())) {
			c.flapSpeed = Math.min(1f, c.oFlapSpeed + 0.3f);
			c.flapping = 1f;
		} else {
			c.flapSpeed = Math.max(0f, c.oFlapSpeed - 0.3f); // fecha as asas suavemente, como no chão
			c.flapping *= 0.5f;
		}
	}
}

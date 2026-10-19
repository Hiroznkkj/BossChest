package br.hirozn.bauchefe.client.mixin;

import br.hirozn.bauchefe.client.Depurar;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Desenhos do /marcioanim colisao junto com os outros desenhos de debug do quadro. */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
	@Inject(method = "extractGizmos", at = @At("HEAD"))
	private void bauchefe$colisao(CallbackInfo ci) { Depurar.desenhar(); }
}

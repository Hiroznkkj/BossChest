package br.hirozn.bauchefe.mixin;

import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Na bigorna, juntar dois níveis iguais sobe além do máximo normal (até 255). Só vale para a bigorna. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Redirect(method = "createResult", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
	private int bauchefe$semLimite(Enchantment e) {
		int normal = e.getMaxLevel();
		if (normal <= 1) return normal; // Remendo, Toque Suave... continuam nível 1
		if (e.description().getContents() instanceof TranslatableContents t) {
			String k = t.getKey();
			if (k.endsWith(".quick_charge") || k.endsWith(".lure")) return Math.max(normal, 5); // acima de V quebra besta/vara
		}
		return 255;
	}
}

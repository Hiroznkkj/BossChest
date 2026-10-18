package br.hirozn.bauchefe.mixin.timber;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** TimberReplant (CC0): todos os drops da árvore caem no bloco que o jogador quebrou. */
@Pseudo
@Mixin(targets = "com.osama.timberreplant.TreeFeller", remap = false)
public class TreeFellerMixin {
	private static final Map<UUID, BlockPos> bauchefe$origem = new HashMap<>();

	@Inject(method = "fellTree", at = @At("HEAD"))
	private static void bauchefe$guardarOrigem(Level level, BlockPos pos, Player player, ItemStack tool, CallbackInfoReturnable<Boolean> cir) {
		if (player != null) bauchefe$origem.put(player.getUUID(), pos.immutable());
	}

	@Redirect(method = "breakBlockWithDrops", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"))
	private static void bauchefe$soltarNaOrigem(BlockState state, Level level, BlockPos pos, BlockEntity be, Entity quem, ItemStack tool) {
		BlockPos alvo = quem == null ? pos : bauchefe$origem.getOrDefault(quem.getUUID(), pos);
		Block.dropResources(state, level, alvo, be, quem, tool);
	}
}

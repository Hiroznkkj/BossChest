package br.hirozn.bauchefe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BauChefeBlockEntity extends BaseContainerBlockEntity {
	private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
	private int timer;

	public BauChefeBlockEntity(BlockPos pos, BlockState s) {
		super(BauChefe.BAU_CHEFE_BE, pos, s);
	}

	public static void tickServer(Level level, BlockPos pos, BlockState s, BauChefeBlockEntity be) {
		if (++be.timer % 20 != 0) return; // 1x por segundo
		ServerLevel sl = (ServerLevel) level;
		boolean perto = sl.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Rede.RAIO, false) != null;
		if (s.getValue(BauChefeBlock.ATIVO) != perto)
			level.setBlock(pos, s.setValue(BauChefeBlock.ATIVO, perto), Block.UPDATE_ALL);
		if (be.isEmpty()) return;
		java.util.Set<String> semLugar = Rede.montar(sl, be).distribuir();
		if (!semLugar.isEmpty() && be.timer % 200 == 0) { // avisa no máximo a cada 10 s
			net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
					"§c[Baú Chefe] " + Rede.erro(semLugar) + " §7— ponha uma moldura ou um baú com esse item");
			for (net.minecraft.server.level.ServerPlayer p : sl.players())
				if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= Rede.RAIO * Rede.RAIO) p.sendOverlayMessage(msg);
		}
	}

	@Override
	protected Component getDefaultName() { return Component.translatable("container.bauchefe.bau_chefe"); }

	@Override
	protected NonNullList<ItemStack> getItems() { return items; }

	@Override
	protected void setItems(NonNullList<ItemStack> list) { items = list; }

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inv) { return ChestMenu.threeRows(id, inv, this); }

	@Override
	public int getContainerSize() { return 27; }

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(in, items);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		ContainerHelper.saveAllItems(out, items);
	}
}

package br.hirozn.bauchefe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class BauChefeBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Fica true quando tem jogador no raio: troca a textura e acende. */
	public static final BooleanProperty ATIVO = BooleanProperty.create("ativo");

	public BauChefeBlock(Properties p) {
		super(p);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ATIVO, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ATIVO); }

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new BauChefeBlockEntity(pos, s); }

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, BauChefe.BAU_CHEFE_BE, BauChefeBlockEntity::tickServer);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BauChefeBlockEntity be) player.openMenu(be);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
		if (!s.getValue(ATIVO)) return;
		for (int i = 0; i < 2; i++)
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + r.nextDouble(), pos.getY() + 1.05, pos.getZ() + r.nextDouble(), 0, 0.02, 0);
		if (r.nextInt(3) == 0)
			level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, r.nextGaussian() * 0.5, -0.6, r.nextGaussian() * 0.5);
	}
}

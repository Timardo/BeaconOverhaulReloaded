package gay.solonovamax.beaconsoverhaul.mixin;

import gay.solonovamax.beaconsoverhaul.block.conduit.OverhauledConduitBlockEntity;
import gay.solonovamax.beaconsoverhaul.util.JvmUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ConduitBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;


@Mixin(ConduitBlock.class)
public abstract class ConduitBlockMixin extends BlockWithEntity {
    @Unique
    private static final VoxelShape ACTIVATED_SHAPE = Block.createCuboidShape(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
    @Unique
    private static final VoxelShape DEACTIVATED_SHAPE = Block.createCuboidShape(6.0, 6.0, 6.0, 10.0, 10.0, 10.0);

    protected ConduitBlockMixin(Settings settings) {
        super(settings);
    }

    /**
     * @author solonovamax
     * @reason Overwritten because the shapes in both states are different from vanilla
     */
    @Override
    @Overwrite
    @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (world.getBlockEntity(pos) instanceof OverhauledConduitBlockEntity conduitBlockEntity && conduitBlockEntity.isActive())
            return ConduitBlockMixin.ACTIVATED_SHAPE;
        else
            return ConduitBlockMixin.DEACTIVATED_SHAPE;
    }

    /**
     * @author solonovamax
     * @reason Overwritten because we replace ConduitBlockEntity entirely
     */
    @Override
    @Overwrite
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new OverhauledConduitBlockEntity(pos, state);
    }

    /**
     * @author solonovamax
     * @reason Overwritten because we replace ConduitBlockEntity entirely
     */
    @Nullable
    @Override
    @Overwrite
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        BlockEntityTicker<OverhauledConduitBlockEntity> ticker = world.isClient ? OverhauledConduitBlockEntity::clientTick : OverhauledConduitBlockEntity::serverTick;
        return BlockWithEntity.validateTicker(type, BlockEntityType.CONDUIT, JvmUtils.castUnchecked(ticker));
    }
}

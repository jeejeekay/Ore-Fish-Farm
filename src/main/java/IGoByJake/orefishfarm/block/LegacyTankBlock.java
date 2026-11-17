package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The 1.x Breeding Tank and Power Tank. They no longer exist as their own kinds: one tick after loading (or being
 * placed from an old item) each turns into a Fish Tank with the same contents, a Love Lattice for a breeding tank
 * and the power role for a power tank.
 */
public class LegacyTankBlock extends BaseEntityBlock {
    private final boolean breeding;

    public LegacyTankBlock(Properties props, boolean breeding) {
        super(props);
        this.breeding = breeding;
    }

    public boolean breeding() { return this.breeding; }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LegacyTankBlockEntity(this.breeding ? Registry.LEGACY_BREEDING_BE.get() : Registry.LEGACY_POWER_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide) return null;
        return (l, p, s, be) -> { if (be instanceof LegacyTankBlockEntity legacy) legacy.convert(); };
    }
}

package IGoByJake.orefishfarm.pipe;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.HashMap;
import java.util.Map;

/**
 * A Pipe Tank: a glass tube of water that hauling fish swim through. It joins every Pipe Tank and every block with
 * an item inventory it touches, with no settings of its own; all the control is in the network's screen (right-click
 * any pipe). There is no block entity and nothing ticks: the six sides are block state (for the look), and the
 * networks live in {@link PipeNetworks}, rebuilt only when a pipe or a neighbour changes.
 */
public class PipeTankBlock extends Block {
    public static final EnumProperty<PipeSide> NORTH = EnumProperty.create("north", PipeSide.class);
    public static final EnumProperty<PipeSide> EAST = EnumProperty.create("east", PipeSide.class);
    public static final EnumProperty<PipeSide> SOUTH = EnumProperty.create("south", PipeSide.class);
    public static final EnumProperty<PipeSide> WEST = EnumProperty.create("west", PipeSide.class);
    public static final EnumProperty<PipeSide> UP = EnumProperty.create("up", PipeSide.class);
    public static final EnumProperty<PipeSide> DOWN = EnumProperty.create("down", PipeSide.class);

    private static final Map<BlockState, VoxelShape> SHAPES = new HashMap<>();
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11); // the 6 px tube

    public PipeTankBlock(Properties props) {
        super(props);
        BlockState s = this.stateDefinition.any();
        for (Direction d : Direction.values()) s = s.setValue(side(d), PipeSide.NONE);
        registerDefaultState(s.setValue(EAST, PipeSide.CAP).setValue(WEST, PipeSide.CAP));
    }

    public static EnumProperty<PipeSide> side(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    // ------------------------------------------------------------------ connections

    /** Whether a pipe at {@code pos} joins whatever is on side {@code d}. */
    static boolean joins(BlockGetter level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        if (level.getBlockState(n).getBlock() instanceof PipeTankBlock) return true;
        return hasInventory(level, n, d.getOpposite());
    }

    /** A block entity with an item inventory on that face. */
    static boolean hasInventory(BlockGetter level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).isPresent();
    }

    /**
     * The look for these joins: joined sides are tubes (a rim where it's an inventory); a pipe with one join runs
     * straight through and is capped (rim) on the far side; a lone pipe is a short east-west tube capped at both ends.
     */
    static BlockState shaped(BlockState base, BlockGetter level, BlockPos pos) {
        PipeSide[] sides = new PipeSide[6];
        int joined = 0;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            PipeSide s = PipeSide.NONE;
            if (level.getBlockState(n).getBlock() instanceof PipeTankBlock) s = PipeSide.PIPE;
            else if (hasInventory(level, n, d.getOpposite())) s = PipeSide.RIM;
            sides[d.ordinal()] = s;
            if (s != PipeSide.NONE) joined++;
        }
        if (joined == 0) {
            sides[Direction.EAST.ordinal()] = PipeSide.CAP;
            sides[Direction.WEST.ordinal()] = PipeSide.CAP;
        } else if (joined == 1) {
            for (Direction d : Direction.values()) {
                if (sides[d.ordinal()] != PipeSide.NONE) {
                    sides[d.getOpposite().ordinal()] = PipeSide.CAP;
                    break;
                }
            }
        }
        BlockState s = base;
        for (Direction d : Direction.values()) s = s.setValue(side(d), sides[d.ordinal()]);
        return s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return shaped(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos from) {
        // Some blocks (Create's depots, belts, and others) only offer their inventory once their block entity has
        // set itself up, which is after this call. Look again a moment later.
        recheckSoon(level, pos);
        return shaped(state, level, pos);
    }

    /** Re-check this pipe's joins in a couple of ticks. */
    private void recheckSoon(LevelAccessor level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, RECHECK_TICKS);
    }

    private static final int RECHECK_TICKS = 2;

    /** The delayed re-check: fix the look, and have the network find its inventories again. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        BlockState now = shaped(state, level, pos);
        if (now != state) level.setBlock(pos, now, Block.UPDATE_ALL); // onPlace marks the network
        else {
            // Same look (a capped end and an inventory mouth look alike), but an inventory may have come or gone.
            PipeNetworks nets = PipeNetworks.get(level);
            PipeNet net = nets.netAt(pos);
            if (net == null || !net.knowsInventoriesAt(level, pos)) nets.markDirty(pos);
        }
    }

    /** A neighbour's block entity changed (Forge): its inventory may have just appeared. */
    @Override
    public void onNeighborChange(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (level instanceof LevelAccessor la) recheckSoon(la, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        // Placed, or its joins changed (a chest placed or broken next to it): the network is worked out again
        // at the end of the tick, once for everything that changed.
        if (level instanceof ServerLevel sl && old != state) PipeNetworks.get(sl).markDirty(pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel sl && !newState.is(this)) PipeNetworks.get(sl).markDirty(pos);
        super.onRemove(state, level, pos, newState, moving);
    }

    /** A neighbour's inventory can appear without its block state changing (rare): re-check our look then. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level.isClientSide) return;
        BlockState now = shaped(state, level, pos);
        if (now != state) level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        recheckSoon(level, pos);
    }

    // ------------------------------------------------------------------ shape and look

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.computeIfAbsent(state, PipeTankBlock::makeShape);
    }

    private static VoxelShape makeShape(BlockState state) {
        VoxelShape s = CORE;
        for (Direction d : Direction.values()) {
            PipeSide side = state.getValue(side(d));
            if (side == PipeSide.NONE) continue;
            s = Shapes.or(s, box(d, 5, 11, 11, 16));
            if (side == PipeSide.RIM || side == PipeSide.CAP) s = Shapes.or(s, box(d, 4, 12, 14, 16)); // the 8 px rim (the part in this block)
        }
        return s.optimize();
    }

    /** A box on side d: lo..hi across the tube, from..to along it (toward d). */
    private static VoxelShape box(Direction d, double lo, double hi, double from, double to) {
        double a = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? from : 16 - to;
        double b = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? to : 16 - from;
        return switch (d.getAxis()) {
            case X -> Block.box(a, lo, lo, b, hi, hi);
            case Y -> Block.box(lo, a, lo, hi, b, hi);
            case Z -> Block.box(lo, lo, a, hi, hi, b);
        };
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    // ------------------------------------------------------------------ right-click

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        PipeNetworks nets = PipeNetworks.get(sl);
        nets.flush();
        PipeNet net = nets.netAt(pos);
        if (net == null) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        // A bucket of hauling fish goes straight in.
        if (!player.isShiftKeyDown() && FishData.isFish(held)) {
            if (!FishData.isHaulingFish(held)) {
                player.displayClientMessage(Component.translatable("message.orefishfarm.pipe_not_hauler"), true);
                return InteractionResult.CONSUME;
            }
            if (!net.addFish(sl, held.copyWithCount(1))) {
                player.displayClientMessage(Component.translatable("message.orefishfarm.pipe_full"), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                ItemStack empty = new ItemStack(FishData.habitat(FishData.habitatOf(held)).empty());
                held.shrink(1);
                if (held.isEmpty()) player.setItemInHand(hand, empty);
                else if (!player.getInventory().add(empty)) player.drop(empty, false);
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 1F, 1F);
            return InteractionResult.CONSUME;
        }
        PipeMenu.open(sp, net, pos);
        return InteractionResult.CONSUME;
    }
}

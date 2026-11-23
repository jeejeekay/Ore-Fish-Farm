package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one Fish Tank block. Kits change it in place: a habitat kit (water bucket, Heating Coil, Void Lens, or any
 * item a habitat file names) changes what it is filled with; a Dynamo Coil makes it a power tank (sneak-use with
 * an empty hand takes the coil back out). Tanks of the same habitat and role that touch join into one aquarium,
 * up to 8x8x8; the six connection properties hide the glass and frame between joined blocks. Inside corners and
 * steps (where an aquarium turns inward, sideways or upward) get extra pieces from the block entity's model data
 * ({@link TankBlockEntity#extras}), so they don't multiply the block states.
 */
public class TankBlock extends BaseEntityBlock {
    public static final EnumProperty<TankRole> ROLE = EnumProperty.create("role", TankRole.class);
    public static final EnumProperty<TankLook> LOOK = EnumProperty.create("look", TankLook.class);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public TankBlock(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(ROLE, TankRole.FISH).setValue(LOOK, TankLook.WATER)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    public static BooleanProperty side(Direction d) {
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
        b.add(ROLE, LOOK, NORTH, EAST, SOUTH, WEST, UP, DOWN);
        // With Create installed: its Blaze Burner heat property, so Basins and boilers read the aquarium's heat.
        net.minecraft.world.level.block.state.properties.Property<?> heat = IGoByJake.orefishfarm.compat.CreateHeat.property();
        if (heat != null) b.add(heat);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        return level.isClientSide ? null : createTickerHelper(t, Registry.FISH_TANK_BE.get(), TankBlockEntity::serverTick);
    }

    // ---- joining: re-worked a tick after a tank appears or disappears, so the block entities all exist

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Aquarium.rebuild(level, List.of(pos));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof TankBlockEntity be) {
            // Its own fish drop. If it was running the aquarium, the shared food, upgrades and output pass to a
            // neighbour that was part of it (the rebuild then hands them to the new controller); else they drop.
            // A newborn hasn't been scooped into a bucket yet: it comes out as a plain (dead) fish.
            for (int i = 0; i < TankBlockEntity.FISH; i++) {
                ItemStack f = be.fish.getStackInSlot(i);
                drop(level, pos, IGoByJake.orefishfarm.data.FishGenes.isNewborn(f) ? FishData.display(f) : f);
            }
            TankBlockEntity heir = null;
            for (Direction d : Direction.values()) {
                if (be.isMemberOfSameGroup(pos.relative(d)) && level.getBlockEntity(pos.relative(d)) instanceof TankBlockEntity n) {
                    heir = n;
                    break;
                }
            }
            for (int i = 0; i < TankBlockEntity.SHARED; i++) {
                ItemStack s = be.shared.getStackInSlot(i).copy();
                if (s.isEmpty()) continue;
                if (heir != null) s = heir.placeShared(i, s);
                drop(level, pos, s);
            }
            if (heir != null && be.isController()) heir.takeEnergy(be.energy);
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (level.getBlockState(n).is(this)) level.scheduleTick(n, this, 1);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    private static void drop(Level level, BlockPos pos, ItemStack s) {
        if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TankBlockEntity be) {
            TankBlockEntity c = be.controllerBE();
            if (c != null) c.sinksDirty = true;
        }
    }

    // ---- right-click

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TankBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        TankBlockEntity c = be.controllerOrSelf();
        ItemStack held = player.getItemInHand(hand);
        boolean sneak = player.isShiftKeyDown();

        // A bucket of fish: into the first free slot of the aquarium.
        if (!sneak && FishData.isFish(held)) {
            if (!c.insertFishAnywhere(held.copyWithCount(1))) {
                player.displayClientMessage(Component.translatable("message.orefishfarm.tank_full"), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 1F, 1F);
            return InteractionResult.CONSUME;
        }
        FishData.Habitat h = FishData.habitatForKit(held);
        // Habitat kits: refill the whole aquarium.
        if (h != null) {
            if (h.id().equals(c.habitat())) {
                player.displayClientMessage(Component.translatable("message.orefishfarm.already_habitat", FishData.habitatName(h.id())), true);
                return InteractionResult.CONSUME;
            }
            FishData.Habitat old = FishData.habitat(c.habitat());
            Aquarium.convert(level, c, h.id(), null);
            // The habitat's bucket is poured in (a lava bucket comes back empty); an empty bucket (void) is kept.
            if (!held.is(net.minecraft.world.item.Items.BUCKET)) useUp(player, hand, held);
            if (old.kit() != null && old.kit() != net.minecraft.world.item.Items.BUCKET
                    && !new ItemStack(old.kit()).hasCraftingRemainingItem()) give(player, new ItemStack(old.kit()));
            level.playSound(null, pos, h.look().equals("lava") ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1F, 1F);
            player.displayClientMessage(Component.translatable("message.orefishfarm.now_habitat", FishData.habitatName(h.id())), true);
            return InteractionResult.CONSUME;
        }
        // Dynamo Coil: power role. Sneak with an empty hand to take it back out.
        if (held.is(Registry.DYNAMO_COIL.get()) && !sneak && state.getValue(ROLE) == TankRole.FISH) {
            Aquarium.convert(level, c, null, TankRole.POWER);
            useUp(player, hand, held);
            level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1F, 1F);
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() && sneak && state.getValue(ROLE) == TankRole.POWER) {
            Aquarium.convert(level, c, null, TankRole.FISH);
            give(player, new ItemStack(Registry.DYNAMO_COIL.get()));
            level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1F, 1F);
            return InteractionResult.CONSUME;
        }
        if (!sneak && !held.isEmpty()) {
            // A plain (dead) fish can't go in: say so.
            if (FishData.loaded() && FishData.fish(held) != null) {
                player.displayClientMessage(Component.translatable("message.orefishfarm.needs_live_fish"), true);
                return InteractionResult.CONSUME;
            }
            // Food: straight into the shared slots.
            if (FishData.loaded() && FishData.foodTicks(held) > 0 && FishData.fish(held) == null) {
                ItemStack rest = c.insertShared(held.copy());
                if (rest.getCount() != held.getCount()) {
                    if (!player.getAbilities().instabuild) held.setCount(rest.getCount());
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.2F);
                    return InteractionResult.CONSUME;
                }
            }
        }
        if (player instanceof ServerPlayer sp) NetworkHooks.openScreen(sp, c, c::writeMenu);
        return InteractionResult.CONSUME;
    }

    private static void useUp(Player player, InteractionHand hand, ItemStack held) {
        if (player.getAbilities().instabuild) return;
        ItemStack rem = held.getCraftingRemainingItem();
        held.shrink(1);
        if (rem.isEmpty()) return;
        if (held.isEmpty()) player.setItemInHand(hand, rem);
        else give(player, rem);
    }

    private static void give(Player player, ItemStack s) {
        if (!player.getInventory().add(s)) player.drop(s, false);
    }

    /** Every tank position joined with this one (just itself when alone). */
    public static List<BlockPos> group(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TankBlockEntity be) return be.controllerOrSelf().members();
        return new ArrayList<>(List.of(pos));
    }
}

package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared by both tanks: an inventory of fish slots, one food slot and one output slot, and a furnace-style food
 * timer. Food only burns while the tank has something to do ({@link #canWork}); when a piece of food runs out the
 * tank rolls once ({@link #onFoodFinished}). Nothing runs while the tank is idle, so idle tanks cost nothing.
 */
public abstract class BaseTankBlockEntity extends BlockEntity implements MenuProvider {

    protected final int fishSlots;
    protected final int foodSlot;
    protected final int outputSlot;
    protected final ItemStackHandler items;
    private final LazyOptional<IItemHandler> automation;
    protected int burn;
    protected int burnTotal;

    protected BaseTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fishSlots) {
        super(type, pos, state);
        this.fishSlots = fishSlots;
        this.foodSlot = fishSlots;
        this.outputSlot = fishSlots + 1;
        this.items = new ItemStackHandler(fishSlots + 2) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                // Fish are drawn in the tank, so clients need to hear about them.
                if (slot < BaseTankBlockEntity.this.fishSlots && level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                if (slot < BaseTankBlockEntity.this.fishSlots) return acceptsFish(stack);
                if (slot == foodSlot) return FishData.isFood(stack);
                return false; // the output only fills itself
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot < BaseTankBlockEntity.this.fishSlots ? 1 : 64;
            }
        };
        this.automation = LazyOptional.of(() -> new Automation(this.items, this.outputSlot));
    }

    public ItemStackHandler items() { return this.items; }

    /** Which fish this tank takes (the Power Tank only takes power fish). */
    protected boolean acceptsFish(ItemStack stack) {
        return FishData.isFish(stack);
    }

    public ItemStack fish(int i) { return this.items.getStackInSlot(i); }

    /** Whether a right-click with this item would put it in the tank. */
    public boolean tryInsertFishCheck(ItemStack held) {
        if (held.isEmpty() || !acceptsFish(held)) return false;
        for (int i = 0; i < this.fishSlots; i++) if (this.items.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    /** Right-click with a fish: drops one into the first empty fish slot. Returns false if it doesn't fit. */
    public boolean tryInsertFish(ItemStack held) {
        if (held.isEmpty() || !acceptsFish(held)) return false;
        for (int i = 0; i < this.fishSlots; i++) {
            if (this.items.getStackInSlot(i).isEmpty()) {
                this.items.setStackInSlot(i, held.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    /** Whether the tank has something to do right now (decides if food burns). */
    protected abstract boolean canWork();

    /** One roll per piece of food eaten. */
    protected abstract void onFoodFinished();

    /** Called every tick food is burning (power fish use it). */
    protected void burningTick() {}

    /** Called every server tick (energy pushing). */
    protected void alwaysTick() {}

    public static void serverTick(Level level, BlockPos pos, BlockState state, BaseTankBlockEntity be) {
        boolean changed = false;
        boolean working = be.canWork();
        if (be.burn > 0 && working) {
            be.burn--;
            be.burningTick();
            if (be.burn == 0) be.onFoodFinished();
            changed = true;
        }
        if (be.burn <= 0 && working && be.canWork()) {
            ItemStack food = be.items.getStackInSlot(be.foodSlot);
            int ticks = FishData.foodTicks(food);
            if (ticks > 0) {
                be.burnTotal = Math.max(1, (int) Math.round(ticks * Config.FOOD_TIME.get()));
                be.burn = be.burnTotal;
                be.items.extractItem(be.foodSlot, 1, false);
                changed = true;
            }
        }
        be.alwaysTick();
        if (changed) be.setChanged();
    }

    /** Puts a result in the single output slot; false (and nothing changes) if it doesn't fit. */
    protected boolean addOutput(ItemStack stack) {
        if (!outputFits(stack)) return false;
        ItemStack cur = this.items.getStackInSlot(this.outputSlot);
        if (cur.isEmpty()) this.items.setStackInSlot(this.outputSlot, stack.copy());
        else {
            cur.grow(stack.getCount());
            this.items.setStackInSlot(this.outputSlot, cur);
        }
        return true;
    }

    protected boolean outputFits(ItemStack stack) {
        ItemStack cur = this.items.getStackInSlot(this.outputSlot);
        return cur.isEmpty() || (ItemStack.isSameItemSameTags(cur, stack) && cur.getCount() + stack.getCount() <= cur.getMaxStackSize());
    }

    public int burn() { return this.burn; }

    public int burnTotal() { return this.burnTotal; }

    public void dropContents() {
        if (this.level == null) return;
        for (int i = 0; i < this.items.getSlots(); i++) {
            Containers.dropItemStack(this.level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, this.items.getStackInSlot(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", this.items.serializeNBT());
        tag.putInt("Burn", this.burn);
        tag.putInt("BurnTotal", this.burnTotal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) this.items.deserializeNBT(tag.getCompound("Items"));
        this.burn = tag.getInt("Burn");
        this.burnTotal = tag.getInt("BurnTotal");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return this.automation.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.automation.invalidate();
    }

    /** What pipes, hoppers and funnels see: fish and food go in, only the output comes out. */
    private record Automation(ItemStackHandler inner, int output) implements IItemHandler {
        @Override public int getSlots() { return inner.getSlots(); }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return inner.getStackInSlot(slot); }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot == output ? stack : inner.insertItem(slot, stack, simulate);
        }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == output ? inner.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return inner.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return slot != output && inner.isItemValid(slot, stack); }
    }
}

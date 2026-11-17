package IGoByJake.orefishfarm.menu;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.block.TankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The aquarium screen. Fish slots: two per block, shown nine to a row, two rows at a time with a scroll bar
 * (the scroll position is a menu button, so the server's slots follow). Then the shared food and Love Lattice
 * slots, the output, and the player's inventory.
 */
public class AquariumMenu extends AbstractContainerMenu {
    public static final int COLS = 9, ROWS = 2, VISIBLE = COLS * ROWS;
    public static final int FISH_X = 8, FISH_Y = 54;
    public static final int FOOD_Y = 98, OUTPUT_Y = 120, INV_Y = 144;
    public static final int UPGRADE_X = 116;

    public final TankBlockEntity controller;
    public final ContainerData data;
    /** Every fish slot in the aquarium: block, slot. */
    private final List<FishRef> refs = new ArrayList<>();
    private int row;
    public final int fishStart = 0, sharedStart = VISIBLE, playerStart;

    public record FishRef(TankBlockEntity be, int slot) {}

    public AquariumMenu(int id, Inventory inv, TankBlockEntity controller, List<TankBlockEntity> members, ContainerData data) {
        super(Registry.AQUARIUM_MENU.get(), id);
        this.controller = controller;
        this.data = data;
        for (int s = 0; s < TankBlockEntity.FISH; s++) for (TankBlockEntity t : members) this.refs.add(new FishRef(t, s));
        for (int i = 0; i < VISIBLE; i++) addSlot(new FishSlot(this, i, FISH_X + (i % COLS) * 18, FISH_Y + (i / COLS) * 18));
        ItemStackHandler shared = controller.sharedHandler();
        for (int i = 0; i < TankBlockEntity.FOOD; i++) addSlot(new SlotItemHandler(shared, TankBlockEntity.FOOD_START + i, 8 + i * 18, FOOD_Y));
        for (int i = 0; i < TankBlockEntity.UPGRADES; i++) addSlot(new SlotItemHandler(shared, TankBlockEntity.UPGRADE_START + i, UPGRADE_X + 6 + i * 18, FOOD_Y));
        for (int i = 0; i < TankBlockEntity.OUTPUT; i++) addSlot(new SlotItemHandler(shared, TankBlockEntity.OUTPUT_START + i, 8 + i * 18, OUTPUT_Y));
        this.playerStart = this.slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + 12 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 70));
        addDataSlots(data);
    }

    public static AquariumMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        Level level = inv.player.level();
        BlockPos cpos = buf.readBlockPos();
        int n = buf.readVarInt();
        List<BlockPos> pos = new ArrayList<>();
        for (int i = 0; i < n; i++) pos.add(buf.readBlockPos());
        if (!(level.getBlockEntity(cpos) instanceof TankBlockEntity c)) throw new IllegalStateException("Fish Tank missing on the client");
        List<TankBlockEntity> members = new ArrayList<>();
        for (BlockPos p : pos) if (level.getBlockEntity(p) instanceof TankBlockEntity t) members.add(t);
        if (members.size() != pos.size()) members = new ArrayList<>(List.of(c)); // keep slot counts honest if some aren't here
        return new AquariumMenu(id, inv, c, members, new SimpleContainerData(TankBlockEntity.D_COUNT));
    }

    public int get(int i) { return this.data.get(i); }

    public int fishSlotCount() { return this.refs.size(); }

    public int rows() { return Mth.positiveCeilDiv(this.refs.size(), COLS); }

    public int maxRow() { return Math.max(0, rows() - ROWS); }

    public int row() { return this.row; }

    public void setRow(int r) { this.row = Mth.clamp(r, 0, maxRow()); }

    @Nullable
    public FishRef ref(int visible) {
        int i = this.row * COLS + visible;
        return i < this.refs.size() ? this.refs.get(i) : null;
    }

    @Nullable
    public FishRef refByIndex(int i) {
        return i >= 0 && i < this.refs.size() ? this.refs.get(i) : null;
    }

    /** Scroll: the button id is the new top row. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        setRow(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < this.playerStart) {
            if (!moveItemStackTo(stack, this.playerStart, this.slots.size(), true)) return ItemStack.EMPTY;
            if (index < VISIBLE) {
                // Fish slots aren't backed by a Container: write the change through.
                slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                return copy;
            }
        } else {
            boolean moved = false;
            // A fish goes into the first open fish slot anywhere in the aquarium, not just the rows on show.
            for (FishRef r : this.refs) {
                if (stack.isEmpty()) break;
                ItemStackHandler h = r.be().fishHandler();
                if (h.getStackInSlot(r.slot()).isEmpty() && h.isItemValid(r.slot(), stack)) {
                    h.setStackInSlot(r.slot(), stack.split(1));
                    moved = true;
                }
            }
            if (!stack.isEmpty()) moved |= moveItemStackTo(stack, this.sharedStart, this.sharedStart + TankBlockEntity.FOOD + TankBlockEntity.UPGRADES, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.controller.isRemoved() && this.controller.bounds().inflate(5).contains(player.position());
    }

    /** A fish slot whose target follows the scroll position. */
    public static final class FishSlot extends Slot {
        private static final Container EMPTY = new SimpleContainer(0);
        private final AquariumMenu menu;
        public final int visible;

        FishSlot(AquariumMenu menu, int visible, int x, int y) {
            super(EMPTY, visible, x, y);
            this.menu = menu;
            this.visible = visible;
        }

        @Nullable
        public FishRef target() { return this.menu.ref(this.visible); }

        @Override
        public ItemStack getItem() {
            FishRef r = target();
            return r == null ? ItemStack.EMPTY : r.be().fishHandler().getStackInSlot(r.slot());
        }

        @Override
        public void set(ItemStack stack) {
            FishRef r = target();
            if (r != null) r.be().fishHandler().setStackInSlot(r.slot(), stack);
            setChanged();
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean mayPlace(ItemStack stack) {
            FishRef r = target();
            return r != null && r.be().fishHandler().isItemValid(r.slot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return target() != null;
        }

        @Override
        public int getMaxStackSize() { return 1; }

        @Override
        public int getMaxStackSize(ItemStack stack) { return 1; }

        @Override
        public ItemStack remove(int amount) {
            FishRef r = target();
            return r == null ? ItemStack.EMPTY : r.be().fishHandler().extractItem(r.slot(), amount, false);
        }

        @Override
        public boolean isActive() { return target() != null; }

        /** Open only while the aquarium has a Love Lattice (the second fish of each block). */
        public boolean locked() {
            FishRef r = target();
            return r != null && r.slot() > 0 && this.menu.controller.latticeCount() == 0;
        }
    }
}

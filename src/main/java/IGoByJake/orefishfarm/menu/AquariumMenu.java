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
import net.minecraft.world.inventory.ClickType;
import IGoByJake.orefishfarm.data.FishData;
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
        addSlot(new SlotItemHandler(shared, TankBlockEntity.BUCKET_SLOT, 8 + 8 * 18, OUTPUT_Y));
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

    /** Index of the bucket slot among the menu's slots. */
    public int bucketSlot() { return this.sharedStart + TankBlockEntity.FOOD + TankBlockEntity.UPGRADES; }

    /**
     * Fish slots work with buckets, not by moving items:
     * <ul>
     *   <li>click while holding a bucketed fish: it goes in (this slot, or the first free one) and you hold the
     *       empty bucket;</li>
     *   <li>click a fish while holding a bucket that can carry it: you hold the fish in that bucket;</li>
     *   <li>click a fish with an empty hand: it is scooped with a bucket from the tank's bucket slot, or else from
     *       your inventory (an empty bucket first, a filled one only if needed);</li>
     *   <li>shift-click a fish: the same, straight into your inventory.</li>
     * </ul>
     */
    @Override
    public void clicked(int id, int button, ClickType type, Player player) {
        if (id >= this.fishStart && id < VISIBLE && this.slots.get(id) instanceof FishSlot fs) {
            clickFish(fs, type, player);
            return;
        }
        super.clicked(id, button, type, player);
    }

    private void clickFish(FishSlot fs, ClickType type, Player player) {
        FishRef r = fs.target();
        if (r == null || (type != ClickType.PICKUP && type != ClickType.QUICK_MOVE)) return;
        ItemStack carried = getCarried();
        ItemStack inSlot = r.be().fishHandler().getStackInSlot(r.slot());
        if (type == ClickType.PICKUP && FishData.isFish(carried)) {
            ItemStack ret = this.controller.releaseFish(carried, r.be(), r.slot());
            if (!ret.isEmpty()) {
                carried.shrink(1);
                if (carried.isEmpty()) setCarried(ret);
                else give(player, ret);
            }
            return;
        }
        if (inSlot.isEmpty()) return;
        ItemStack container = ItemStack.EMPTY;
        if (type == ClickType.PICKUP && !carried.isEmpty()) {
            if (!FishData.canScoop(carried, inSlot, this.controller.habitat())) return;
            container = carried.split(1);
        } else {
            container = this.controller.takeScoopFromSlot(inSlot);
            if (container.isEmpty()) container = takeFromInventory(player, inSlot);
            if (container.isEmpty()) return;
        }
        ItemStack fish = inSlot.copy();
        r.be().fishHandler().setStackInSlot(r.slot(), ItemStack.EMPTY);
        if (type == ClickType.PICKUP && getCarried().isEmpty()) setCarried(fish);
        else give(player, fish);
    }

    /** One bucket from the player that can scoop this fish: the cheap empty kind first, a filled one if needed. */
    private ItemStack takeFromInventory(Player player, ItemStack fish) {
        var inv = player.getInventory();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!FishData.canScoop(s, fish, this.controller.habitat())) continue;
                if (pass == 0 && !FishData.isCheapScoop(s, fish, this.controller.habitat())) continue;
                return s.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Whether a fish could be scooped right now with an empty hand (for the screen's hint). */
    public boolean canScoopNow(Player player, ItemStack fish) {
        if (FishData.canScoop(this.controller.sharedHandler().getStackInSlot(TankBlockEntity.BUCKET_SLOT), fish, this.controller.habitat())) return true;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (FishData.canScoop(inv.getItem(i), fish, this.controller.habitat())) return true;
        return false;
    }

    private static void give(Player player, ItemStack s) {
        if (!player.getInventory().add(s)) player.drop(s, false);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof FishSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || index < VISIBLE) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < this.playerStart) {
            if (!moveItemStackTo(stack, this.playerStart, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (FishData.isFish(stack)) {
            // A bucketed fish from the inventory: into the aquarium, the empty bucket takes its place.
            ItemStack ret = this.controller.releaseFish(stack, null, 0);
            if (ret.isEmpty()) return ItemStack.EMPTY;
            stack.shrink(1);
            if (stack.isEmpty()) slot.set(ret);
            else give(player, ret);
            return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, this.sharedStart, bucketSlot() + 1, false)) return ItemStack.EMPTY;
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

        /** Fish go in and out only through clicks with buckets (see clicked). */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
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

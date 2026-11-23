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
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.data.FishGenes;
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
 * The aquarium screen. Fish slots: two per block, nine to a row, at most three rows on show with a scroll bar when
 * there are more (the scroll position is a menu button, so the server's slots follow); only as many slots as the
 * aquarium has. Then one line: the food slots on the left and the output slots on the right (one of each per block,
 * up to four), with the breeding heart between them; then the player's inventory.
 *
 * <p>Menu buttons: below {@link #BREEDING_BUTTON} the new top row; {@link #BREEDING_BUTTON} toggles breeding;
 * {@link #SEEN_BUTTON} + fish index marks a newborn as seen (its asterisk goes).
 */
public class AquariumMenu extends AbstractContainerMenu {
    public static final int COLS = 9, MAX_ROWS = 3, VISIBLE = COLS * MAX_ROWS;
    public static final int FISH_X = 8, FISH_Y = 54;
    /** How much taller the aquarium window is for bigger aquariums: four sizes. */
    public static int extraFor(int blocks) {
        return blocks <= 1 ? 0 : blocks <= 8 ? 20 : blocks <= 64 ? 40 : 60;
    }

    /** This screen's window growth (all slots below the window move down by it). */
    public final int extra;
    /** Fish rows on show (1 to 3), and the y of the first fish row, the food/output line and the inventory label. */
    public final int visRows, fishY, lineY, invY;
    /** The breeding heart's slot-sized spot: the middle column of the food/output line. */
    public static final int BREED_COL = 4;
    public static final int BREEDING_BUTTON = 100_000, SEEN_BUTTON = 200_000;

    public final TankBlockEntity controller;
    public final ContainerData data;
    /** Every fish slot in the aquarium: block, slot. */
    private final List<FishRef> refs = new ArrayList<>();
    private int row;
    public final int fishStart = 0, sharedStart = VISIBLE, playerStart;
    /** Food and output slots shown: one per tank block, up to four each. */
    public final int foodCount, outputCount;
    /** Blocks in this aquarium (breeding needs {@link TankBlockEntity#BREED_MIN_BLOCKS}). */
    public final int blocks;

    public record FishRef(TankBlockEntity be, int slot) {}

    public AquariumMenu(int id, Inventory inv, TankBlockEntity controller, List<TankBlockEntity> members, ContainerData data) {
        super(Registry.AQUARIUM_MENU.get(), id);
        this.controller = controller;
        this.data = data;
        this.extra = extraFor(members.size());
        this.blocks = members.size();
        int e = this.extra;
        for (int s = 0; s < TankBlockEntity.FISH; s++) for (TankBlockEntity t : members) this.refs.add(new FishRef(t, s));
        this.visRows = Mth.clamp(rows(), 1, MAX_ROWS);
        this.fishY = e + FISH_Y;
        this.lineY = this.fishY + this.visRows * 18 + 8;
        this.invY = this.lineY + 24;
        for (int i = 0; i < VISIBLE; i++) addSlot(new FishSlot(this, i, FISH_X + (i % COLS) * 18, this.fishY + (i / COLS) * 18));
        ItemStackHandler shared = controller.sharedHandler();
        this.foodCount = TankBlockEntity.foodSlots(members.size());
        this.outputCount = TankBlockEntity.outputSlots(members.size());
        for (int i = 0; i < this.foodCount; i++) addSlot(new SlotItemHandler(shared, TankBlockEntity.FOOD_START + i, 8 + i * 18, this.lineY));
        for (int i = 0; i < this.outputCount; i++)
            addSlot(new SlotItemHandler(shared, TankBlockEntity.OUTPUT_START + i, 8 + (COLS - this.outputCount + i) * 18, this.lineY));
        this.playerStart = this.slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, this.invY + 12 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, this.invY + 70));
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

    public int maxRow() { return Math.max(0, rows() - this.visRows); }

    public int row() { return this.row; }

    public void setRow(int r) { this.row = Mth.clamp(r, 0, maxRow()); }

    @Nullable
    public FishRef ref(int visible) {
        if (visible >= this.visRows * COLS) return null; // rows past the ones on show
        int i = this.row * COLS + visible;
        return i < this.refs.size() ? this.refs.get(i) : null;
    }

    @Nullable
    public FishRef refByIndex(int i) {
        return i >= 0 && i < this.refs.size() ? this.refs.get(i) : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BREEDING_BUTTON) {
            this.controller.toggleBreeding();
            return true;
        }
        if (id >= SEEN_BUTTON) {
            FishRef r = refByIndex(id - SEEN_BUTTON);
            if (r != null) {
                ItemStack f = r.be().fishHandler().getStackInSlot(r.slot()).copy();
                if (FishGenes.isUnseen(f)) {
                    FishGenes.markSeen(f);
                    r.be().fishHandler().setStackInSlot(r.slot(), f);
                }
            }
            return true;
        }
        setRow(id);
        return true;
    }

    /** Index of the last food slot among the menu's slots, plus one. */
    public int foodEnd() { return this.sharedStart + this.foodCount; }

    /**
     * A newborn only comes out when scooped: click its slot holding its habitat's bucket (a water bucket, a lava
     * bucket, or a Void Bucket for void fish). The bucket becomes the fish in a bucket.
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType type, Player player) {
        if (slotId >= 0 && slotId < VISIBLE && this.slots.get(slotId) instanceof FishSlot fs) {
            ItemStack fish = fs.getItem();
            if (FishGenes.isNewborn(fish)) {
                ItemStack carried = getCarried();
                if (type == net.minecraft.world.inventory.ClickType.PICKUP && !carried.isEmpty() && carried.is(FishData.scoop(fish))) {
                    ItemStack out = fish.copy();
                    FishGenes.clearNewborn(out);
                    fs.set(ItemStack.EMPTY);
                    if (carried.getCount() == 1) {
                        setCarried(out);
                    } else {
                        carried.shrink(1);
                        give(player, out);
                    }
                }
                return; // no other way out for a newborn
            }
        }
        super.clicked(slotId, button, type, player);
    }

    private static void give(Player player, ItemStack s) {
        if (!player.getInventory().add(s)) player.drop(s, false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < this.playerStart) {
            if (index < VISIBLE && FishGenes.isNewborn(stack)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, this.playerStart, this.slots.size(), true)) return ItemStack.EMPTY;
            if (index < VISIBLE) {
                // Fish slots aren't backed by a Container: write the change through.
                slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                return ItemStack.EMPTY;
            }
        } else if (FishData.isFish(stack)) {
            // A bucket of fish goes into the first free fish slot anywhere in the aquarium, not just the rows on show.
            boolean moved = false;
            for (FishRef r : this.refs) {
                if (stack.isEmpty()) break;
                ItemStackHandler h = r.be().fishHandler();
                if (h.getStackInSlot(r.slot()).isEmpty() && h.isItemValid(r.slot(), stack)) {
                    h.setStackInSlot(r.slot(), stack.split(1));
                    moved = true;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, this.sharedStart, foodEnd(), false)) return ItemStack.EMPTY;
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
            return target() != null && !FishGenes.isNewborn(getItem());
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

        /** This slot's index among all the aquarium's fish slots. */
        public int index() { return this.menu.row * COLS + this.visible; }
    }
}

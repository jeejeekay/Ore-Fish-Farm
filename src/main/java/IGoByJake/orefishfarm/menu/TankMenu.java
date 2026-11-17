package IGoByJake.orefishfarm.menu;

import IGoByJake.orefishfarm.block.BaseTankBlockEntity;
import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Shared tank menu. Slots: the fish slots, food, output, then the player's inventory. Shift-click sends fish to an
 * empty fish slot and food to the food slot.
 */
public abstract class TankMenu extends AbstractContainerMenu {
    public final BaseTankBlockEntity be;
    public final ContainerData data;
    protected final int fishSlots;
    protected final int tankSlots;

    /** fishPos: x,y pairs for each fish slot; then food x,y; then output x,y. */
    protected TankMenu(MenuType<?> type, int id, Inventory inv, BaseTankBlockEntity be, ContainerData data, int fishSlots, int... pos) {
        super(type, id);
        this.be = be;
        this.data = data;
        this.fishSlots = fishSlots;
        this.tankSlots = fishSlots + 2;
        for (int i = 0; i < this.tankSlots; i++) {
            addSlot(new SlotItemHandler(be.items(), i, pos[i * 2], pos[i * 2 + 1]));
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
        addDataSlots(data);
    }

    public int get(int i) {
        return this.data.get(i);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int end = this.slots.size();
        if (index < this.tankSlots) {
            if (!moveItemStackTo(stack, this.tankSlots, end, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            // Fish first (a fish that is also food goes in as a fish), then food.
            for (int i = 0; i < this.fishSlots && !stack.isEmpty(); i++) {
                Slot s = this.slots.get(i);
                if (!s.hasItem() && s.mayPlace(stack)) {
                    s.set(stack.split(1));
                    moved = true;
                }
            }
            if (!stack.isEmpty() && this.slots.get(this.fishSlots).mayPlace(stack)) {
                moved |= moveItemStackTo(stack, this.fishSlots, this.fishSlots + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockPos p = this.be.getBlockPos();
        return !this.be.isRemoved() && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64;
    }
}

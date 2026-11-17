package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/** Holds a 1.x tank's saved contents until it turns itself into a Fish Tank. */
public class LegacyTankBlockEntity extends BlockEntity {
    private CompoundTag old = new CompoundTag();
    private boolean done;

    public LegacyTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.old = tag.copy();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (String k : this.old.getAllKeys()) if (!tag.contains(k)) tag.put(k, this.old.get(k).copy());
    }

    void convert() {
        if (this.done || this.level == null || this.level.isClientSide) return;
        this.done = true;
        boolean breeding = getBlockState().getBlock() instanceof LegacyTankBlock l && l.breeding();
        // 1.x layout: fish slot(s), then food, then output.
        int fishSlots = breeding ? 2 : 1;
        ItemStackHandler items = new ItemStackHandler(fishSlots + 2);
        if (this.old.contains("Items")) items.deserializeNBT(this.old.getCompound("Items"));
        List<ItemStack> fish = new ArrayList<>();
        for (int i = 0; i < fishSlots && i < items.getSlots(); i++) fish.add(items.getStackInSlot(i).copy());
        ItemStack food = items.getSlots() > fishSlots ? items.getStackInSlot(fishSlots).copy() : ItemStack.EMPTY;
        ItemStack out = items.getSlots() > fishSlots + 1 ? items.getStackInSlot(fishSlots + 1).copy() : ItemStack.EMPTY;
        int fe = this.old.getInt("Energy");
        BlockPos pos = this.worldPosition;
        BlockState ns = Registry.FISH_TANK.get().defaultBlockState().setValue(TankBlock.ROLE, breeding ? TankRole.FISH : TankRole.POWER);
        this.level.setBlock(pos, ns, 3);
        if (this.level.getBlockEntity(pos) instanceof TankBlockEntity t) t.acceptLegacy(fish, food, out, breeding, breeding ? 0 : fe);
    }
}

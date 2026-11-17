package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.menu.FishTankMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** One fish, one food slot, one output: each finished piece of food rolls the fish's product chance (scales, teeth). */
public class FishTankBlockEntity extends BaseTankBlockEntity {

    /** Menu status codes, shown as text in the screen. */
    public static final int OK = 0, NO_FISH = 1, NO_FOOD = 2, OUTPUT_FULL = 3, MAKES_NOTHING = 4;

    public FishTankBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.FISH_TANK_BE.get(), pos, state, 1);
    }

    @Nullable
    private FishData.Fish current() {
        return FishData.fish(fish(0));
    }

    @Override
    protected boolean canWork() {
        FishData.Fish f = current();
        return f != null && f.makesProduct() && outputFits(f.product());
    }

    @Override
    protected void onFoodFinished() {
        FishData.Fish f = current();
        if (f != null && f.makesProduct() && this.level != null
                && this.level.random.nextFloat() < f.productChance() * Config.PRODUCT_CHANCE.get()) {
            addOutput(f.product().copy());
        }
    }

    public int status() {
        FishData.Fish f = current();
        if (f == null) return NO_FISH;
        if (!f.makesProduct()) return MAKES_NOTHING;
        if (!outputFits(f.product())) return OUTPUT_FULL;
        if (this.burn <= 0 && FishData.foodTicks(this.items.getStackInSlot(this.foodSlot)) <= 0) return NO_FOOD;
        return OK;
    }

    /** Synced to the open screen: food left (per mille), status, chance (per mille, after config). */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            FishData.Fish f = current();
            return switch (i) {
                case 0 -> burnTotal <= 0 ? 0 : (int) (1000L * burn / burnTotal);
                case 1 -> status();
                case 2 -> f == null ? 0 : (int) Math.min(1000, Math.round(1000 * f.productChance() * Config.PRODUCT_CHANCE.get()));
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 3; }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.orefishfarm.fish_tank");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FishTankMenu(id, inv, this, this.data);
    }
}

package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.menu.BreedingTankMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Two fish, one food slot, one output. Each finished food rolls once: two of the same fish may make another of
 * that fish (its breed_chance); two different fish may make a hybrid if an orefishfarm_breeding file pairs them.
 * The parents stay. Babies go to the single output slot, so a farm has to pipe them out to keep breeding.
 */
public class BreedingTankBlockEntity extends BaseTankBlockEntity {

    public static final int OK = 0, NEED_TWO = 1, NO_FOOD = 2, OUTPUT_FULL = 3, CANT_BREED = 4;

    public BreedingTankBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.BREEDING_TANK_BE.get(), pos, state, 2);
    }

    private record Pairing(ItemStack result, float chance) {}

    @Nullable
    private Pairing pairing() {
        FishData.Fish a = FishData.fish(fish(0));
        FishData.Fish b = FishData.fish(fish(1));
        if (a == null || b == null) return null;
        if (a.item() == b.item()) {
            float c = (float) (a.breedChance() * Config.BREED_CHANCE.get());
            return c > 0 ? new Pairing(new ItemStack(a.item()), c) : null;
        }
        FishData.Cross x = FishData.cross(a.item(), b.item());
        if (x == null) return null;
        float c = (float) (x.chance() * Config.CROSSBREED_CHANCE.get());
        return c > 0 ? new Pairing(x.result(), c) : null;
    }

    @Override
    protected boolean canWork() {
        // The fish eat whenever there's food, even if they can't breed or the output is full.
        return FishData.fish(fish(0)) != null || FishData.fish(fish(1)) != null;
    }

    @Override
    protected void onFoodFinished() {
        Pairing p = pairing();
        if (p != null && this.level != null && this.level.random.nextFloat() < p.chance()) {
            addOutput(p.result().copy());
        }
    }

    public int status() {
        if (FishData.fish(fish(0)) == null || FishData.fish(fish(1)) == null) return NEED_TWO;
        Pairing p = pairing();
        if (p == null) return CANT_BREED;
        if (!outputFits(p.result())) return OUTPUT_FULL;
        if (this.burn <= 0 && FishData.foodTicks(this.items.getStackInSlot(this.foodSlot)) <= 0) return NO_FOOD;
        return OK;
    }

    /** Synced to the open screen: food left (per mille), status, chance (per mille), hybrid (0/1). */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            Pairing p = pairing();
            return switch (i) {
                case 0 -> burnTotal <= 0 ? 0 : (int) (1000L * burn / burnTotal);
                case 1 -> status();
                case 2 -> p == null ? 0 : (int) Math.min(1000, Math.round(1000 * p.chance()));
                case 3 -> p != null && fish(0).getItem() != fish(1).getItem() ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 4; }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.orefishfarm.breeding_tank");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new BreedingTankMenu(id, inv, this, this.data);
    }
}

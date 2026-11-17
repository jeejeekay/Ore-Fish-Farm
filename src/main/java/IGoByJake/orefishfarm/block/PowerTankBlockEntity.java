package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.menu.PowerTankMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Takes one power fish (a fish file with "power" above 0). While it's eating it makes that many FE per tick and
 * no product. The energy is a plain Forge Energy capability on every side and is pushed into any neighbour that
 * accepts it, so cables from other mods and Create: New Age's connectors all hook up. It keeps an output slot
 * like the other tanks so pipes and the screen layout stay the same, but nothing goes in it.
 */
public class PowerTankBlockEntity extends BaseTankBlockEntity {

    public static final int OK = 0, NO_FISH = 1, NO_FOOD = 2, ENERGY_FULL = 3;

    private final TankEnergy energy = new TankEnergy();
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> this.energy);

    public PowerTankBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.POWER_TANK_BE.get(), pos, state, 1);
    }

    @Override
    protected boolean acceptsFish(ItemStack stack) {
        if (!FishData.loaded()) return true;
        FishData.Fish f = FishData.fish(stack);
        return f != null && f.makesPower();
    }

    @Nullable
    private FishData.Fish current() {
        FishData.Fish f = FishData.fish(fish(0));
        return f != null && f.makesPower() ? f : null;
    }

    private int perTick() {
        FishData.Fish f = current();
        return f == null ? 0 : (int) Math.round(f.power() * Config.POWER.get());
    }

    @Override
    protected boolean canWork() {
        return current() != null && this.energy.getEnergyStored() < this.energy.getMaxEnergyStored();
    }

    @Override
    protected void burningTick() {
        this.energy.generate(perTick());
    }

    @Override
    protected void onFoodFinished() {}

    @Override
    protected void alwaysTick() {
        if (this.energy.getEnergyStored() <= 0 || this.level == null) return;
        int before = this.energy.getEnergyStored();
        for (Direction d : Direction.values()) {
            BlockEntity n = this.level.getBlockEntity(this.worldPosition.relative(d));
            if (n == null) continue;
            n.getCapability(ForgeCapabilities.ENERGY, d.getOpposite()).ifPresent(s -> {
                if (s.canReceive()) {
                    int sent = s.receiveEnergy(Math.min(this.energy.getEnergyStored(), Config.ENERGY_OUTPUT.get()), false);
                    if (sent > 0) this.energy.extractEnergy(sent, false);
                }
            });
            if (this.energy.getEnergyStored() <= 0) break;
        }
        if (this.energy.getEnergyStored() != before) setChanged();
    }

    public int status() {
        if (current() == null) return NO_FISH;
        if (this.energy.getEnergyStored() >= this.energy.getMaxEnergyStored()) return ENERGY_FULL;
        if (this.burn <= 0 && FishData.foodTicks(this.items.getStackInSlot(this.foodSlot)) <= 0) return NO_FOOD;
        return OK;
    }

    /** Synced to the open screen: food left (per mille), status, energy (per mille), FE/tick, generating (0/1). */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> burnTotal <= 0 ? 0 : (int) (1000L * burn / burnTotal);
                case 1 -> status();
                case 2 -> (int) (1000L * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored()));
                case 3 -> Math.min(30000, perTick());
                case 4 -> burn > 0 && canWork() ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 5; }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.orefishfarm.power_tank");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new PowerTankMenu(id, inv, this, this.data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", this.energy.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.energy.set(tag.getInt("Energy"));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return this.energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.energyCap.invalidate();
    }

    /** Output-only FE buffer. */
    private static final class TankEnergy extends EnergyStorage {
        TankEnergy() { super(Config.ENERGY_CAPACITY.get(), 0, Config.ENERGY_OUTPUT.get()); }
        void generate(int amount) { this.energy = Math.min(this.capacity, this.energy + Math.max(0, amount)); }
        void set(int amount) { this.energy = Math.max(0, Math.min(this.capacity, amount)); }
    }
}

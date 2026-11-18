package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.menu.AquariumMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One block of an aquarium. Every block owns two fish slots (the second only opens while the aquarium holds a
 * Love Lattice). The lowest block of the aquarium is its controller: it owns the shared food, upgrade and output
 * slots, the food timer and the energy buffer, and is the only block that ticks any logic. The others just hold
 * their fish and point at it.
 *
 * <p>Food: one timer for the whole aquarium. A piece of food lasts its ticks divided by the number of fish eating,
 * and when it finishes every eating fish rolls once, so N fish use food and make scales exactly like N separate
 * tanks. Power fish in a power tank make FE every tick food is burning.
 *
 * <p>Habitats: a fish in a tank of another habitat gets a grace period (the tank reaches for that habitat's food
 * at once). With the habitat food being eaten, it is fine. Without, after the grace it is hurt a few times and
 * then dies, dropping a Fish Bone. A fish that is being hurt stops eating, so it doesn't waste food.
 */
public class TankBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FISH = 2;
    public static final int FOOD = 6, UPGRADES = 3, OUTPUT = 8;
    /** Shared slots: food, Love Lattices, one bucket slot (empty buckets for newborn fish), output. */
    public static final int FOOD_START = 0, UPGRADE_START = FOOD, BUCKET_SLOT = FOOD + UPGRADES, OUTPUT_START = BUCKET_SLOT + 1, SHARED = OUTPUT_START + OUTPUT;
    /** Container data indices. */
    public static final int D_FOOD = 0, D_STATUS = 1, D_ENERGY = 2, D_RATE = 3, D_DIES_IN = 4, D_LATTICES = 5, D_FISH = 6, D_BLOCKS = 7, D_PROTECTED = 8, D_COUNT = 9;
    public static final int OK = 0, NO_FISH = 1, NO_FOOD = 2, OUTPUT_FULL = 3, ENERGY_FULL = 4, NEEDS_HABITAT_FOOD = 5, DYING = 6, NO_POWER_FISH = 7;

    final ItemStackHandler fish = new ItemStackHandler(FISH) {
        @Override
        protected void onContentsChanged(int slot) {
            stress[slot] = 0;
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return canHoldFish(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) { return 1; }
    };

    final ItemStackHandler shared = new ItemStackHandler(SHARED) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < UPGRADE_START) return FishData.isFood(stack) && (!FishData.loaded() || FishData.fish(stack) == null);
            if (slot < BUCKET_SLOT) return stack.is(Registry.LOVE_LATTICE.get());
            if (slot == BUCKET_SLOT) return FishData.isBabyBucket(stack);
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot >= UPGRADE_START && slot < BUCKET_SLOT) return 1;
            return slot == BUCKET_SLOT ? 16 : 64;
        }
    };

    final int[] stress = new int[FISH];
    private ResourceLocation habitat = FishData.WATER;
    /** The block running this aquarium; null until it has been grouped. */
    @Nullable BlockPos controller;
    /** Controller only: every block in the aquarium, itself first. */
    List<BlockPos> members = List.of();
    @Nullable private List<TankBlockEntity> memberCache;
    boolean needsCheck = true;

    int burn, burnTotal;
    ItemStack burnItem = ItemStack.EMPTY;
    int energy;

    boolean sinksDirty = true;
    private int sinkTimer;
    private final List<Sink> sinks = new ArrayList<>();
    private record Sink(BlockPos pos, Direction side) {}

    /** Last tick's numbers for an open screen. */
    private final int[] view = new int[D_COUNT];

    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new Automation(this));
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> new Energy(this));

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.FISH_TANK_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------ group access

    public ResourceLocation habitat() { return this.habitat; }

    void setHabitat(ResourceLocation h) {
        this.habitat = h;
        setChanged();
    }

    public TankRole role() {
        BlockState s = getBlockState();
        return s.hasProperty(TankBlock.ROLE) ? s.getValue(TankBlock.ROLE) : TankRole.FISH;
    }

    public boolean isController() {
        return this.controller == null || this.controller.equals(this.worldPosition);
    }

    @Nullable
    public TankBlockEntity controllerBE() {
        if (isController()) return this;
        if (this.level == null || !this.level.isLoaded(this.controller)) return null;
        return this.level.getBlockEntity(this.controller) instanceof TankBlockEntity t ? t : null;
    }

    public TankBlockEntity controllerOrSelf() {
        TankBlockEntity c = controllerBE();
        return c == null ? this : c;
    }

    /** Controller: every block of the aquarium. */
    public List<BlockPos> members() {
        return this.members.isEmpty() ? new ArrayList<>(List.of(this.worldPosition)) : this.members;
    }

    boolean isMemberOfSameGroup(BlockPos p) {
        TankBlockEntity c = controllerBE();
        return c != null && c.members().contains(p);
    }

    /** Controller: the loaded block entities of the aquarium, or null if part of it isn't loaded. */
    @Nullable
    public List<TankBlockEntity> memberBEs() {
        if (this.level == null) return null;
        if (this.memberCache != null) {
            boolean ok = true;
            for (TankBlockEntity t : this.memberCache) if (t.isRemoved()) { ok = false; break; }
            if (ok) return this.memberCache;
        }
        List<TankBlockEntity> out = new ArrayList<>();
        for (BlockPos p : members()) {
            if (!this.level.isLoaded(p)) return null;
            if (this.level.getBlockEntity(p) instanceof TankBlockEntity t) out.add(t);
            else return null;
        }
        this.memberCache = out;
        return out;
    }

    void groupChanged() {
        this.memberCache = null;
        this.sinksDirty = true;
    }

    public ItemStack fishStack(int slot) { return this.fish.getStackInSlot(slot); }

    public int stress(int slot) { return this.stress[slot]; }

    public ItemStackHandler fishHandler() { return this.fish; }

    public ItemStackHandler sharedHandler() { return this.shared; }

    public int latticeCount() {
        int n = 0;
        for (int i = UPGRADE_START; i < BUCKET_SLOT; i++) if (this.shared.getStackInSlot(i).is(Registry.LOVE_LATTICE.get())) n++;
        return n;
    }

    /** Whether a fish may go in this block's slot (asks the controller about role and lattices). */
    boolean canHoldFish(int slot, ItemStack stack) {
        if (!FishData.isFish(stack)) return false;
        TankBlockEntity c = controllerOrSelf();
        if (role() == TankRole.POWER && FishData.loaded()) {
            FishData.Fish f = FishData.fish(stack);
            if (f == null || !f.makesPower()) return false;
        }
        return slot == 0 || c.latticeCount() > 0;
    }

    /** Whether the fish would be living outside its habitat here. */
    public boolean isForeign(ItemStack fishStack) {
        return FishData.loaded() && !FishData.habitatOf(fishStack).equals(controllerOrSelf().habitat);
    }

    /** Controller: is there food in the shared slots that makes this tank's habitat livable? */
    boolean hasHabitatFood() {
        for (int i = FOOD_START; i < UPGRADE_START; i++) if (this.habitat.equals(FishData.foodHabitat(this.shared.getStackInSlot(i)))) return true;
        return false;
    }

    /** Controller: is a habitat food for this tank being eaten right now? */
    public boolean isProtected() {
        return this.burn > 0 && this.habitat.equals(FishData.foodHabitat(this.burnItem));
    }

    /** Controller: puts a fish in the first open slot anywhere in the aquarium. */
    public boolean insertFishAnywhere(ItemStack one) {
        List<TankBlockEntity> mem = memberBEs();
        if (mem == null) mem = List.of(this);
        for (int s = 0; s < FISH; s++) {
            for (TankBlockEntity t : mem) {
                if (t.fish.getStackInSlot(s).isEmpty() && t.canHoldFish(s, one)) {
                    t.fish.setStackInSlot(s, one);
                    return true;
                }
            }
        }
        return false;
    }

    /** Controller: food and lattices into the shared slots. Returns what didn't fit. */
    public ItemStack insertShared(ItemStack stack) {
        for (int i = FOOD_START; i < OUTPUT_START && !stack.isEmpty(); i++) stack = this.shared.insertItem(i, stack, false);
        return stack;
    }

    /** Puts a stack into the same section of this block's shared slots, ignoring slot rules. Returns the rest. */
    ItemStack placeShared(int fromSlot, ItemStack stack) {
        int start = fromSlot < UPGRADE_START ? FOOD_START : fromSlot < BUCKET_SLOT ? UPGRADE_START : fromSlot == BUCKET_SLOT ? BUCKET_SLOT : OUTPUT_START;
        int end = fromSlot < UPGRADE_START ? UPGRADE_START : fromSlot < BUCKET_SLOT ? BUCKET_SLOT : fromSlot == BUCKET_SLOT ? OUTPUT_START : SHARED;
        return merge(start, end, stack);
    }

    private ItemStack merge(int start, int end, ItemStack stack) {
        stack = stack.copy();
        for (int i = start; i < end && !stack.isEmpty(); i++) {
            ItemStack cur = this.shared.getStackInSlot(i);
            int limit = Math.min(this.shared.getSlotLimit(i), stack.getMaxStackSize());
            if (cur.isEmpty()) {
                int n = Math.min(limit, stack.getCount());
                this.shared.setStackInSlot(i, stack.split(n));
            } else if (ItemStack.isSameItemSameTags(cur, stack) && cur.getCount() < limit) {
                int n = Math.min(limit - cur.getCount(), stack.getCount());
                ItemStack grown = cur.copy();
                grown.grow(n);
                stack.shrink(n);
                this.shared.setStackInSlot(i, grown);
            }
        }
        return stack;
    }

    void takeEnergy(int fe) {
        TankBlockEntity c = controllerOrSelf();
        c.energy = Math.min(c.energyCapacity(), c.energy + fe);
        c.setChanged();
    }

    int energyCapacity() {
        return role() == TankRole.POWER ? (int) Math.min(Integer.MAX_VALUE, (long) Config.ENERGY_CAPACITY.get() * members().size()) : 0;
    }

    /** Called by a legacy 1.x tank turning into this block. */
    public void acceptLegacy(List<ItemStack> fishes, ItemStack food, ItemStack output, boolean lattice, int fe) {
        for (int i = 0; i < Math.min(FISH, fishes.size()); i++) this.fish.setStackInSlot(i, FishData.wrapLegacy(fishes.get(i)));
        if (!food.isEmpty()) drop(merge(FOOD_START, UPGRADE_START, food));
        if (!output.isEmpty()) drop(merge(OUTPUT_START, SHARED, output));
        if (lattice) this.shared.setStackInSlot(UPGRADE_START, new ItemStack(Registry.LOVE_LATTICE.get()));
        this.energy = fe;
        setChanged();
    }

    private void drop(ItemStack s) {
        if (!s.isEmpty() && this.level != null) Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1, this.worldPosition.getZ() + 0.5, s);
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, TankBlockEntity be) {
        if (!be.isController()) return;
        if (be.needsCheck) {
            be.needsCheck = false;
            if (!be.groupValid()) {
                Aquarium.rebuild(level, List.of(pos));
                return;
            }
        }
        be.tickController();
    }

    private boolean groupValid() {
        if (this.controller == null || this.level == null) return false;
        for (BlockPos p : members()) {
            if (!this.level.isLoaded(p)) continue;
            if (!(this.level.getBlockEntity(p) instanceof TankBlockEntity t)) return false;
            if (!this.worldPosition.equals(t.controller)) return false;
        }
        return true;
    }

    private record FishRef(TankBlockEntity be, int slot) {
        ItemStack stack() { return be.fish.getStackInSlot(slot); }
    }

    private void tickController() {
        List<TankBlockEntity> mem = memberBEs();
        if (mem == null || this.level == null) return; // part of the aquarium isn't loaded: wait
        boolean power = role() == TankRole.POWER;

        List<FishRef> fishes = new ArrayList<>();
        for (TankBlockEntity t : mem) for (int s = 0; s < FISH; s++) if (!t.fish.getStackInSlot(s).isEmpty()) fishes.add(new FishRef(t, s));

        // Visitors from another habitat: reach for the habitat food straight away.
        boolean needHabitatFood = false;
        for (FishRef f : fishes) if (!FishData.habitatOf(f.stack()).equals(this.habitat)) { needHabitatFood = true; break; }
        if (needHabitatFood && !isProtected()) {
            int slot = findFood(true);
            if (slot >= 0 && this.habitat.equals(FishData.foodHabitat(this.shared.getStackInSlot(slot)))) {
                List<FishRef> eaters = eaters(fishes);
                if (this.burn > 0) finishFood(eaters, power);
                startFood(slot, Math.max(1, eaters.size()));
            }
        }

        // Stress: grace, then hurt, then death.
        boolean safe = isProtected();
        int grace = Config.HABITAT_GRACE.get(), hurt = Config.HABITAT_HURT.get();
        int diesIn = -1;
        boolean anyHurt = false;
        List<FishRef> dead = new ArrayList<>();
        for (FishRef f : fishes) {
            int s = f.be.stress[f.slot];
            if (safe || FishData.habitatOf(f.stack()).equals(this.habitat)) {
                if (s != 0) f.be.stress[f.slot] = 0;
                continue;
            }
            s++;
            f.be.stress[f.slot] = s;
            if (s >= grace) anyHurt = true;
            if (s >= grace && s < grace + hurt) {
                if ((s - grace) % 20 == 0) this.level.playSound(null, f.be.worldPosition, SoundEvents.TROPICAL_FISH_HURT, SoundSource.BLOCKS, 1F, 1F);
            } else if (s >= grace + hurt) dead.add(f);
            int left = Math.max(0, (grace + hurt - s + 19) / 20);
            if (diesIn < 0 || left < diesIn) diesIn = left;
        }
        for (FishRef f : dead) {
            f.be.fish.setStackInSlot(f.slot, ItemStack.EMPTY);
            this.level.playSound(null, f.be.worldPosition, SoundEvents.TROPICAL_FISH_DEATH, SoundSource.BLOCKS, 1F, 1F);
            f.be.drop(addOutput(new ItemStack(Registry.FISH_BONE.get())));
            fishes.remove(f);
        }

        // Food.
        List<FishRef> eaters = eaters(fishes);
        int rate = 0;
        if (this.burn > 0 && !eaters.isEmpty()) {
            this.burn--;
            if (power) {
                for (FishRef f : eaters) {
                    FishData.Fish d = FishData.fish(f.stack());
                    if (d != null && d.makesPower()) rate += FishData.powerPerTick(d);
                }
                this.energy = (int) Math.min(energyCapacity(), (long) this.energy + rate);
            }
            if (this.burn == 0) finishFood(eaters, power);
            setChanged();
        }
        if (this.burn <= 0 && !eaters.isEmpty()) {
            int slot = findFood(needHabitatFood);
            if (slot >= 0) startFood(slot, eaters.size());
        }

        if (power && this.energy > 0) pushEnergy(mem);

        // Numbers for the screen.
        int status;
        boolean hasPowerFish = false;
        if (power) for (FishRef f : fishes) { FishData.Fish d = FishData.fish(f.stack()); if (d != null && d.makesPower()) hasPowerFish = true; }
        if (anyHurt) status = DYING;
        else if (diesIn >= 0) status = NEEDS_HABITAT_FOOD;
        else if (fishes.isEmpty()) status = NO_FISH;
        else if (power && !hasPowerFish) status = NO_POWER_FISH;
        else if (this.burn <= 0 && findFood(false) < 0) status = NO_FOOD;
        else if (power && this.energy >= energyCapacity()) status = ENERGY_FULL;
        else if (!power && outputFull()) status = OUTPUT_FULL;
        else status = OK;
        this.view[D_FOOD] = this.burnTotal <= 0 ? 0 : (int) (1000L * this.burn / this.burnTotal);
        this.view[D_STATUS] = status;
        this.view[D_ENERGY] = (int) (1000L * this.energy / Math.max(1, energyCapacity()));
        this.view[D_RATE] = Math.min(30000, rate);
        this.view[D_DIES_IN] = diesIn;
        this.view[D_LATTICES] = latticeCount();
        this.view[D_FISH] = fishes.size();
        this.view[D_BLOCKS] = mem.size();
        this.view[D_PROTECTED] = isProtected() ? 1 : 0;
    }

    private List<FishRef> eaters(List<FishRef> fishes) {
        int grace = Config.HABITAT_GRACE.get();
        List<FishRef> out = new ArrayList<>();
        for (FishRef f : fishes) if (f.be.stress[f.slot] < grace) out.add(f);
        return out;
    }

    /** Index of the food to eat next: habitat food first when needed, otherwise ordinary food before habitat food. */
    private int findFood(boolean habitatFirst) {
        if (habitatFirst) {
            for (int i = FOOD_START; i < UPGRADE_START; i++) {
                ItemStack s = this.shared.getStackInSlot(i);
                if (FishData.foodTicks(s) > 0 && this.habitat.equals(FishData.foodHabitat(s))) return i;
            }
        }
        for (int i = FOOD_START; i < UPGRADE_START; i++) {
            ItemStack s = this.shared.getStackInSlot(i);
            if (FishData.foodTicks(s) > 0 && FishData.foodHabitat(s) == null) return i;
        }
        for (int i = FOOD_START; i < UPGRADE_START; i++) if (FishData.foodTicks(this.shared.getStackInSlot(i)) > 0) return i;
        return -1;
    }

    private void startFood(int slot, int eaters) {
        ItemStack food = this.shared.getStackInSlot(slot);
        int ticks = FishData.foodTicks(food);
        if (ticks <= 0) return;
        this.burnItem = food.copyWithCount(1);
        this.burnTotal = Math.max(1, (int) Math.round(ticks * Config.FOOD_TIME.get() / Math.max(1, eaters)));
        this.burn = this.burnTotal;
        this.shared.extractItem(slot, 1, false);
        setChanged();
    }

    /** One finished piece of food: lattice pairs try to breed, every other eating fish rolls its product. */
    private void finishFood(List<FishRef> eaters, boolean power) {
        this.burn = 0;
        if (this.level == null) return;
        Set<FishRef> busy = new HashSet<>();
        int lattices = latticeCount();
        if (lattices > 0) breed(eaters, lattices, busy);
        if (power) return;
        for (FishRef f : eaters) {
            if (busy.contains(f)) continue;
            FishData.Fish d = FishData.fish(f.stack());
            if (d != null && d.makesProduct() && this.level.random.nextFloat() < FishData.productChance(d)) {
                ItemStack rest = addOutput(d.product().copy());
                if (!rest.isEmpty()) break; // output full: the rest of this food is wasted
            }
        }
    }

    private void breed(List<FishRef> eaters, int lattices, Set<FishRef> busy) {
        float mult = (float) (1 + Config.LATTICE_BONUS.get() * (lattices - 1));
        Map<Item, List<FishRef>> bySpecies = new LinkedHashMap<>();
        for (FishRef f : eaters) {
            FishData.Fish d = FishData.fish(f.stack());
            if (d != null) bySpecies.computeIfAbsent(d.item(), k -> new ArrayList<>()).add(f);
        }
        List<FishRef> singles = new ArrayList<>();
        for (List<FishRef> same : bySpecies.values()) {
            FishData.Fish d = FishData.fish(same.get(0).stack());
            float chance = d == null ? 0 : FishData.breedChance(d);
            int i = 0;
            if (chance > 0) {
                for (; i + 1 < same.size(); i += 2) {
                    busy.add(same.get(i));
                    busy.add(same.get(i + 1));
                    if (this.level.random.nextFloat() < chance * mult) placeBaby(d);
                }
            }
            for (; i < same.size(); i++) singles.add(same.get(i));
        }
        for (int a = 0; a < singles.size(); a++) {
            FishRef x = singles.get(a);
            if (busy.contains(x)) continue;
            FishData.Fish fx = FishData.fish(x.stack());
            for (int b = a + 1; b < singles.size(); b++) {
                FishRef y = singles.get(b);
                if (busy.contains(y)) continue;
                FishData.Fish fy = FishData.fish(y.stack());
                FishData.Cross c = fx == null || fy == null ? null : FishData.cross(fx.item(), fy.item());
                if (c == null) continue;
                busy.add(x);
                busy.add(y);
                if (this.level.random.nextFloat() < FishData.crossChance(c) * mult) {
                    FishData.Fish child = FishData.fish(c.result());
                    if (child != null) placeBaby(child);
                }
                break;
            }
        }
    }

    /**
     * A new fish needs a bucket: one empty bucket (a Void Bucket for void fish) from the bucket slot. It goes into a
     * free fish slot of the aquarium if there is one, otherwise into the output. Without a bucket or room it is lost.
     */
    private void placeBaby(FishData.Fish species) {
        ItemStack b = this.shared.getStackInSlot(BUCKET_SLOT);
        if (!b.is(FishData.habitat(species.habitat()).empty())) return;
        ItemStack baby = FishData.bucketOf(species);
        if (insertFishAnywhere(baby.copy()) || merge(OUTPUT_START, SHARED, baby.copy()).isEmpty()) {
            this.shared.extractItem(BUCKET_SLOT, 1, false);
        }
    }

    /** Into the shared output; returns what didn't fit. */
    ItemStack addOutput(ItemStack stack) {
        ItemStack rest = merge(OUTPUT_START, SHARED, stack);
        return rest;
    }

    private boolean outputFull() {
        for (int i = OUTPUT_START; i < SHARED; i++) {
            ItemStack s = this.shared.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return false;
        }
        return true;
    }

    private void pushEnergy(List<TankBlockEntity> mem) {
        if (this.level == null) return;
        if (this.sinksDirty || ++this.sinkTimer >= 100) {
            this.sinksDirty = false;
            this.sinkTimer = 0;
            this.sinks.clear();
            Set<BlockPos> inside = new HashSet<>(members());
            for (TankBlockEntity t : mem) {
                for (Direction d : Direction.values()) {
                    BlockPos n = t.worldPosition.relative(d);
                    if (inside.contains(n) || !this.level.isLoaded(n)) continue;
                    BlockEntity be = this.level.getBlockEntity(n);
                    if (be != null && !(be instanceof TankBlockEntity) && be.getCapability(ForgeCapabilities.ENERGY, d.getOpposite()).isPresent()) {
                        this.sinks.add(new Sink(n, d.getOpposite()));
                    }
                }
            }
        }
        int before = this.energy;
        for (Sink s : this.sinks) {
            if (this.energy <= 0) break;
            if (!this.level.isLoaded(s.pos())) continue;
            BlockEntity be = this.level.getBlockEntity(s.pos());
            if (be == null) { this.sinksDirty = true; continue; }
            be.getCapability(ForgeCapabilities.ENERGY, s.side()).ifPresent(e -> {
                if (e.canReceive()) {
                    int sent = e.receiveEnergy(Math.min(this.energy, Config.ENERGY_OUTPUT.get()), false);
                    if (sent > 0) this.energy -= sent;
                }
            });
        }
        if (this.energy != before) setChanged();
    }

    // ------------------------------------------------------------------ menu

    public final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return i >= 0 && i < D_COUNT ? view[i] : 0; }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return D_COUNT; }
    };

    /** Sent when the screen opens: the controller and every block, so the client can lay out the fish slots. */
    public void writeMenu(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.worldPosition);
        List<BlockPos> m = members();
        buf.writeVarInt(m.size());
        for (BlockPos p : m) buf.writeBlockPos(p);
    }

    @Override
    public Component getDisplayName() {
        int n = members().size();
        Component hab = FishData.habitatName(this.habitat);
        String key = role() == TankRole.POWER ? "container.orefishfarm.power_tank" : "container.orefishfarm.fish_tank";
        return n > 1 ? Component.translatable(key + ".aquarium", hab, n) : Component.translatable(key, hab);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        List<TankBlockEntity> mem = memberBEs();
        return new AquariumMenu(id, inv, this, mem == null ? List.of(this) : mem, this.data);
    }

    /** Area the aquarium covers (render bounds and screen reach). */
    public AABB bounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : members()) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return isController() ? bounds() : new AABB(this.worldPosition);
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Version", 2);
        tag.put("Fish", this.fish.serializeNBT());
        tag.put("Shared", this.shared.serializeNBT());
        tag.putIntArray("Stress", this.stress);
        tag.putString("Habitat", this.habitat.toString());
        if (this.controller != null) tag.putLong("Controller", this.controller.asLong());
        if (!this.members.isEmpty()) {
            long[] m = new long[this.members.size()];
            for (int i = 0; i < m.length; i++) m[i] = this.members.get(i).asLong();
            tag.putLongArray("Members", m);
        }
        tag.putInt("Burn", this.burn);
        tag.putInt("BurnTotal", this.burnTotal);
        if (!this.burnItem.isEmpty()) tag.put("BurnItem", this.burnItem.save(new CompoundTag()));
        tag.putInt("Energy", this.energy);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Fish")) this.fish.deserializeNBT(tag.getCompound("Fish"));
        if (tag.contains("Shared")) this.shared.deserializeNBT(tag.getCompound("Shared"));
        int version = tag.getInt("Version");
        if (tag.contains("Items") && !tag.contains("Fish")) {
            // A 1.x Fish Tank: [fish, food, output].
            ItemStackHandler old = new ItemStackHandler(3);
            old.deserializeNBT(tag.getCompound("Items"));
            if (old.getSlots() > 0) this.fish.setStackInSlot(0, FishData.wrapLegacy(old.getStackInSlot(0)));
            if (old.getSlots() > 1) this.shared.setStackInSlot(FOOD_START, old.getStackInSlot(1));
            if (old.getSlots() > 2) this.shared.setStackInSlot(OUTPUT_START, old.getStackInSlot(2));
            version = 2;
        }
        if (version < 2) {
            // 2.0 tanks: fish were plain items, and slot 9 was the first output slot (now the bucket slot).
            for (int i = 0; i < FISH; i++) this.fish.setStackInSlot(i, FishData.wrapLegacy(this.fish.getStackInSlot(i)));
            ItemStack nine = this.shared.getStackInSlot(BUCKET_SLOT);
            if (!nine.isEmpty() && !FishData.isBabyBucket(nine)) {
                ItemStack rest = merge(OUTPUT_START, SHARED, nine);
                this.shared.setStackInSlot(BUCKET_SLOT, rest);
            }
        }
        int[] s = tag.getIntArray("Stress");
        for (int i = 0; i < Math.min(s.length, FISH); i++) this.stress[i] = s[i];
        ResourceLocation h = tag.contains("Habitat") ? ResourceLocation.tryParse(tag.getString("Habitat")) : null;
        this.habitat = h == null ? FishData.WATER : h;
        this.controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        List<BlockPos> m = new ArrayList<>();
        for (long l : tag.getLongArray("Members")) m.add(BlockPos.of(l));
        this.members = m;
        this.memberCache = null;
        this.burn = tag.getInt("Burn");
        this.burnTotal = tag.getInt("BurnTotal");
        this.burnItem = tag.contains("BurnItem") ? ItemStack.of(tag.getCompound("BurnItem")) : ItemStack.EMPTY;
        this.energy = tag.getInt("Energy");
        this.needsCheck = true;
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        ResourceLocation before = this.habitat;
        super.onDataPacket(net, pkt);
        // A new habitat with the same look still needs its tint redrawn.
        if (this.level != null && this.level.isClientSide && !before.equals(this.habitat)) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 8);
        }
    }

    // ------------------------------------------------------------------ capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return this.itemCap.cast();
        if (cap == ForgeCapabilities.ENERGY && role() == TankRole.POWER) return this.energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.itemCap.invalidate();
        this.energyCap.invalidate();
    }

    /**
     * What pipes and hoppers see on any block of the aquarium: every fish slot, then the food slots, then the
     * output. Fish and food go in, only the output comes out. A fish from another habitat is only accepted when
     * the habitat food for this tank is in the food slots.
     */
    private record Automation(TankBlockEntity self) implements IItemHandler {
        private TankBlockEntity c() { return self.controllerOrSelf(); }

        private List<TankBlockEntity> mem() {
            List<TankBlockEntity> m = c().memberBEs();
            return m == null ? List.of(self) : m;
        }

        /** Slots: every fish slot (buckets of fish go in), the food, the bucket slot, the output. */
        @Override public int getSlots() { return mem().size() * FISH + FOOD + 1 + OUTPUT; }

        private int fishSlots() { return mem().size() * FISH; }

        /** Shared slot index for a slot after the fish slots. */
        private static int shared(int i) { return i < FOOD ? FOOD_START + i : i == FOOD ? BUCKET_SLOT : OUTPUT_START + i - FOOD - 1; }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            int f = fishSlots();
            if (slot < f) return mem().get(slot / FISH).fish.getStackInSlot(slot % FISH);
            return c().shared.getStackInSlot(shared(slot - f));
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            int f = fishSlots();
            if (slot < f) {
                // A bucket of fish goes in as it is.
                TankBlockEntity c = c();
                if (stack.isEmpty() || !FishData.isFish(stack)) return stack;
                if (c.isForeign(stack) && !c.hasHabitatFood()) return stack;
                return mem().get(slot / FISH).fish.insertItem(slot % FISH, stack, simulate);
            }
            int i = slot - f;
            if (i <= FOOD) return c().shared.insertItem(shared(i), stack, simulate);
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            int i = slot - fishSlots();
            if (i <= FOOD) return ItemStack.EMPTY;
            return c().shared.extractItem(shared(i), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int i = slot - fishSlots();
            if (i < 0) return 1;
            return c().shared.getSlotLimit(shared(i));
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            int f = fishSlots();
            if (slot < f) return FishData.isFish(stack) && mem().get(slot / FISH).canHoldFish(slot % FISH, stack);
            int i = slot - f;
            return i <= FOOD && c().shared.isItemValid(shared(i), stack);
        }
    }

    /** The aquarium's FE buffer, reachable from any of its blocks. Output only. */
    private record Energy(TankBlockEntity self) implements IEnergyStorage {
        private TankBlockEntity c() { return self.controllerOrSelf(); }
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) {
            TankBlockEntity c = c();
            int n = Math.min(c.energy, Math.min(max, Config.ENERGY_OUTPUT.get()));
            if (!simulate && n > 0) { c.energy -= n; c.setChanged(); }
            return n;
        }
        @Override public int getEnergyStored() { return c().energy; }
        @Override public int getMaxEnergyStored() { return c().energyCapacity(); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    }
}

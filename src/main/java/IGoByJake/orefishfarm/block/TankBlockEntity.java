package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.data.FishGenes;
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
 * One block of an aquarium. Every block owns two fish slots. The lowest block of the aquarium is its controller:
 * it owns the shared food and output slots, the food timer, the breeding switch and the energy buffer, and is the
 * only block that ticks any logic. The others just hold their fish and point at it.
 *
 * <p>Food: one timer for the whole aquarium. A piece of food lasts its ticks divided by how hungry the eating fish
 * are together (each fish's Hunger stat, 1 for an average fish), so the aquarium eats at their combined rate.
 * While food is being eaten a second timer counts bites: every {@code biteTicks} every eating fish rolls once, so N
 * fish use food and make scales exactly like N separate tanks, and how long food lasts never changes how often they
 * roll. Power fish in a power tank make FE every tick food is burning. Yield and Charge scale each fish's product
 * chance and FE.
 *
 * <p>Breeding (while switched on): pairs sometimes breed on a bite. A baby needs a free fish slot; it swims in the
 * tank as a newborn and only comes out when scooped with its habitat's bucket (see FishGenes).
 *
 * <p>Habitats: a fish in a tank of another habitat gets a grace period (the tank reaches for that habitat's food
 * at once). With the habitat food being eaten, it is fine. Without, after the grace it is hurt a few times and
 * then dies, leaving a Fish Bone and an empty bucket in the output. A fish that is being hurt stops eating, so it doesn't waste food.
 */
public class TankBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FISH = 2;
    public static final int FOOD = 6, OUTPUT = 9;
    /** Food slots in use: one per tank block, up to this many (the rest of {@link #FOOD} stays unused). */
    public static final int MAX_FOOD_SLOTS = 4;
    /** Output slots in use: one per tank block, up to this many (storage keeps {@link #OUTPUT}). */
    public static final int MAX_OUTPUT_SLOTS = 4;
    /** Breeding needs an aquarium of at least this many blocks. */
    public static final int BREED_MIN_BLOCKS = 2;
    /** Heat per tank block (eating heat fish × their Heat factor, summed) for each Create heat level. */
    public static final float HEAT_SMOULDERING = 0.25F, HEAT_KINDLED = 0.7F, HEAT_SEETHING = 1.4F;
    /** The aquarium's heat level now (0 none, 1 smouldering, 2 kindled, 3 seething); not saved, worked out each tick. */
    private int heatLevel = -1;
    /** Shared slots: food, then output. */
    public static final int FOOD_START = 0, OUTPUT_START = FOOD, SHARED = OUTPUT_START + OUTPUT;
    /** Shared-slot layout before 2.4 (food, 3 Love Lattices, a bucket slot, 8 output), for old saves. */
    private static final int OLD_SHARED = 18, OLD_OUTPUT_START = 10;
    /** Container data indices. */
    public static final int D_FOOD = 0, D_STATUS = 1, D_ENERGY = 2, D_RATE = 3, D_DIES_IN = 4, D_BREEDING = 5, D_FISH = 6, D_BLOCKS = 7, D_PROTECTED = 8, D_COUNT = 9;
    public static final int OK = 0, NO_FISH = 1, NO_FOOD = 2, OUTPUT_FULL = 3, ENERGY_FULL = 4, NEEDS_HABITAT_FOOD = 5, DYING = 6, NO_POWER_FISH = 7;

    final ItemStackHandler fish = new ItemStackHandler(FISH) {
        @Override
        protected void onContentsChanged(int slot) {
            stress[slot] = 0;
            breedTimer[slot] = -1; // a fish just put in starts its breeding timer from the top
            shedTimer[slot] = -1;  // and its shedding timer somewhere random, so fish shed staggered
            // A fish without stats or genes yet (made in a recipe, from creative) gets them on the way in.
            if (level != null && !level.isClientSide) FishGenes.ensure(getStackInSlot(slot), level.random);
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
            if (slot < OUTPUT_START) return FishData.isFood(stack) && (!FishData.loaded() || FishData.fish(stack) == null);
            return false;
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
    /** Ticks eaten since the last bite. Carries over from one piece of food to the next. */
    int bite;
    /** Length of the current aquarium bite (jittered; not saved). */
    private int biteLen;
    ItemStack burnItem = ItemStack.EMPTY;
    int energy;
    /** Part FE carried over between ticks, so small boosts (Charged) aren't rounded away. Not saved. */
    private float energyPart;
    /** Controller: whether pairs breed. */
    boolean breeding = true;

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

    public boolean breeding() { return this.breeding; }

    /** Controller: switches breeding on or off. */
    public void toggleBreeding() {
        this.breeding = !this.breeding;
        setChanged();
    }

    /** Whether a fish may go in this block's slot (power tanks take only power fish). */
    boolean canHoldFish(int slot, ItemStack stack) {
        if (!FishData.isFish(stack)) return false;
        if (role() == TankRole.POWER && FishData.loaded()) {
            FishData.Fish f = FishData.fish(stack);
            if (f == null || !f.makesPower()) return false;
        }
        return true;
    }

    /** Whether the fish would be living outside its habitat here. */
    public boolean isForeign(ItemStack fishStack) {
        return FishData.loaded() && !FishData.habitatOf(fishStack).equals(controllerOrSelf().habitat);
    }

    /** Food slots in use for an aquarium of this many blocks: one per block, up to {@link #MAX_FOOD_SLOTS}. */
    public static int foodSlots(int blocks) {
        return Math.max(1, Math.min(MAX_FOOD_SLOTS, blocks));
    }

    /** Output slots in use for an aquarium of this many blocks: one per block, up to {@link #MAX_OUTPUT_SLOTS}. */
    public static int outputSlots(int blocks) {
        return Math.max(1, Math.min(MAX_OUTPUT_SLOTS, blocks));
    }

    /** Controller: end of the output slots in use. */
    public int outputEnd() {
        List<TankBlockEntity> mem = memberBEs();
        return OUTPUT_START + outputSlots(mem == null ? 1 : mem.size());
    }

    /** Controller: end of the food slots in use. */
    public int foodEnd() {
        List<TankBlockEntity> mem = memberBEs();
        return FOOD_START + foodSlots(mem == null ? 1 : mem.size());
    }

    /** Controller: is there food in the shared slots that makes this tank's habitat livable? */
    boolean hasHabitatFood() {
        for (int i = FOOD_START; i < foodEnd(); i++) if (this.habitat.equals(FishData.foodHabitat(this.shared.getStackInSlot(i)))) return true;
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

    /** Controller: food into the shared slots. Returns what didn't fit. */
    public ItemStack insertShared(ItemStack stack) {
        for (int i = FOOD_START, end = foodEnd(); i < end && !stack.isEmpty(); i++) stack = this.shared.insertItem(i, stack, false);
        return stack;
    }

    /** Puts a stack into the same section of this block's shared slots, ignoring slot rules. Returns the rest. */
    ItemStack placeShared(int fromSlot, ItemStack stack) {
        return fromSlot < OUTPUT_START ? merge(FOOD_START, foodEnd(), stack) : merge(OUTPUT_START, outputEnd(), stack);
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
        if (!food.isEmpty()) drop(merge(FOOD_START, foodEnd(), food));
        if (!output.isEmpty()) drop(merge(OUTPUT_START, outputEnd(), output));
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
        // Food in slots the aquarium no longer uses (it got smaller): into the used ones, or dropped.
        int foodEnd = foodEnd();
        for (int i = foodEnd; i < OUTPUT_START; i++) {
            ItemStack extra = this.shared.getStackInSlot(i);
            if (extra.isEmpty()) continue;
            this.shared.setStackInSlot(i, ItemStack.EMPTY);
            drop(merge(FOOD_START, foodEnd, extra));
        }
        // The same for output.
        int outputEnd = outputEnd();
        for (int i = outputEnd; i < SHARED; i++) {
            ItemStack extra = this.shared.getStackInSlot(i);
            if (extra.isEmpty()) continue;
            this.shared.setStackInSlot(i, ItemStack.EMPTY);
            drop(merge(OUTPUT_START, outputEnd, extra));
        }
        boolean power = role() == TankRole.POWER;

        List<FishRef> fishes = new ArrayList<>();
        for (TankBlockEntity t : mem) for (int s = 0; s < FISH; s++) if (!t.fish.getStackInSlot(s).isEmpty()) fishes.add(new FishRef(t, s));

        // Visitors from another habitat: reach for the habitat food straight away.
        boolean needHabitatFood = false;
        for (FishRef f : fishes) if (!atHome(f.stack())) { needHabitatFood = true; break; }
        if (needHabitatFood && !isProtected()) {
            int slot = findFood(true);
            if (slot >= 0 && this.habitat.equals(FishData.foodHabitat(this.shared.getStackInSlot(slot)))) {
                this.burn = 0;
                startFood(slot, hungry(eaters(fishes), true));
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
            if (safe || atHome(f.stack())) {
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
            f.be.drop(addOutput(new ItemStack(net.minecraft.world.item.Items.BUCKET))); // the bucket it lived in
            fishes.remove(f);
        }

        // Food. Only fish that make something (or visitors, who need their habitat food) eat; the rest breed on the
        // aquarium's bite clock without food.
        List<FishRef> live = eaters(fishes);
        List<FishRef> eaters = hungry(live, true), rest = hungry(live, false);
        int rate = 0;
        boolean eating = this.burn > 0 && !eaters.isEmpty();
        if (eating) {
            this.burn--;
            if (power) {
                float fe = this.energyPart;
                for (FishRef f : eaters) {
                    FishData.Fish d = FishData.fish(f.stack());
                    if (d == null || !d.makesPower()) continue;
                    float r = FishData.powerPerTick(d) * FishGenes.factor(f.stack(), FishGenes.CHARGE);
                    if (FishGenes.hasGene(f.stack(), FishGenes.CHARGED)) r *= FishGenes.CHARGED_POWER;
                    fe += r;
                }
                rate = (int) fe;
                this.energyPart = fe - rate;
                this.energy = (int) Math.min(energyCapacity(), (long) this.energy + rate);
            }
            // Each fish sheds on its own timer (a bite, give or take the jitter), started at a random point.
            if (!power) for (FishRef f : eaters) {
                int[] t = f.be.shedTimer;
                if (t[f.slot] < 0) t[f.slot] = 1 + this.level.random.nextInt(biteLength());
                if (--t[f.slot] <= 0) {
                    t[f.slot] = biteLength();
                    shed(f);
                }
            }
            setChanged();
        }
        // Breeding goes by the aquarium's bite, also jittered: eating fish while they eat, the others always.
        List<FishRef> breeders = new ArrayList<>(rest);
        if (eating) breeders.addAll(eaters);
        if (!breeders.isEmpty()) {
            if (this.biteLen <= 0) this.biteLen = biteLength();
            if (++this.bite >= this.biteLen) {
                int elapsed = this.bite;
                this.bite = 0;
                this.biteLen = biteLength();
                takeBite(breeders, power, elapsed);
            }
            setChanged();
        }
        if (this.burn <= 0 && !eaters.isEmpty()) {
            int slot = findFood(needHabitatFood);
            if (slot >= 0) startFood(slot, eaters);
        }

        if (power && this.energy > 0) pushEnergy(mem);

        // Heat: the eating heat fish, each times its Heat stat factor, per tank block.
        // Hot Blooded: any eating heat fish with the gene lifts the aquarium one level (at most seething).
        float heat = 0;
        boolean hotBlooded = false;
        if (this.burn > 0) for (FishRef f : eaters) {
            FishData.Fish d = FishData.fish(f.stack());
            if (d == null || !d.makesHeat()) continue;
            heat += d.heat() * FishGenes.factor(f.stack(), FishGenes.HEAT);
            if (FishGenes.hasGene(f.stack(), FishGenes.HOT_BLOODED)) hotBlooded = true;
        }
        heat /= Math.max(1, mem.size());
        int heatLevel = heat >= HEAT_SEETHING ? 3 : heat >= HEAT_KINDLED ? 2 : heat >= HEAT_SMOULDERING ? 1 : 0;
        if (hotBlooded) heatLevel = Math.min(3, heatLevel + 1);
        this.heatLevel = heatLevel;
        for (TankBlockEntity m : mem) { // only touches blocks whose heat differs (cheap; no-op without Create)
            BlockState st = m.getBlockState(), hot = IGoByJake.orefishfarm.compat.CreateHeat.withHeat(st, heatLevel);
            if (hot != st) this.level.setBlock(m.worldPosition, hot, 3);
        }

        // Numbers for the screen.
        int status;
        boolean hasPowerFish = false;
        if (power) for (FishRef f : fishes) { FishData.Fish d = FishData.fish(f.stack()); if (d != null && d.makesPower()) hasPowerFish = true; }
        if (anyHurt) status = DYING;
        else if (diesIn >= 0) status = NEEDS_HABITAT_FOOD;
        else if (fishes.isEmpty()) status = NO_FISH;
        else if (power && !hasPowerFish) status = NO_POWER_FISH;
        else if (!eaters.isEmpty() && this.burn <= 0 && findFood(false) < 0) status = NO_FOOD;
        else if (power && this.energy >= energyCapacity()) status = ENERGY_FULL;
        else if (!power && outputFull()) status = OUTPUT_FULL;
        else status = OK;
        this.view[D_FOOD] = this.burnTotal <= 0 ? 0 : (int) (1000L * this.burn / this.burnTotal);
        this.view[D_STATUS] = status;
        this.view[D_ENERGY] = (int) (1000L * this.energy / Math.max(1, energyCapacity()));
        this.view[D_RATE] = Math.min(30000, rate);
        this.view[D_DIES_IN] = diesIn;
        this.view[D_BREEDING] = this.breeding ? 1 : 0;
        this.view[D_FISH] = fishes.size();
        this.view[D_BLOCKS] = mem.size();
        this.view[D_PROTECTED] = isProtected() ? 1 : 0;
    }

    /** The fish that need food (wantFood) or don't: those that make something, and visitors from another habitat. */
    private List<FishRef> hungry(List<FishRef> fishes, boolean wantFood) {
        List<FishRef> out = new ArrayList<>();
        for (FishRef f : fishes) {
            boolean needs = FishData.eats(FishData.fish(f.stack())) || !atHome(f.stack());
            if (needs == wantFood) out.add(f);
        }
        return out;
    }

    /** In its own habitat here, or Hardy (fine anywhere). */
    private boolean atHome(ItemStack fish) {
        return FishData.habitatOf(fish).equals(this.habitat) || FishGenes.hasGene(fish, FishGenes.HARDY);
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
            for (int i = FOOD_START; i < foodEnd(); i++) {
                ItemStack s = this.shared.getStackInSlot(i);
                if (FishData.foodTicks(s) > 0 && this.habitat.equals(FishData.foodHabitat(s))) return i;
            }
        }
        for (int i = FOOD_START; i < foodEnd(); i++) {
            ItemStack s = this.shared.getStackInSlot(i);
            if (FishData.foodTicks(s) > 0 && FishData.foodHabitat(s) == null) return i;
        }
        for (int i = FOOD_START; i < foodEnd(); i++) if (FishData.foodTicks(this.shared.getStackInSlot(i)) > 0) return i;
        return -1;
    }

    /** Starts the next piece of food; it lasts its ticks divided by the eaters' combined appetite (1 / Satiety factor each). */
    private void startFood(int slot, List<FishRef> eaters) {
        ItemStack food = this.shared.getStackInSlot(slot);
        int ticks = FishData.foodTicks(food);
        if (ticks <= 0) return;
        double appetite = 0;
        for (FishRef f : eaters) appetite += 1.0 / Math.max(0.05, FishGenes.factor(f.stack(), FishGenes.SATIETY));
        this.burnItem = food.copyWithCount(1);
        this.burnTotal = Math.max(1, (int) Math.round(ticks * Config.FOOD_TIME.get() / Math.max(0.05, appetite)));
        this.burn = this.burnTotal;
        this.shared.extractItem(slot, 1, false);
        setChanged();
    }

    /** One aquarium bite: pairs may breed (while breeding is on). elapsed: ticks since the last bite. */
    private void takeBite(List<FishRef> eaters, boolean power, int elapsed) {
        if (this.level == null) return;
        List<TankBlockEntity> mem = memberBEs();
        if (this.breeding && mem != null && mem.size() >= BREED_MIN_BLOCKS) breed(eaters, elapsed);
    }

    /**
     * One fish sheds: Yield above 1x makes more (the product chance x the Yield factor is how many products on
     * average: 2.25 = two, plus a 25% chance of a third). If the output is full, the rest is wasted.
     */
    private void shed(FishRef f) {
        FishData.Fish d = FishData.fish(f.stack());
        if (d == null || !d.makesProduct()) return;
        double expected = FishData.productChance(d) * FishGenes.factor(f.stack(), FishGenes.YIELD);
        int count = (int) Math.floor(expected) + (this.level.random.nextDouble() < expected - Math.floor(expected) ? 1 : 0);
        for (int k = 0; k < count; k++) {
            if (!addOutput(FishData.productFor(d, this.level.random, f.stack(), this.burnItem)).isEmpty()) return;
        }
    }

    /** Ticks left until each fish slot sheds again (-1 = not started; not saved). */
    final int[] shedTimer = new int[FISH];
    { java.util.Arrays.fill(this.shedTimer, -1); }

    /** One bite's length: biteTicks, give or take up to biteJitterTicks. */
    private int biteLength() {
        int j = Config.BITE_JITTER.get();
        return Math.max(1, Config.BITE_TICKS.get() + (j <= 0 || this.level == null ? 0 : this.level.random.nextInt(2 * j + 1) - j));
    }

    /** Ticks left on each fish slot's breeding timer (-1 = not started; not saved, so it restarts on a reload). */
    final int[] breedTimer = new int[FISH];
    { java.util.Arrays.fill(this.breedTimer, -1); }

    /**
     * Each eating fish (not a newborn) runs down its breeding timer by a bite. A fish whose timer is up picks a random
     * partner among the fish that haven't bred this bite and are a valid pair with it (FishData.canPair: same species,
     * a shared breeding group tag, or a breeding recipe), and tries for a baby (breedPair); its timer then starts
     * again. With no partner it keeps waiting and tries again next bite. Each fish breeds at most once a bite.
     * Newborns (not yet scooped out with a bucket) never breed, so a fresh baby can't pair up with its parents before
     * the player picks the pairs.
     */
    private void breed(List<FishRef> eaters, int bite) {
        List<FishRef> pool = new ArrayList<>(), ready = new ArrayList<>();
        for (FishRef f : eaters) {
            if (FishGenes.isNewborn(f.stack())) continue;
            FishData.Fish d = FishData.fish(f.stack());
            if (d == null) continue;
            pool.add(f);
            if (!FishData.canBreed(d)) continue;
            int[] t = f.be().breedTimer;
            if (t[f.slot()] < 0) t[f.slot()] = FishData.breedTicks(d);
            t[f.slot()] = Math.max(0, t[f.slot()] - bite);
            if (t[f.slot()] == 0) ready.add(f);
        }
        for (int i = ready.size() - 1; i > 0; i--) java.util.Collections.swap(ready, i, this.level.random.nextInt(i + 1));
        Set<FishRef> done = new HashSet<>();
        for (FishRef me : ready) {
            if (done.contains(me)) continue;
            FishData.Fish fm = FishData.fish(me.stack());
            List<FishRef> mates = new ArrayList<>();
            for (FishRef o : pool) {
                if (o.equals(me) || done.contains(o)) continue;
                FishData.Fish fo = FishData.fish(o.stack());
                if (fo != null && FishData.canPair(fm, fo)) mates.add(o);
            }
            if (mates.isEmpty()) continue;
            FishRef mate = mates.get(this.level.random.nextInt(mates.size()));
            done.add(me);
            done.add(mate);
            me.be().breedTimer[me.slot()] = FishData.breedTicks(fm);
            breedPair(fm, FishData.fish(mate.stack()), me.stack(), mate.stack());
        }
    }

    /**
     * One baby of these two: one of the two species at random (a baby of two species is 3 levels lower, see
     * FishGenes.inherit). If a breeding recipe names the pair and the baby inherited all of its traits, the baby
     * becomes the recipe's fish instead. Then the baby is born only with the chance for what it is (FishData:
     * same species, a mix of two, or a recipe fish).
     */
    private void breedPair(FishData.Fish fa, FishData.Fish fb, ItemStack a, ItemStack b) {
        if (this.level == null) return;
        ItemStack baby = FishData.bucketOf(fa.item() == fb.item() || this.level.random.nextBoolean() ? fa : fb);
        FishGenes.inherit(baby, a, b, this.level.random);
        float chance = fa.item() == fb.item() ? FishData.sameSpeciesChance() : FishData.crossChance();
        for (FishData.Cross c : FishData.hybridsFor(fa.item(), fb.item())) {
            if (!FishGenes.hasAll(baby, c.traits())) continue;
            FishData.Fish h = FishData.fish(c.result());
            if (h == null) continue;
            ItemStack hybrid = FishData.bucketOf(h);
            FishGenes.copyData(baby, hybrid);
            FishGenes.addGenes(hybrid, FishGenes.speciesGenes(h.item()));
            baby = hybrid;
            chance = FishData.hybridChance(c);
            break;
        }
        if (this.level.random.nextFloat() >= chance) return;
        if (insertFishAnywhere(baby)) this.level.playSound(null, this.worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.4F);
    }


    /** Into the shared output; returns what didn't fit. */
    ItemStack addOutput(ItemStack stack) {
        return merge(OUTPUT_START, outputEnd(), stack);
    }

    private boolean outputFull() {
        for (int i = OUTPUT_START, end = outputEnd(); i < end; i++) {
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

    /** This block's shape ({@link TankGeometry#shape}): which neighbours share its aquarium; 0 until worked out. */
    int extras;
    public static final net.minecraftforge.client.model.data.ModelProperty<Integer> EXTRAS = new net.minecraftforge.client.model.data.ModelProperty<>();

    @Override
    public net.minecraftforge.client.model.data.ModelData getModelData() {
        int shape = this.extras;
        // not sent yet (or saved before 2.25): read it off the neighbours' block states, which say who they join
        if ((shape & TankGeometry.KNOWN) == 0 && this.level != null) shape = TankGeometry.shape(this::sharesAquarium);
        return net.minecraftforge.client.model.data.ModelData.builder().with(EXTRAS, shape).build();
    }

    /** Whether the block at this offset is in this block's aquarium, going by the joins in the block states. */
    private boolean sharesAquarium(int dx, int dy, int dz) {
        // step one axis at a time, each step through a side the block there says is joined (either order)
        int[][] orders = {{0, 1, 2}, {2, 1, 0}, {1, 0, 2}, {1, 2, 0}};
        for (int[] order : orders) {
            BlockPos at = this.worldPosition;
            boolean ok = true;
            for (int axis : order) {
                int v = axis == 0 ? dx : axis == 1 ? dy : dz;
                if (v == 0) continue;
                Direction d = axis == 0 ? (v > 0 ? Direction.EAST : Direction.WEST) : axis == 1 ? (v > 0 ? Direction.UP : Direction.DOWN) : (v > 0 ? Direction.SOUTH : Direction.NORTH);
                BlockState s = this.level.getBlockState(at);
                if (!(s.getBlock() instanceof TankBlock) || !s.getValue(TankBlock.side(d))) { ok = false; break; }
                at = at.relative(d);
            }
            if (ok) return true;
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Version", 3);
        if (this.extras != 0) tag.putInt("Shape", this.extras);
        tag.putBoolean("Breeding", this.breeding);
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
        tag.putInt("Bite", this.bite);
        if (!this.burnItem.isEmpty()) tag.put("BurnItem", this.burnItem.save(new CompoundTag()));
        tag.putInt("Energy", this.energy);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Fish")) this.fish.deserializeNBT(tag.getCompound("Fish"));
        int version = tag.getInt("Version");
        if (tag.contains("Shared")) {
            if (version >= 3) {
                this.shared.deserializeNBT(tag.getCompound("Shared"));
            } else {
                // Before 2.4: food, Love Lattices, a bucket slot, output. Keep the food and output; the old
                // lattice and bucket slots' items go to the output if there's room.
                ItemStackHandler old = new ItemStackHandler(OLD_SHARED);
                old.deserializeNBT(tag.getCompound("Shared"));
                for (int i = 0; i < SHARED; i++) this.shared.setStackInSlot(i, ItemStack.EMPTY);
                for (int i = 0; i < FOOD && i < old.getSlots(); i++) this.shared.setStackInSlot(FOOD_START + i, old.getStackInSlot(i));
                for (int i = OLD_OUTPUT_START; i < old.getSlots(); i++) merge(OUTPUT_START, SHARED, old.getStackInSlot(i));
                for (int i = FOOD; i < OLD_OUTPUT_START && i < old.getSlots(); i++) merge(OUTPUT_START, SHARED, old.getStackInSlot(i));
            }
        }
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
            // 2.0 tanks: fish were plain items.
            for (int i = 0; i < FISH; i++) this.fish.setStackInSlot(i, FishData.wrapLegacy(this.fish.getStackInSlot(i)));
        }
        this.breeding = !tag.contains("Breeding") || tag.getBoolean("Breeding");
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
        this.bite = tag.getInt("Bite");
        this.burnItem = tag.contains("BurnItem") ? ItemStack.of(tag.getCompound("BurnItem")) : ItemStack.EMPTY;
        this.energy = tag.getInt("Energy");
        this.extras = tag.getInt("Shape"); // "Extras" (before 2.25) meant something else: worked out again
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
        int extrasBefore = this.extras;
        super.onDataPacket(net, pkt);
        if (this.level != null && this.level.isClientSide && extrasBefore != this.extras) requestModelDataUpdate();
        // A new habitat with the same look still needs its tint redrawn; new extras need the model rebuilt.
        if (this.level != null && this.level.isClientSide && (!before.equals(this.habitat) || extrasBefore != this.extras)) {
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

        /** Slots: every fish slot (buckets of fish go in), the food, the output. */
        @Override public int getSlots() { return mem().size() * FISH + SHARED; }

        private int fishSlots() { return mem().size() * FISH; }

        /** Shared slot index for a slot after the fish slots. */
        private static int shared(int i) { return i; }

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
            if (i < FOOD) return i < c().foodEnd() ? c().shared.insertItem(shared(i), stack, simulate) : stack;
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            int i = slot - fishSlots();
            if (i < FOOD) return ItemStack.EMPTY;
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
            return i < c().foodEnd() && c().shared.isItemValid(shared(i), stack);
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

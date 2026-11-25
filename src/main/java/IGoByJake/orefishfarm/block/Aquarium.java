package IGoByJake.orefishfarm.block;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out which tanks form one aquarium. Touching tanks with the same habitat and role join, as long as the
 * aquarium stays within maxAquariumSize blocks on each axis; past that a new aquarium starts. Only runs when a
 * tank is placed, broken or converted (and once per controller after loading), never per tick.
 */
public final class Aquarium {
    private static final int SEARCH_CAP = 4096;

    private Aquarium() {}

    private static boolean compatible(TankBlockEntity a, TankBlockEntity b) {
        return a.role() == b.role() && a.habitat().equals(b.habitat());
    }

    @Nullable
    private static TankBlockEntity tank(Level level, BlockPos p) {
        if (!level.isLoaded(p)) return null;
        return level.getBlockEntity(p) instanceof TankBlockEntity t && !t.isRemoved() ? t : null;
    }

    /** Re-groups every aquarium touching these positions. */
    public static void rebuild(Level level, Collection<BlockPos> seeds) {
        if (level.isClientSide) return;
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos seed : seeds) {
            if (done.contains(seed)) continue;
            TankBlockEntity start = tank(level, seed);
            if (start == null) continue;
            List<BlockPos> comp = component(level, seed, start);
            done.addAll(comp);
            partition(level, comp);
        }
    }

    /** All touching, compatible tanks. */
    private static List<BlockPos> component(Level level, BlockPos seed, TankBlockEntity start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        seen.add(seed);
        while (!queue.isEmpty() && out.size() < SEARCH_CAP) {
            BlockPos p = queue.poll();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                TankBlockEntity t = tank(level, n);
                if (t == null || !compatible(start, t)) continue;
                seen.add(n);
                queue.add(n);
            }
        }
        return out;
    }

    /** Splits a component into aquariums within the size limit, lowest block first, and applies them. */
    private static void partition(Level level, List<BlockPos> comp) {
        int max = Config.MAX_AQUARIUM_SIZE.get();
        List<BlockPos> sorted = new ArrayList<>(comp);
        sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX()));
        Set<BlockPos> left = new LinkedHashSet<>(sorted);
        while (!left.isEmpty()) {
            BlockPos s = left.iterator().next();
            List<BlockPos> group = new ArrayList<>();
            int minX = s.getX(), maxX = s.getX(), minY = s.getY(), maxY = s.getY(), minZ = s.getZ(), maxZ = s.getZ();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(s);
            left.remove(s);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                group.add(p);
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (!left.contains(n)) continue;
                    int nx0 = Math.min(minX, n.getX()), nx1 = Math.max(maxX, n.getX());
                    int ny0 = Math.min(minY, n.getY()), ny1 = Math.max(maxY, n.getY());
                    int nz0 = Math.min(minZ, n.getZ()), nz1 = Math.max(maxZ, n.getZ());
                    if (nx1 - nx0 >= max || ny1 - ny0 >= max || nz1 - nz0 >= max) continue;
                    minX = nx0; maxX = nx1; minY = ny0; maxY = ny1; minZ = nz0; maxZ = nz1;
                    left.remove(n);
                    queue.add(n);
                }
            }
            apply(level, group);
        }
    }

    private static void apply(Level level, List<BlockPos> group) {
        BlockPos ctrlPos = group.get(0);
        TankBlockEntity c = tank(level, ctrlPos);
        if (c == null) return;
        Set<BlockPos> set = new HashSet<>(group);
        List<BlockPos> members = List.copyOf(group);
        int energy = 0;
        for (BlockPos p : group) {
            TankBlockEntity t = tank(level, p);
            if (t == null) continue;
            if (t != c) {
                // Its shared slots, food timer and energy pass to the controller.
                for (int i = 0; i < TankBlockEntity.SHARED; i++) {
                    ItemStack s = t.shared.getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    ItemStack rest = c.placeShared(i, s.copy());
                    t.shared.setStackInSlot(i, ItemStack.EMPTY);
                    if (!rest.isEmpty()) Containers.dropItemStack(level, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, rest);
                }
                if (t.burn > 0 && c.burn <= 0) {
                    c.burn = t.burn;
                    c.burnTotal = t.burnTotal;
                    c.burnItem = t.burnItem.copy();
                }
                t.burn = 0;
                t.burnTotal = 0;
                t.burnItem = ItemStack.EMPTY;
                energy += t.energy;
                t.energy = 0;
                if (!t.breeding) c.breeding = false; // switched off anywhere: stays off
                t.breeding = true;
                t.members = List.of();
            }
            t.controller = ctrlPos;
            t.needsCheck = false;
            t.groupChanged();
        }
        c.members = members;
        c.groupChanged();
        c.energy = Math.min(c.energyCapacity(), c.energy + energy);

        for (BlockPos p : group) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof TankBlock)) continue;
            BlockState ns = state;
            for (Direction d : Direction.values()) ns = ns.setValue(TankBlock.side(d), set.contains(p.relative(d)));
            if (ns != state) level.setBlock(p, ns, Block.UPDATE_CLIENTS);
            TankBlockEntity t = tank(level, p);
            if (t != null) {
                t.extras = extras(set, p);
                t.setChanged();
                level.sendBlockUpdated(p, ns, ns, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Changes the habitat and/or role of a whole aquarium, then re-groups it with whatever it now matches.
     * Pass null to leave either as it is.
     */
    public static void convert(Level level, TankBlockEntity controller, @Nullable ResourceLocation habitat, @Nullable TankRole role) {
        List<BlockPos> members = new ArrayList<>(controller.members());
        Set<BlockPos> touched = new LinkedHashSet<>(members);
        TankLook look = habitat == null ? null : TankLook.of(FishData.habitat(habitat).look());
        for (BlockPos p : members) {
            TankBlockEntity t = tank(level, p);
            if (t == null) continue;
            if (habitat != null) t.setHabitat(habitat);
            BlockState state = level.getBlockState(p);
            BlockState ns = state;
            if (look != null) ns = ns.setValue(TankBlock.LOOK, look);
            if (role != null) ns = ns.setValue(TankBlock.ROLE, role);
            if (ns != state) level.setBlock(p, ns, Block.UPDATE_ALL);
            for (Direction d : Direction.values()) touched.add(p.relative(d));
        }
        // Energy only makes sense in a power tank.
        if (role == TankRole.FISH) controller.energy = 0;
        rebuild(level, touched);
    }

    /** A tank block's shape for its glass and liquid ({@link TankGeometry#shape}): which neighbours share its aquarium. */
    static int extras(java.util.Set<BlockPos> set, BlockPos p) {
        return TankGeometry.shape((dx, dy, dz) -> set.contains(p.offset(dx, dy, dz)));
    }
}

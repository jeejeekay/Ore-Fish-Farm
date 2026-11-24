package IGoByJake.orefishfarm.pipe;

import IGoByJake.orefishfarm.Config;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Pipe Tank network in one dimension, saved with the world ({@code data/orefishfarm_pipes.dat}).
 *
 * <p>Pipes have no block entity. When one is placed, broken or changes its joins it is only marked here; at the
 * end of the tick every network it touched is worked out again in one go (a flood fill through the pipes), so
 * placing a hundred pipes at once is one rebuild, not a hundred. Pipes in unloaded chunks are taken as they were
 * saved, so a network that runs into unloaded chunks never splits by itself and never loads chunks.
 *
 * <p>Each tick: one pass over the networks comparing a number ({@link PipeNet#wake}); only due ones do anything.
 */
public final class PipeNetworks extends SavedData {
    private static final String NAME = "orefishfarm_pipes";

    private final Int2ObjectOpenHashMap<PipeNet> nets = new Int2ObjectOpenHashMap<>();
    /** Pipe position to network id. */
    private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private int nextId = 1;
    private ServerLevel level;

    private PipeNetworks() {
        this.index.defaultReturnValue(0);
    }

    public static PipeNetworks get(ServerLevel level) {
        PipeNetworks n = level.getDataStorage().computeIfAbsent(PipeNetworks::load, PipeNetworks::new, NAME);
        n.level = level;
        return n;
    }

    @Nullable
    public PipeNet netAt(BlockPos pos) {
        int id = this.index.get(pos.asLong());
        return id == 0 ? null : this.nets.get(id);
    }

    @Nullable
    public PipeNet byId(int id) {
        return this.nets.get(id);
    }

    void markDirty(BlockPos pos) {
        this.dirty.add(pos.asLong());
    }

    // ================================================================== ticking

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel sl)) return;
        PipeNetworks n = sl.getDataStorage().get(PipeNetworks::load, NAME);
        if (n == null) return;
        n.level = sl;
        n.flush();
        if (n.nets.isEmpty()) return;
        long now = sl.getGameTime();
        for (PipeNet net : n.nets.values()) if (net.wake <= now) net.update(sl, now);
    }

    // ================================================================== rebuilding

    private boolean isPipe(long p, LongOpenHashSet trusted) {
        BlockPos pos = BlockPos.of(p);
        if (!this.level.isLoaded(pos)) return trusted.contains(p);
        return this.level.getBlockState(pos).getBlock() instanceof PipeTankBlock;
    }

    /** Works out again every network a marked pipe touched. */
    public void flush() {
        if (this.dirty.isEmpty() || this.level == null) return;
        long[] marks = this.dirty.toLongArray();
        this.dirty.clear();
        // Old networks touched, and every pipe they had (trusted where its chunk isn't loaded).
        Map<Integer, PipeNet> olds = new HashMap<>();
        LongOpenHashSet seeds = new LongOpenHashSet();
        for (long m : marks) {
            seeds.add(m);
            take(this.index.get(m), olds);
            for (Direction d : Direction.values()) take(this.index.get(BlockPos.offset(m, d)), olds);
        }
        LongOpenHashSet trusted = new LongOpenHashSet();
        for (PipeNet o : olds.values()) trusted.addAll(o.pipes);
        for (PipeNet o : olds.values()) seeds.addAll(o.pipes);
        for (PipeNet o : olds.values()) {
            this.nets.remove(o.id);
            for (long p : o.pipes) this.index.remove(p);
        }
        // Flood fill the pieces.
        int max = Config.PIPE_MAX_NETWORK.get();
        LongOpenHashSet seen = new LongOpenHashSet();
        List<LongOpenHashSet> parts = new ArrayList<>();
        for (long s : seeds) {
            if (seen.contains(s) || !isPipe(s, trusted)) continue;
            LongOpenHashSet part = new LongOpenHashSet();
            LongArrayFIFOQueue q = new LongArrayFIFOQueue();
            q.enqueue(s);
            seen.add(s);
            while (!q.isEmpty() && part.size() < max) {
                long p = q.dequeueLong();
                part.add(p);
                for (Direction d : Direction.values()) {
                    long n = BlockPos.offset(p, d);
                    if (seen.contains(n) || !isPipe(n, trusted)) continue;
                    // A pipe of a network we weren't rebuilding (joined through an unloaded edge): merge it in.
                    int other = this.index.get(n);
                    if (other != 0 && !olds.containsKey(other)) {
                        PipeNet o = this.nets.remove(other);
                        if (o != null) {
                            olds.put(o.id, o);
                            trusted.addAll(o.pipes);
                            for (long op : o.pipes) this.index.remove(op);
                        }
                    }
                    seen.add(n);
                    q.enqueue(n);
                }
            }
            // Over the size limit: what's left in the queue starts its own network next time round.
            while (!q.isEmpty()) seen.remove(q.dequeueLong());
            parts.add(part);
        }
        // Each old network goes on in the piece holding most of its pipes; the biggest old one keeps its id.
        Map<LongOpenHashSet, List<PipeNet>> heirs = new java.util.IdentityHashMap<>();
        List<PipeNet> orphans = new ArrayList<>();
        for (PipeNet o : olds.values()) {
            LongOpenHashSet best = null;
            int bestN = 0;
            for (LongOpenHashSet part : parts) {
                int c = 0;
                for (long p : o.pipes) if (part.contains(p)) c++;
                if (c > bestN) { bestN = c; best = part; }
            }
            if (best == null) orphans.add(o);
            else heirs.computeIfAbsent(best, k -> new ArrayList<>()).add(o);
        }
        List<PipeNet.Endpoint> known = new ArrayList<>();
        for (PipeNet o : olds.values()) known.addAll(o.endpoints);
        for (LongOpenHashSet part : parts) {
            List<PipeNet> from = heirs.getOrDefault(part, List.of());
            PipeNet net;
            if (from.isEmpty()) {
                net = new PipeNet(this.nextId++);
            } else {
                from = new ArrayList<>(from);
                from.sort((a, b) -> Integer.compare(b.pipes.size(), a.pipes.size()));
                net = from.get(0);
                for (int i = 1; i < from.size(); i++) net.absorb(this.level, from.get(i));
            }
            net.pipes = part;
            net.clearRoutes();
            net.refreshEndpoints(this.level, known);
            net.settleFish(this.level, marks[0]);
            net.wake = 0;
            this.nets.put(net.id, net);
            for (long p : part) this.index.put(p, net.id);
        }
        // Networks whose pipes are all gone: their fish and loads drop where the last pipe was.
        for (PipeNet o : orphans) {
            BlockPos at = BlockPos.of(marks[0]);
            for (PipeNet.HaulFish f : o.fish) if (f != null) PipeNet.dropFish(this.level, f, at);
        }
        setDirty();
    }

    private void take(int id, Map<Integer, PipeNet> olds) {
        if (id == 0 || olds.containsKey(id)) return;
        PipeNet n = this.nets.get(id);
        if (n != null) olds.put(id, n);
    }

    // ================================================================== saving

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", this.nextId);
        ListTag list = new ListTag();
        for (PipeNet n : this.nets.values()) list.add(n.save());
        tag.put("Networks", list);
        long[] d = this.dirty.toLongArray();
        if (d.length > 0) tag.putLongArray("Dirty", d);
        return tag;
    }

    private static PipeNetworks load(CompoundTag tag) {
        PipeNetworks n = new PipeNetworks();
        n.nextId = Math.max(1, tag.getInt("NextId"));
        for (Tag t : tag.getList("Networks", Tag.TAG_COMPOUND)) {
            PipeNet net = PipeNet.load((CompoundTag) t);
            n.nets.put(net.id, net);
            for (long p : net.pipes) n.index.put(p, net.id);
            n.nextId = Math.max(n.nextId, net.id + 1);
        }
        for (long d : tag.getLongArray("Dirty")) n.dirty.add(d);
        return n;
    }
}

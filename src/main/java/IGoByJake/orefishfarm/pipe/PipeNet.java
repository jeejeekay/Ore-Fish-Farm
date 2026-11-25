package IGoByJake.orefishfarm.pipe;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.data.FishGenes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One network of joined Pipe Tanks: its pipes, the inventories they touch (lettered A, B, C...), the player's
 * commands ("A cobblestone to C, keep 128") and its hauling fish.
 *
 * <p>Built to stay cheap with hundreds of networks: nothing here runs every tick. {@link PipeNetworks} calls
 * {@link #update} only when {@link #wake} comes round: when a fish reaches the end of its swim, or when the
 * network looks for work (every scanTicks, slower and slower while it finds none, never while it has no idle
 * fish or no switched-on command). A swim is just a start and end time; the path is only worked out to send
 * to the clients that draw it.
 *
 * <p>A trip: the fish swims to the source, takes the load (checked again then), swims to the target and puts it
 * in. A load is reserved while it's on its way, so two fish never over-fill a "keep" amount or empty the same
 * stack twice. If the target is gone or full, the fish takes the rest back to the source; failing that, to
 * another inventory a switched-on command sends that item to; failing that it holds it and tries again on
 * each look. Items are never deleted; they only drop when a carrying fish's pipe is broken or the fish is
 * taken out.
 */
public final class PipeNet {
    public static final int MAX_FISH = 16;
    /** Most slots of one inventory looked through per command per look (big storage blocks stay cheap). */
    private static final int MAX_SLOTS_SCANNED = 256;
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> FAST_SWIMMERS =
            net.minecraft.tags.ItemTags.create(new ResourceLocation("orefishfarm", "fast_swimmers"));
    public static final byte IDLE = 0, TO_SOURCE = 1, TO_TARGET = 2, RETURNING = 3, HOLDING = 4;

    public final int id;
    LongOpenHashSet pipes = new LongOpenHashSet();
    final List<Endpoint> endpoints = new ArrayList<>();
    final List<Command> commands = new ArrayList<>();
    /** Where the next look for work starts in the command list (not saved). */
    int nextCommand;
    final HaulFish[] fish = new HaulFish[MAX_FISH];
    int nextEndpointId = 1;
    /** Game time of the next thing to do (Long.MAX_VALUE = nothing until something changes). */
    long wake = 0;
    private long nextScan = 0;
    private int idleScans = 0;
    /** Paths worked out since the network last changed (only for drawing). */
    private final Map<Long, long[]> routes = new HashMap<>();

    PipeNet(int id) {
        this.id = id;
    }

    // ================================================================== parts

    /** An inventory the network touches: a block (a double chest counts once) and, for sided blocks, the face. */
    public static final class Endpoint {
        final int id;
        String letter;
        String name = "";
        /** The block's position (a double chest: the lower of its two), and the face for sided blocks (-1 otherwise). */
        long key;
        int face;
        ResourceLocation block;
        /** Where the network reaches it: pipe position and the direction from the pipe to the block. */
        final List<long[]> access = new ArrayList<>();
        boolean missing;

        Endpoint(int id, String letter) {
            this.id = id;
            this.letter = letter;
        }

        boolean sameAs(long key, int face) {
            return this.key == key && this.face == face;
        }

        /** The block this endpoint is, as reached from pipe {@code pipe} (NONE if that pipe doesn't reach it). */
        long blockAt(long pipe) {
            for (long[] a : this.access) if (a[0] == pipe) return BlockPos.of(pipe).relative(net.minecraft.core.Direction.from3DDataValue((int) a[1])).asLong();
            return NONE;
        }

        long accessPipe(LongOpenHashSet pipes) {
            for (long[] a : this.access) if (pipes.contains(a[0])) return a[0];
            return this.access.isEmpty() ? BlockPos.asLong(0, -10000, 0) : this.access.get(0)[0];
        }

        String label() {
            return this.name.isEmpty() ? this.letter : this.letter + " " + this.name;
        }
    }

    /** "From A, this item (empty = any), to C", optionally keeping C topped up to {@link #keep} of it. */
    public static final class Command {
        ItemStack filter = ItemStack.EMPTY;
        int from, to;
        /** Keep the target at this many (-1 = move everything). */
        int keep = -1;
        boolean on;
    }

    /** A fish in the network and what it's doing. */
    public static final class HaulFish {
        ItemStack bucket;
        long uid;
        byte state = IDLE;
        /** Pipe it swims from and to this leg (when idle, {@code at} is where it rests), and the leg's times. */
        long from, at;
        long legStart, legEnd;
        /** The inventory block it swam into at the end of its last leg (drawn only; NONE = it's in a pipe). */
        long inside = NONE;
        /** The command (list index) this trip is for, shown in the screen; -1 = none. */
        int command = -1;
        ItemStack cargo = ItemStack.EMPTY;
        int amount;
        /** Source and target endpoints of the trip, and where a load goes back to. */
        int src, dst, ret;

        boolean moving() {
            return this.state == TO_SOURCE || this.state == TO_TARGET || this.state == RETURNING;
        }
    }

    // ================================================================== haul

    /** What this fish carries per trip, before the part-item roll: haulBase x its Haul multiplier x its file's "haul". */
    public static double haulAmount(ItemStack bucket, @Nullable FishData.Fish f) {
        double mult = f == null ? 1 : FishData.haulMultiplier(f);
        return Config.HAUL_BASE.get() * FishGenes.factor(bucket, FishGenes.HAUL) * mult;
    }

    private static int rollHaul(ItemStack bucket, RandomSource random) {
        double h = haulAmount(bucket, FishData.fish(bucket));
        int n = (int) Math.floor(h);
        if (random.nextDouble() < h - n) n++;
        return n;
    }

    // ================================================================== fish slots

    /** Fish slots: 1, plus one per 4 pipes, plus one per 2 connected inventories (config), up to the max. */
    public int capacity() {
        int inv = 0;
        for (Endpoint e : this.endpoints) if (!e.missing) inv++;
        int slots = 1 + this.pipes.size() / Config.PIPES_PER_FISH_SLOT.get() + inv / Config.INVENTORIES_PER_FISH_SLOT.get();
        return Mth.clamp(slots, 1, Math.min(MAX_FISH, Config.PIPE_MAX_FISH.get()));
    }

    public ItemStack fishStack(int slot) {
        HaulFish f = slot >= 0 && slot < MAX_FISH ? this.fish[slot] : null;
        return f == null ? ItemStack.EMPTY : f.bucket;
    }

    /** Puts a hauling fish in the first free slot. */
    public boolean addFish(ServerLevel level, ItemStack bucket) {
        for (int i = 0; i < capacity(); i++) {
            if (this.fish[i] == null) {
                setFish(level, i, bucket);
                return true;
            }
        }
        return false;
    }

    void setFish(ServerLevel level, int slot, ItemStack bucket) {
        ItemStack b = bucket.copyWithCount(1);
        FishGenes.ensure(b, level.random);
        HaulFish f = new HaulFish();
        f.bucket = b;
        f.uid = level.random.nextLong();
        f.at = f.from = anyPipe();
        this.fish[slot] = f;
        changed(level);
    }

    /** Takes a fish out of its slot; a load it carries goes back into its source, or drops. */
    ItemStack removeFish(ServerLevel level, int slot) {
        HaulFish f = this.fish[slot];
        if (f == null) return ItemStack.EMPTY;
        this.fish[slot] = null;
        if (f.amount > 0) {
            int left = f.amount;
            Endpoint s = byId(f.ret);
            IItemHandler h = s == null ? null : handler(level, s);
            if (h != null) left = insert(h, f.cargo, left);
            drop(level, BlockPos.of(f.at), f.cargo, left);
        }
        PipePackets.sendLegEnd(level, this, f);
        changed(level);
        return f.bucket;
    }

    // ================================================================== running

    /** Something the player or the world changed: look for work right away. */
    void changed(ServerLevel level) {
        this.idleScans = 0;
        this.nextScan = level.getGameTime();
        this.wake = this.nextScan;
        PipeNetworks.get(level).setDirty();
    }

    /** Does whatever is due. Called by {@link PipeNetworks} when {@link #wake} comes round. */
    void update(ServerLevel level, long now) {
        for (HaulFish f : this.fish) if (f != null && f.moving() && f.legEnd <= now) arrive(level, f, now);
        if (now >= this.nextScan) scan(level, now);
        long w = Long.MAX_VALUE;
        boolean idle = false, holding = false;
        for (HaulFish f : this.fish) {
            if (f == null) continue;
            if (f.moving()) w = Math.min(w, f.legEnd);
            else if (f.state == HOLDING) holding = true;
            else idle = true;
        }
        boolean work = holding || (idle && hasLiveCommand());
        if (work) w = Math.min(w, this.nextScan);
        this.wake = w;
    }

    private boolean hasLiveCommand() {
        for (Command c : this.commands) if (c.on) return true;
        return false;
    }

    /** Looks for work for every idle fish (and somewhere to put what holding fish carry). */
    private void scan(ServerLevel level, long now) {
        boolean did = false;
        for (HaulFish f : this.fish) if (f != null && f.state == HOLDING && placeHeld(level, f, now, -1)) did = true;
        List<HaulFish> idle = new ArrayList<>();
        for (HaulFish f : this.fish) if (f != null && f.state == IDLE) idle.add(f);
        // The commands are worked in turn: each pass gives at most one fish to each command, starting after the
        // last command that got one, so every command on the list gets served, not just the top one.
        int n = this.commands.size();
        boolean progress = true;
        while (!idle.isEmpty() && progress && n > 0) {
            progress = false;
            for (int k = 0; k < n && !idle.isEmpty(); k++) {
                int ci = Math.floorMod(this.nextCommand + k, n);
                Command c = this.commands.get(ci);
                if (!c.on || c.from == c.to) continue;
                Endpoint s = byId(c.from), t = byId(c.to);
                if (s == null || t == null || s.missing || t.missing) continue;
                IItemHandler hs = handler(level, s), ht = handler(level, t);
                if (hs == null || ht == null) continue;
                // Keep: count what's there plus what's on its way.
                int need = Integer.MAX_VALUE;
                if (c.keep >= 0) {
                    int have = c.filter.isEmpty() ? countAll(ht) : count(ht, c.filter);
                    need = c.keep - have - inbound(t.id, c.filter.isEmpty() ? null : c.filter);
                    if (need <= 0) continue;
                }
                ItemStack item = pick(c, hs, ht, s.id);
                if (item.isEmpty()) continue;
                int avail = count(hs, item) - outbound(s.id, item);
                int room = room(ht, item) - inbound(t.id, item);
                if (Math.min(avail, room) <= 0) continue;
                // the nearest idle fish to the source takes it
                long srcPipe = s.accessPipe(this.pipes);
                BlockPos sp = BlockPos.of(srcPipe);
                HaulFish f = java.util.Collections.min(idle, java.util.Comparator.comparingDouble(x -> BlockPos.of(x.at).distSqr(sp)));
                int haul = rollHaul(f.bucket, level.random);
                int amount = Math.min(Math.min(haul, avail), Math.min(room, need));
                if (amount <= 0) continue; // rolled nothing this time: it tries again next look
                idle.remove(f);
                f.cargo = item.copyWithCount(1);
                f.amount = amount;
                f.src = s.id;
                f.dst = t.id;
                f.ret = s.id;
                f.command = ci;
                startLeg(level, f, srcPipe, TO_SOURCE, now, s);
                this.nextCommand = ci + 1;
                did = progress = true;
            }
        }
        if (did) this.idleScans = 0;
        else this.idleScans++;
        int base = Config.PIPE_SCAN_TICKS.get();
        int wait = Math.min(Config.PIPE_MAX_SCAN_TICKS.get(), base << Math.min(4, this.idleScans / 3));
        this.nextScan = now + Math.max(1, wait);
        if (did) PipeNetworks.get(level).setDirty();
    }

    /** The item this command moves now: its filter, or (no filter) the first stack in the source the target takes. */
    private ItemStack pick(Command c, IItemHandler hs, IItemHandler ht, int srcId) {
        if (!c.filter.isEmpty()) return c.filter;
        int n = Math.min(hs.getSlots(), MAX_SLOTS_SCANNED);
        for (int i = 0; i < n; i++) {
            ItemStack st = hs.getStackInSlot(i);
            if (st.isEmpty() || hs.extractItem(i, 1, true).isEmpty()) continue;
            ItemStack one = st.copyWithCount(1);
            if (count(hs, one) - outbound(srcId, one) <= 0) continue;
            if (ItemHandlerHelper.insertItemStacked(ht, one, true).isEmpty()) return one;
        }
        return ItemStack.EMPTY;
    }

    private void arrive(ServerLevel level, HaulFish f, long now) {
        switch (f.state) {
            case TO_SOURCE -> {
                Endpoint s = byId(f.src);
                IItemHandler h = s == null ? null : handler(level, s);
                int got = h == null ? 0 : extract(h, f.cargo, f.amount);
                if (got <= 0) {
                    clear(f);
                    return;
                }
                f.amount = got;
                Endpoint t = byId(f.dst);
                startLeg(level, f, t == null ? f.at : t.accessPipe(this.pipes), TO_TARGET, now, t);
            }
            case TO_TARGET, RETURNING -> {
                Endpoint t = byId(f.dst);
                IItemHandler h = t == null || t.missing ? null : handler(level, t);
                int left = h == null ? f.amount : insert(h, f.cargo, f.amount);
                if (left <= 0) {
                    clear(f);
                    this.idleScans = 0;
                    this.nextScan = Math.min(this.nextScan, now);
                    return;
                }
                f.amount = left;
                // Target gone or full: back to where it came from, else anywhere a command wants it, else hold it.
                f.state = HOLDING;
                f.from = f.at;
                if (!placeHeld(level, f, now, f.dst)) {
                    f.legStart = f.legEnd = now;
                    this.nextScan = Math.min(this.nextScan, now + Config.PIPE_SCAN_TICKS.get());
                }
            }
            default -> {}
        }
        PipeNetworks.get(level).setDirty();
    }

    private static void clear(HaulFish f) {
        f.state = IDLE;
        f.command = -1;
        f.cargo = ItemStack.EMPTY;
        f.amount = 0;
    }

    /** Finds a home for what a holding fish carries (not {@code skip}): its source first, then any command's target. */
    private boolean placeHeld(ServerLevel level, HaulFish f, long now, int skip) {
        List<Integer> tries = new ArrayList<>();
        tries.add(f.ret);
        for (Command c : this.commands) {
            if (c.on && (c.filter.isEmpty() || ItemStack.isSameItemSameTags(c.filter, f.cargo)) && !tries.contains(c.to)) tries.add(c.to);
        }
        if (!tries.contains(f.dst)) tries.add(f.dst);
        for (int id : tries) {
            if (id == skip) continue;
            Endpoint e = byId(id);
            if (e == null || e.missing) continue;
            IItemHandler h = handler(level, e);
            if (h == null || ItemHandlerHelper.insertItemStacked(h, f.cargo.copyWithCount(1), true).getCount() > 0) continue;
            f.dst = id;
            startLeg(level, f, e.accessPipe(this.pipes), RETURNING, now, e);
            return true;
        }
        return false;
    }

    /**
     * Sends the fish to pipe {@code to}. For the drawing, a leg that ends at an inventory runs on into that block
     * (the fish swims into the chest), and the next leg starts back inside it.
     */
    private void startLeg(ServerLevel level, HaulFish f, long to, byte state, long now, @javax.annotation.Nullable Endpoint into) {
        long[] path = route(f.at, to);
        long[] shown = path;
        if (f.inside != NONE && path.length > 0) shown = prepend(f.inside, shown);
        long block = into == null ? NONE : into.blockAt(to);
        if (block != NONE && path.length > 0) shown = append(shown, block);
        f.inside = block;
        double speed = Config.PIPE_SWIM_SPEED.get();
        FishData.Fish d = FishData.fish(f.bucket);
        if (d != null && new ItemStack(d.item()).is(FAST_SWIMMERS)) speed *= 1.5;
        int ticks = Math.max(4, (int) Math.round((shown.length - 1) * 20 / speed));
        f.state = state;
        f.from = f.at;
        f.at = to;
        f.legStart = now;
        f.legEnd = now + ticks;
        PipePackets.sendLeg(level, this, f, shown);
    }

    static final long NONE = Long.MIN_VALUE;

    private static long[] prepend(long v, long[] a) {
        long[] o = new long[a.length + 1];
        o[0] = v;
        System.arraycopy(a, 0, o, 1, a.length);
        return o;
    }

    private static long[] append(long[] a, long v) {
        long[] o = java.util.Arrays.copyOf(a, a.length + 1);
        o[a.length] = v;
        return o;
    }

    // ================================================================== reservations

    /** What fish on their way to source {@code src} will take of this item. */
    private int outbound(int src, ItemStack item) {
        int n = 0;
        for (HaulFish f : this.fish) if (f != null && f.state == TO_SOURCE && f.src == src && ItemStack.isSameItemSameTags(f.cargo, item)) n += f.amount;
        return n;
    }

    /** What fish will bring to target {@code dst} of this item (null = of anything). */
    private int inbound(int dst, @Nullable ItemStack item) {
        int n = 0;
        for (HaulFish f : this.fish) {
            if (f == null || f.dst != dst || !(f.state == TO_SOURCE || f.state == TO_TARGET || f.state == RETURNING)) continue;
            if (item == null || ItemStack.isSameItemSameTags(f.cargo, item)) n += f.amount;
        }
        return n;
    }

    // ================================================================== inventories

    @Nullable
    IItemHandler handler(ServerLevel level, Endpoint e) {
        for (long[] a : e.access) {
            Direction d = Direction.from3DDataValue((int) a[1]);
            BlockPos inv = BlockPos.of(a[0]).relative(d);
            if (!level.isLoaded(inv)) continue;
            BlockEntity be = level.getBlockEntity(inv);
            if (be == null) continue;
            var h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, d.getOpposite()).resolve();
            if (h.isPresent()) return h.get();
        }
        return null;
    }

    static int count(IItemHandler h, ItemStack item) {
        int n = 0, slots = Math.min(h.getSlots(), MAX_SLOTS_SCANNED);
        for (int i = 0; i < slots; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, item)) n += s.getCount();
        }
        return n;
    }

    static int countAll(IItemHandler h) {
        int n = 0, slots = Math.min(h.getSlots(), MAX_SLOTS_SCANNED);
        for (int i = 0; i < slots; i++) n += h.getStackInSlot(i).getCount();
        return n;
    }

    /** How many of this item the inventory could take now. */
    static int room(IItemHandler h, ItemStack item) {
        int n = 0, slots = Math.min(h.getSlots(), MAX_SLOTS_SCANNED), max = item.getMaxStackSize();
        for (int i = 0; i < slots && n < 4096; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && !ItemStack.isSameItemSameTags(s, item)) continue;
            ItemStack rest = h.insertItem(i, item.copyWithCount(max), true);
            n += max - rest.getCount();
        }
        return n;
    }

    static int extract(IItemHandler h, ItemStack item, int want) {
        int got = 0, slots = Math.min(h.getSlots(), MAX_SLOTS_SCANNED);
        for (int i = 0; i < slots && got < want; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, item)) continue;
            got += h.extractItem(i, want - got, false).getCount();
        }
        return got;
    }

    /** Puts in up to {@code amount}; returns what didn't fit. */
    static int insert(IItemHandler h, ItemStack item, int amount) {
        int left = amount, max = Math.max(1, item.getMaxStackSize());
        while (left > 0) {
            int chunk = Math.min(left, max);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(h, item.copyWithCount(chunk), false);
            int in = chunk - rest.getCount();
            left -= in;
            if (in < chunk) break;
        }
        return left;
    }

    static void drop(ServerLevel level, BlockPos pos, ItemStack item, int amount) {
        if (item.isEmpty() || amount <= 0 || !level.isLoaded(pos)) return;
        while (amount > 0) {
            int n = Math.min(amount, item.getMaxStackSize());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item.copyWithCount(n));
            amount -= n;
        }
    }

    // ================================================================== paths

    long anyPipe() {
        var it = this.pipes.iterator();
        return it.hasNext() ? it.nextLong() : 0L;
    }

    void clearRoutes() {
        this.routes.clear();
    }

    /** Pipe positions from a to b through the network (breadth first; cached until the network changes). */
    long[] route(long a, long b) {
        if (a == b) return new long[]{a};
        long key = a * 31 + b;
        long[] cached = this.routes.get(key);
        if (cached != null && cached[0] == a && cached[cached.length - 1] == b) return cached;
        long[] path = bfs(a, b);
        if (this.routes.size() > 256) this.routes.clear();
        this.routes.put(key, path);
        return path;
    }

    private long[] bfs(long a, long b) {
        if (!this.pipes.contains(a) || !this.pipes.contains(b)) return new long[]{a, b};
        Long2ObjectOpenHashMap<Long> prev = new Long2ObjectOpenHashMap<>();
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        q.enqueue(a);
        prev.put(a, (Long) a);
        Direction[] dirs = Direction.values();
        while (!q.isEmpty()) {
            long p = q.dequeueLong();
            if (p == b) break;
            for (Direction d : dirs) {
                long n = BlockPos.offset(p, d);
                if (!this.pipes.contains(n) || prev.containsKey(n)) continue;
                prev.put(n, (Long) p);
                q.enqueue(n);
            }
        }
        if (!prev.containsKey(b)) return new long[]{a, b};
        List<Long> back = new ArrayList<>();
        long p = b;
        while (p != a) {
            back.add(p);
            p = prev.get(p);
        }
        back.add(a);
        long[] out = new long[back.size()];
        for (int i = 0; i < out.length; i++) out[i] = back.get(back.size() - 1 - i);
        return out;
    }

    // ================================================================== endpoints

    @Nullable
    Endpoint byId(int id) {
        for (Endpoint e : this.endpoints) if (e.id == id) return e;
        return null;
    }

    boolean usedByCommand(int id) {
        for (Command c : this.commands) if (c.from == id || c.to == id) return true;
        return false;
    }

    static String letterFor(int i) {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i > 0) {
            i--;
            sb.insert(0, (char) ('A' + i % 26));
            i /= 26;
        }
        return sb.toString();
    }

    String freeLetter() {
        for (int i = 0; ; i++) {
            String l = letterFor(i);
            boolean used = false;
            for (Endpoint e : this.endpoints) if (e.letter.equals(l)) { used = true; break; }
            if (!used) return l;
        }
    }

    /** A block's endpoint identity: its position (a double chest: the lower half's) and, for sided blocks, the face. */
    static long[] identity(ServerLevel level, BlockPos inv, Direction face) {
        BlockState st = level.getBlockState(inv);
        long key = inv.asLong();
        if (st.getBlock() instanceof ChestBlock && st.hasProperty(ChestBlock.TYPE) && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            long other = inv.relative(ChestBlock.getConnectedDirection(st)).asLong();
            key = Math.min(key, other);
        }
        int f = level.getBlockEntity(inv) instanceof WorldlyContainer ? face.get3DDataValue() : -1;
        return new long[]{key, f};
    }

    /**
     * Works out the inventories this network touches now. Loaded pipes are looked at; pipes in unloaded chunks keep
     * what they reached before. A found inventory keeps its letter and name; {@code known} holds endpoints from
     * the networks this one came from (a split keeps names on both sides). One no longer touched stays (shown as
     * missing) while a command uses it.
     */
    void refreshEndpoints(ServerLevel level, List<Endpoint> known) {
        record Found(long key, int face, ResourceLocation block, List<long[]> access) {}
        List<Found> found = new ArrayList<>();
        LongOpenHashSet loaded = new LongOpenHashSet();
        for (long p : this.pipes) {
            BlockPos pos = BlockPos.of(p);
            if (!level.isLoaded(pos)) continue;
            loaded.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (this.pipes.contains(n.asLong()) || !level.isLoaded(n)) continue;
                if (level.getBlockState(n).getBlock() instanceof PipeTankBlock) continue;
                if (!PipeTankBlock.hasInventory(level, n, d.getOpposite())) continue;
                long[] idn = identity(level, n, d.getOpposite());
                Found f = null;
                for (Found x : found) if (x.key == idn[0] && x.face == idn[1]) { f = x; break; }
                if (f == null) {
                    f = new Found(idn[0], (int) idn[1], ForgeRegistries.BLOCKS.getKey(level.getBlockState(n).getBlock()), new ArrayList<>());
                    found.add(f);
                }
                f.access.add(new long[]{p, d.get3DDataValue()});
            }
        }
        // Each old endpoint keeps the ways in through pipes that are in unloaded chunks (they can't be looked at).
        Map<Endpoint, List<long[]>> stale = new HashMap<>();
        for (Endpoint e : this.endpoints) {
            List<long[]> st = new ArrayList<>();
            for (long[] a : e.access) if (this.pipes.contains(a[0]) && !loaded.contains(a[0])) st.add(a);
            stale.put(e, st);
        }
        List<Endpoint> next = new ArrayList<>();
        for (Found f : found) {
            Endpoint e = null;
            for (Endpoint x : this.endpoints) if (x.sameAs(f.key, f.face)) { e = x; break; }
            if (e == null) {
                e = new Endpoint(this.nextEndpointId++, "?");
                e.key = f.key;
                e.face = f.face;
                for (Endpoint x : known) if (x.sameAs(f.key, f.face)) { e.name = x.name; e.letter = x.letter; break; }
            }
            e.access.clear();
            e.access.addAll(stale.getOrDefault(e, List.of()));
            e.access.addAll(f.access);
            e.block = f.block;
            e.missing = false;
            next.add(e);
        }
        for (Endpoint e : this.endpoints) {
            if (next.contains(e)) continue;
            List<long[]> st = stale.get(e);
            if (!st.isEmpty()) {
                e.access.clear();
                e.access.addAll(st);
                next.add(e);
            } else if (usedByCommand(e.id)) {
                // Gone, but a command still uses it: kept (shown as missing) so its letter and name come back with it.
                e.access.clear();
                e.missing = true;
                next.add(e);
            }
        }
        // Letters: each keeps its own unless two clash; new ones get the lowest free letter.
        this.endpoints.clear();
        for (Endpoint e : next) {
            boolean clash = e.letter.equals("?");
            for (Endpoint o : this.endpoints) if (o.letter.equals(e.letter)) clash = true;
            if (clash) e.letter = "?";
            this.endpoints.add(e);
        }
        for (Endpoint e : this.endpoints) if (e.letter.equals("?")) e.letter = freeLetter();
        this.endpoints.sort(java.util.Comparator.comparing((Endpoint e) -> e.letter.length()).thenComparing(e -> e.letter));
    }

    /** Merges another network into this one (they were joined): its inventories, commands and fish. */
    void absorb(ServerLevel level, PipeNet other) {
        Map<Integer, Integer> remap = new HashMap<>();
        for (Endpoint e : other.endpoints) {
            Endpoint mine = null;
            for (Endpoint x : this.endpoints) if (x.sameAs(e.key, e.face)) { mine = x; break; }
            if (mine == null) {
                mine = new Endpoint(this.nextEndpointId++, e.letter);
                mine.name = e.name;
                mine.key = e.key;
                mine.face = e.face;
                mine.block = e.block;
                mine.missing = e.missing;
                mine.access.addAll(e.access);
                for (Endpoint x : this.endpoints) if (x.letter.equals(mine.letter)) { mine.letter = "?"; break; }
                this.endpoints.add(mine);
                if (mine.letter.equals("?")) mine.letter = freeLetter();
            } else if (mine.name.isEmpty()) {
                mine.name = e.name;
            }
            remap.put(e.id, mine.id);
        }
        for (Command c : other.commands) {
            c.from = remap.getOrDefault(c.from, -1);
            c.to = remap.getOrDefault(c.to, -1);
            if (this.commands.size() < Config.PIPE_MAX_COMMANDS.get()) this.commands.add(c);
        }
        for (HaulFish f : other.fish) {
            if (f == null) continue;
            f.src = remap.getOrDefault(f.src, -1);
            f.dst = remap.getOrDefault(f.dst, -1);
            f.ret = remap.getOrDefault(f.ret, -1);
            int slot = -1;
            for (int i = 0; i < MAX_FISH; i++) if (this.fish[i] == null) { slot = i; break; }
            if (slot >= 0) this.fish[slot] = f;
            else dropFish(level, f, BlockPos.of(f.at));
        }
    }

    /** After the pipes changed: fish past the slot count, and fish whose pipe went, are dealt with. */
    void settleFish(ServerLevel level, long brokenHint) {
        int cap = capacity();
        for (int i = 0; i < MAX_FISH; i++) {
            HaulFish f = this.fish[i];
            if (f == null) continue;
            if (i >= cap) {
                this.fish[i] = null;
                dropFish(level, f, BlockPos.of(this.pipes.contains(f.at) ? f.at : anyPipe()));
                continue;
            }
            boolean lost = !this.pipes.contains(f.at) || (f.moving() && !this.pipes.contains(f.from));
            if (!lost) continue;
            // Its pipe was broken under it: what it carried drops there, and it carries on from a pipe still here.
            if (f.amount > 0) drop(level, BlockPos.of(this.pipes.contains(f.at) ? f.from : f.at), f.cargo, f.amount);
            PipePackets.sendLegEnd(level, this, f);
            clear(f);
            f.at = f.from = anyPipe();
        }
    }

    /** A fish with nowhere to be: it and its load drop as items. */
    static void dropFish(ServerLevel level, HaulFish f, BlockPos pos) {
        drop(level, pos, f.cargo, f.amount);
        if (level.isLoaded(pos)) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, f.bucket.copy());
        PipePackets.sendLegEnd(level, null, f);
    }

    // ================================================================== editing (from the screen)

    void rename(int endpoint, String name) {
        Endpoint e = byId(endpoint);
        if (e != null) e.name = name.length() > 24 ? name.substring(0, 24) : name;
    }

    void addCommand() {
        if (this.commands.size() >= Config.PIPE_MAX_COMMANDS.get()) return;
        Command c = new Command();
        List<Endpoint> live = new ArrayList<>();
        for (Endpoint e : this.endpoints) if (!e.missing) live.add(e);
        c.from = live.isEmpty() ? -1 : live.get(0).id;
        c.to = live.size() < 2 ? c.from : live.get(1).id;
        this.commands.add(c);
    }

    /** Next (or previous) endpoint after {@code id}, for the From/To buttons. */
    int cycle(int id, int step) {
        if (this.endpoints.isEmpty()) return -1;
        int i = 0;
        for (int k = 0; k < this.endpoints.size(); k++) if (this.endpoints.get(k).id == id) i = k;
        i = Math.floorMod(i + step, this.endpoints.size());
        return this.endpoints.get(i).id;
    }

    void dropUnusedMissing() {
        this.endpoints.removeIf(e -> e.missing && !usedByCommand(e.id));
    }

    // ================================================================== screen snapshot

    /** Everything the network screen shows, written for {@link PipePackets.Snapshot}. */
    void writeSnapshot(ServerLevel level, FriendlyByteBuf buf) {
        buf.writeVarInt(capacity());
        buf.writeVarInt(this.pipes.size());
        buf.writeVarInt(this.endpoints.size());
        for (Endpoint e : this.endpoints) {
            buf.writeVarInt(e.id);
            buf.writeUtf(e.letter);
            buf.writeUtf(e.name);
            buf.writeUtf(e.block == null ? "minecraft:air" : e.block.toString());
            buf.writeLong(e.key);
            buf.writeByte(e.face);
            buf.writeBoolean(e.missing);
            IItemHandler h = e.missing ? null : handler(level, e);
            int used = 0, total = 0;
            if (h != null) {
                total = h.getSlots();
                for (int i = 0; i < Math.min(total, MAX_SLOTS_SCANNED); i++) if (!h.getStackInSlot(i).isEmpty()) used++;
            }
            buf.writeBoolean(h != null);
            buf.writeVarInt(used);
            buf.writeVarInt(total);
        }
        buf.writeVarInt(this.commands.size());
        for (Command c : this.commands) {
            buf.writeItem(c.filter);
            buf.writeVarInt(c.from);
            buf.writeVarInt(c.to);
            buf.writeVarInt(c.keep);
            buf.writeBoolean(c.on);
        }
        for (int i = 0; i < MAX_FISH; i++) {
            HaulFish f = this.fish[i];
            buf.writeByte(f == null ? -1 : f.state);
            if (f == null) continue;
            buf.writeItem(f.cargo);
            buf.writeVarInt(f.amount);
            buf.writeVarInt(f.state == TO_SOURCE ? f.src : f.dst);
            buf.writeVarInt(f.moving() ? f.command : -1);
        }
    }

    // ================================================================== saving

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", this.id);
        t.put("Pipes", new LongArrayTag(this.pipes.toLongArray()));
        t.putInt("NextEndpoint", this.nextEndpointId);
        ListTag eps = new ListTag();
        for (Endpoint e : this.endpoints) {
            CompoundTag x = new CompoundTag();
            x.putInt("Id", e.id);
            x.putString("Letter", e.letter);
            x.putString("Name", e.name);
            x.putLong("Key", e.key);
            x.putInt("Face", e.face);
            if (e.block != null) x.putString("Block", e.block.toString());
            x.putBoolean("Missing", e.missing);
            long[] acc = new long[e.access.size() * 2];
            for (int i = 0; i < e.access.size(); i++) {
                acc[i * 2] = e.access.get(i)[0];
                acc[i * 2 + 1] = e.access.get(i)[1];
            }
            x.put("Access", new LongArrayTag(acc));
            eps.add(x);
        }
        t.put("Endpoints", eps);
        ListTag cmds = new ListTag();
        for (Command c : this.commands) {
            CompoundTag x = new CompoundTag();
            if (!c.filter.isEmpty()) x.put("Filter", c.filter.save(new CompoundTag()));
            x.putInt("From", c.from);
            x.putInt("To", c.to);
            x.putInt("Keep", c.keep);
            x.putBoolean("On", c.on);
            cmds.add(x);
        }
        t.put("Commands", cmds);
        ListTag fl = new ListTag();
        for (int i = 0; i < MAX_FISH; i++) {
            HaulFish f = this.fish[i];
            if (f == null) continue;
            CompoundTag x = new CompoundTag();
            x.putInt("Slot", i);
            x.put("Bucket", f.bucket.save(new CompoundTag()));
            x.putLong("Uid", f.uid);
            x.putByte("State", f.state);
            x.putLong("From", f.from);
            x.putLong("At", f.at);
            x.putLong("LegStart", f.legStart);
            x.putLong("LegEnd", f.legEnd);
            if (!f.cargo.isEmpty()) x.put("Cargo", f.cargo.save(new CompoundTag()));
            x.putInt("Amount", f.amount);
            x.putInt("Src", f.src);
            x.putInt("Dst", f.dst);
            x.putInt("Ret", f.ret);
            fl.add(x);
        }
        t.put("Fish", fl);
        return t;
    }

    static PipeNet load(CompoundTag t) {
        PipeNet n = new PipeNet(t.getInt("Id"));
        n.pipes = new LongOpenHashSet(t.getLongArray("Pipes"));
        n.nextEndpointId = Math.max(1, t.getInt("NextEndpoint"));
        for (Tag tag : t.getList("Endpoints", Tag.TAG_COMPOUND)) {
            CompoundTag x = (CompoundTag) tag;
            Endpoint e = new Endpoint(x.getInt("Id"), x.getString("Letter"));
            e.name = x.getString("Name");
            e.key = x.getLong("Key");
            e.face = x.getInt("Face");
            e.block = x.contains("Block") ? ResourceLocation.tryParse(x.getString("Block")) : null;
            e.missing = x.getBoolean("Missing");
            long[] acc = x.getLongArray("Access");
            for (int i = 0; i + 1 < acc.length; i += 2) e.access.add(new long[]{acc[i], acc[i + 1]});
            n.endpoints.add(e);
        }
        for (Tag tag : t.getList("Commands", Tag.TAG_COMPOUND)) {
            CompoundTag x = (CompoundTag) tag;
            Command c = new Command();
            c.filter = x.contains("Filter") ? ItemStack.of(x.getCompound("Filter")) : ItemStack.EMPTY;
            c.from = x.getInt("From");
            c.to = x.getInt("To");
            c.keep = x.getInt("Keep");
            c.on = x.getBoolean("On");
            n.commands.add(c);
        }
        for (Tag tag : t.getList("Fish", Tag.TAG_COMPOUND)) {
            CompoundTag x = (CompoundTag) tag;
            int slot = x.getInt("Slot");
            if (slot < 0 || slot >= MAX_FISH) continue;
            HaulFish f = new HaulFish();
            f.bucket = ItemStack.of(x.getCompound("Bucket"));
            if (f.bucket.isEmpty()) continue;
            f.uid = x.getLong("Uid");
            f.state = x.getByte("State");
            f.from = x.getLong("From");
            f.at = x.getLong("At");
            f.legStart = x.getLong("LegStart");
            f.legEnd = x.getLong("LegEnd");
            f.cargo = x.contains("Cargo") ? ItemStack.of(x.getCompound("Cargo")) : ItemStack.EMPTY;
            f.amount = x.getInt("Amount");
            f.src = x.getInt("Src");
            f.dst = x.getInt("Dst");
            f.ret = x.getInt("Ret");
            n.fish[slot] = f;
        }
        return n;
    }
}

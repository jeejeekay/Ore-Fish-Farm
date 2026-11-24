package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.block.TankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Draws an aquarium's fish, once per aquarium (from its controller block), as the mob from each bucket (or the
 * bucket's own sprite if it has no mob), with the mob's own swim animation.
 * <p>Every fish swims a slow, smooth loop of its own, in a lone tank block or through a joined aquarium. A fish's
 * centre never leaves the tank blocks:
 * <ul>
 *   <li>its loop only steps between blocks that share a face, passing through a point on that face;</li>
 *   <li>its waypoints keep clear of the glass, and each frame its position is clamped to stay a margin inside any
 *       glass side: {@link #MARGIN_ALONE} in a lone tank block, {@link #MARGIN_ROAM} in a joined aquarium, where fish
 *       turn and travel and a nose or tail pointed at the glass must not poke through (the roaming margin is about
 *       three quarters of the drawn fish's size, so even long-bodied mobs stay inside). That full margin only applies
 *       toward the glass a fish faces: toward glass it swims alongside it only keeps {@link #PERP_ALONE} /
 *       {@link #PERP_ROAM} (its half-width), eased as it turns. Some stops are runs along a glass wall, so fish
 *       (bottom feeders too, between the corner rocks) cruise close alongside it; fast swimmers' laps hug the glass.</li>
 * </ul>
 * Swim styles, by item tag on the fish's species (any mod or pack can add to them):
 * <ul>
 *   <li>normal: the wandering loop above;</li>
 *   <li>{@code #orefishfarm:bottom_feeders}: only the aquarium's bottom layer, low and slow, along the clear lanes
 *       through the middle of each block (the floor decor keeps its rocks in the corners), stopping now and then to
 *       graze, and kept level;</li>
 *   <li>{@code #orefishfarm:fast_swimmers}: quick laps round the outside of the aquarium, spiralling up and down.</li>
 * </ul>
 * Paths and each block's glass sides are worked out once per aquarium shape and cached, so a frame only
 * evaluates a spline and one lookup per fish. Every fish has its own timing. At most 24 fish are drawn per
 * aquarium, and none past 32 blocks.
 */
public class AquariumRenderer implements BlockEntityRenderer<TankBlockEntity> {
    private static final float SIZE_ALONE = 0.5F, SIZE_PAIR = 0.38F, SIZE_ROAM = 0.45F;
    /** How far a fish's centre stays from the glass, in blocks: in a lone tank block, and in a joined aquarium. */
    private static final double MARGIN_ALONE = 0.15, MARGIN_ROAM = SIZE_ROAM * 0.75;
    /** How far waypoints stay from the glass (a little more than the margin, so the clamp rarely bites). */
    private static final double INSET_ALONE = 0.22, INSET_ROAM = MARGIN_ROAM + 0.04;
    /** How close a fish's centre may come to glass it swims alongside (only its nose and tail need the full margin). */
    private static final double PERP_ALONE = 0.1, PERP_ROAM = 0.12;
    private static final int MAX_FISH = 24;
    private static final double MAX_DIST = 32;
    private static final Map<BlockPos, Paths> CACHE = new HashMap<>();
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> BOTTOM_FEEDERS =
            net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("orefishfarm", "bottom_feeders"));
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> FAST_SWIMMERS =
            net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("orefishfarm", "fast_swimmers"));
    static final int NORMAL = 0, BOTTOM = 1, FAST = 2;

    /** The swim style for the fish in this bucket, from its species' tags. */
    private static int style(ItemStack bucket) {
        IGoByJake.orefishfarm.data.FishData.Fish d = IGoByJake.orefishfarm.data.FishData.fish(bucket);
        ItemStack species = d == null ? bucket : new ItemStack(d.item());
        if (species.is(BOTTOM_FEEDERS)) return BOTTOM;
        if (species.is(FAST_SWIMMERS)) return FAST;
        return NORMAL;
    }

    public AquariumRenderer(BlockEntityRendererProvider.Context ctx) {}

    /** A well-spread offset per fish, so neighbours never move in step. */
    private static float phase(BlockPos p, int slot) {
        int h = (p.hashCode() * 31 + slot) * 0x9E3779B9;
        return ((h >>> 8) & 0xFFFF) / 65536F * Mth.TWO_PI * 4;
    }

    private static void draw(ItemStack bucket, @org.jetbrains.annotations.Nullable Entity mob, float size, Level level, float phase, float partial, PoseStack pose, MultiBufferSource buffers, int lit) {
        if (mob != null) {
            FishLook.render(mob, size, level.getGameTime() + (long) (phase * 40), partial, pose, buffers, lit);
        } else {
            pose.scale(size, size, size);
            Minecraft.getInstance().getItemRenderer().renderStatic(bucket, ItemDisplayContext.FIXED, lit, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        }
    }

    @Override
    public void render(TankBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.isController()) return;
        List<BlockPos> members = be.members();
        if (members.isEmpty()) members = List.of(be.getBlockPos());
        float time = (level.getGameTime() + partial) / 20F;
        BlockPos origin = be.getBlockPos();
        Paths paths = CACHE.get(origin);
        if (paths == null || paths.hash != members.hashCode()) {
            if (CACHE.size() > 512) CACHE.clear();
            paths = new Paths(members);
            CACHE.put(origin.immutable(), paths);
        }
        boolean lone = members.size() == 1;
        int drawn = 0;
        for (int m = 0; m < members.size() && drawn < MAX_FISH; m++) {
            if (!(level.getBlockEntity(members.get(m)) instanceof TankBlockEntity t)) continue;
            boolean pair = lone && !t.fishStack(0).isEmpty() && !t.fishStack(1).isEmpty();
            for (int s = 0; s < TankBlockEntity.FISH && drawn < MAX_FISH; s++) {
                ItemStack bucket = t.fishStack(s);
                if (bucket.isEmpty()) continue;
                int style = style(bucket);
                float base = lone ? (pair ? SIZE_PAIR : SIZE_ALONE) : SIZE_ROAM;
                Entity mob = FishLook.mob(bucket);
                float size = mob == null ? base : FishLook.tankSize(mob, base, lone);
                Path path = paths.get(m * TankBlockEntity.FISH + s, m, style);
                Vec3 dir = path.heading(time, paths);
                // Glass margins by heading: the full margin (plus the extra half-length of big fish) toward the walls
                // the fish faces, only its half-width toward the walls it swims alongside; eased as it turns.
                double extra = Math.max(0, size - base) * 0.5;
                double full = Math.min(0.45, paths.margin + extra), perp = Math.min(full, paths.perp + extra * 0.25);
                double hl = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                double hx = hl < 1.0E-6 ? 1 : Math.abs(dir.x) / hl, hz = hl < 1.0E-6 ? 1 : Math.abs(dir.z) / hl;
                path.ease(time, perp + (full - perp) * hx, perp + (full - perp) * hz);
                Vec3 p = paths.clamp(path.at(time), path.blockAt(time), path.marginX, path.marginZ);
                // Over a floor block, keep the whole fish above the floor (3/16 up): a big or deep-bodied fish
                // would otherwise sink into it. The mob is drawn centred on p, bbHeight tall at this size.
                BlockPos at = BlockPos.containing(p);
                if (mob != null && paths.isFloor(at)) {
                    float sc = size / Math.max(0.2F, Math.max(mob.getBbWidth(), mob.getBbHeight()));
                    double minY = at.getY() + 3 / 16.0 + 0.01 + mob.getBbHeight() * sc / 2;
                    if (p.y < minY) p = new Vec3(p.x, minY, p.z);
                }
                double dx = dir.x, dy = dir.y, dz = dir.z;
                float yaw = (float) Math.atan2(-dz, dx);
                float pitch = style == BOTTOM ? 0F : (float) Mth.clamp(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)), -0.4, 0.4);
                int lit = LevelRenderer.getLightColor(level, BlockPos.containing(p));
                float phase = phase(members.get(m), s);
                pose.pushPose();
                pose.translate(p.x - origin.getX(), p.y - origin.getY(), p.z - origin.getZ());
                pose.mulPose(Axis.YP.rotation(yaw));
                pose.mulPose(Axis.ZP.rotation(pitch));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 1.1F + phase) * 4F));
                draw(bucket, mob, size, level, phase, partial, pose, buffers, lit);
                pose.popPose();
                drawn++;
            }
        }
    }

    @Override
    public boolean shouldRender(TankBlockEntity be, Vec3 camera) {
        if (!be.isController()) return false;
        AABB box = be.bounds();
        double x = Mth.clamp(camera.x, box.minX, box.maxX), y = Mth.clamp(camera.y, box.minY, box.maxY), z = Mth.clamp(camera.z, box.minZ, box.maxZ);
        return camera.distanceToSqr(x, y, z) < MAX_DIST * MAX_DIST;
    }

    @Override
    public boolean shouldRenderOffScreen(TankBlockEntity be) {
        return true;
    }

    /** Swim paths for one aquarium shape, and which sides of each tank block are glass. */
    private static final class Paths {
        private static final Direction[] DIRS = Direction.values();
        final int hash;
        final List<BlockPos> members;
        /** Per tank block: bit d set when the side facing {@code DIRS[d]} is glass (no tank block beyond it). */
        final Map<BlockPos, Integer> glass = new HashMap<>();
        final Map<Integer, Path> byFish = new HashMap<>();
        /** This aquarium's glass margin and waypoint inset (see the class notes), and the side-on margin. */
        final double margin, inset, perp;

        Paths(List<BlockPos> members) {
            this.hash = members.hashCode();
            boolean lone = members.size() <= 1;
            this.margin = lone ? MARGIN_ALONE : MARGIN_ROAM;
            this.inset = lone ? INSET_ALONE : INSET_ROAM;
            this.perp = lone ? PERP_ALONE : PERP_ROAM;
            this.members = new ArrayList<>(members);
            Set<BlockPos> set = new HashSet<>(members);
            for (BlockPos b : members) {
                int mask = 0;
                for (Direction d : DIRS) if (!set.contains(b.relative(d))) mask |= 1 << d.ordinal();
                this.glass.put(b.immutable(), mask);
            }
        }

        Path get(int fish, int home, int style) {
            return this.byFish.computeIfAbsent(fish * 4 + style, k -> new Path(this, fish, home, style));
        }

        /** Tank blocks with glass underneath: the aquarium's bottom layer. */
        boolean isFloor(BlockPos b) {
            Integer m = this.glass.get(b);
            return m != null && (m & (1 << Direction.DOWN.ordinal())) != 0;
        }

        boolean isGlass(BlockPos b, Direction d) {
            return (this.glass.get(b) & (1 << d.ordinal())) != 0;
        }

        /**
         * Keeps a point inside the tanks: if it has strayed out of every tank block it is pulled back into
         * {@code fallback} (the block the fish is passing through), then held {@link #margin} off any glass side.
         */
        Vec3 clamp(Vec3 p, BlockPos fallback) {
            return clamp(p, fallback, this.margin, this.margin);
        }

        /** As {@link #clamp(Vec3, BlockPos)}, with its own margins for the west/east and north/south glass. */
        Vec3 clamp(Vec3 p, BlockPos fallback, double marginX, double marginZ) {
            BlockPos b = BlockPos.containing(p);
            Integer mask = this.glass.get(b);
            if (mask == null) {
                b = fallback;
                mask = this.glass.get(b);
            }
            double x = axis(p.x, b.getX(), mask, Direction.WEST, Direction.EAST, marginX);
            double y = axis(p.y, b.getY(), mask, Direction.DOWN, Direction.UP, Math.min(this.margin, BOTTOM_Y - 0.02));
            double z = axis(p.z, b.getZ(), mask, Direction.NORTH, Direction.SOUTH, marginZ);
            return x == p.x && y == p.y && z == p.z ? p : new Vec3(x, y, z);
        }

        private static double axis(double v, int base, int mask, Direction low, Direction high, double margin) {
            double lo = base + ((mask & (1 << low.ordinal())) != 0 ? margin : 0);
            double hi = base + 1 - ((mask & (1 << high.ordinal())) != 0 ? margin : 1.0E-4);
            return Mth.clamp(v, lo, hi);
        }
    }

    /**
     * A closed loop of waypoints inside the aquarium, followed with a Catmull-Rom spline at a slow, even pace.
     * The loop wanders between tank blocks that share a face, and crossing from one block to the next always
     * goes through a point on the shared face.
     */
    /** Bottom feeders' height above the block's bottom (the floor surface is at 3/16). */
    static final double BOTTOM_Y = 0.30;

    private static final class Path {
        private static final int STOPS = 10;
        private static final double SPEED = 0.35, BOTTOM_SPEED = 0.2, FAST_SPEED = 1.1; // blocks per second
        final Vec3[] pts;
        /** Bottom feeders weave side to side (and bob a little) as they swim, so a row of them doesn't stack up. */
        boolean weave;
        float wavePhase, waveRate, waveSize;
        final BlockPos[] blocks;
        final float[] start;
        /** Seconds the fish rests at each waypoint before moving on (bottom feeders grazing). */
        final float[] dwell;
        /** The fish's current glass margins (west/east, north/south), eased toward the ones its heading wants. */
        double marginX = -1, marginZ = -1;
        private float easedAt;

        void ease(float time, double wantX, double wantZ) {
            float dt = time - this.easedAt;
            this.easedAt = time;
            if (this.marginX < 0 || dt < 0 || dt > 1) {
                this.marginX = wantX;
                this.marginZ = wantZ;
                return;
            }
            double k = 1 - Math.exp(-dt / 0.35);
            this.marginX += (wantX - this.marginX) * k;
            this.marginZ += (wantZ - this.marginZ) * k;
        }

        Path(Paths p, int seed, int home, int style) {
            Random r = new Random(seed * 341873128712L + p.hash + style * 7919L);
            List<Vec3> pts = new ArrayList<>();
            List<BlockPos> blocks = new ArrayList<>();
            double speed = SPEED;
            if (style == FAST) {
                lapPoints(p, r, pts, blocks);
                speed = FAST_SPEED;
            } else {
                boolean bottom = style == BOTTOM;
                // each bottom feeder keeps its own lane, a little to one side of the block's middle line, so a row
                // of them in a 1-wide tank spreads out instead of piling up on the centre line
                double laneOff = bottom ? (r.nextDouble() * 2 - 1) * 0.13 : 0;
                BlockPos first = p.members.get(home);
                if (bottom && !p.isFloor(first)) { // start on the bottom layer, under the home block if possible
                    BlockPos f = first;
                    while (p.glass.containsKey(f.below()) && !p.isFloor(f)) f = f.below();
                    first = p.isFloor(f) ? f : p.members.stream().filter(p::isFloor).findFirst().orElse(first);
                }
                BlockPos cur = first;
                for (int i = 0; i <= STOPS; i++) {
                    // Next block: stay, or step through a face into a touching tank block; the last step heads home.
                    BlockPos next = cur;
                    if (i == STOPS) {
                        next = first;
                    } else if (i > 0) {
                        List<BlockPos> near = new ArrayList<>();
                        near.add(cur);
                        for (Direction d : Paths.DIRS) {
                            if (p.isGlass(cur, d)) continue;
                            BlockPos n = cur.relative(d);
                            if (!bottom || (d.getAxis() != Direction.Axis.Y && p.isFloor(n))) near.add(n);
                        }
                        next = near.get(r.nextInt(near.size()));
                    }
                    if (!next.equals(cur) && !walk(p, r, cur, next, pts, blocks, bottom, laneOff)) next = cur;
                    cur = next;
                    if (i == STOPS) break;
                    stop(p, r, cur, bottom, laneOff, pts, blocks);
                }
                if (bottom) speed = BOTTOM_SPEED;
            }
            int n = pts.size();
            this.pts = pts.toArray(new Vec3[0]);
            this.blocks = blocks.toArray(new BlockPos[0]);
            this.start = new float[n + 1];
            this.dwell = new float[n];
            if (style == BOTTOM) {
                this.weave = true;
                this.wavePhase = r.nextFloat() * 6.2832F;
                this.waveRate = 1.6F + r.nextFloat() * 1.4F;   // radians per second
                this.waveSize = 0.06F + r.nextFloat() * 0.05F; // blocks to each side
            }
            for (int i = 0; i < n; i++) {
                // bottom feeders rest now and then, but mostly keep swimming low along the floor
                if (style == BOTTOM && r.nextFloat() < 0.35F) this.dwell[i] = 1.0F + r.nextFloat() * 2.5F;
                double d = this.pts[i].distanceTo(this.pts[(i + 1) % n]);
                this.start[i + 1] = this.start[i] + this.dwell[i] + (float) Math.max(style == FAST ? 0.05 : 1.0, d / speed);
            }
        }

        /**
         * Fast swimmers: laps round the outside of the aquarium's footprint (a rounded rectangle kept off the
         * glass), two laps rising from near the floor to near the top, two coming back down. Points outside the
         * aquarium's blocks (odd shapes) are pulled into the nearest one by the frame clamp.
         */
        private static void lapPoints(Paths p, Random r, List<Vec3> pts, List<BlockPos> blocks) {
            // The aquarium's footprint as columns, with each column's lowest and highest tank block.
            Map<Long, int[]> cols = new HashMap<>();
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (BlockPos b : p.members) {
                cols.merge(colKey(b.getX(), b.getZ()), new int[]{b.getY(), b.getY()}, (o, n) -> new int[]{Math.min(o[0], n[0]), Math.max(o[1], n[1])});
                x0 = Math.min(x0, b.getX()); x1 = Math.max(x1, b.getX());
                z0 = Math.min(z0, b.getZ()); z1 = Math.max(z1, b.getZ());
            }
            if (cols.size() == (x1 - x0 + 1) * (z1 - z0 + 1)) {
                rectLap(p, r, pts, blocks, x0, z0, x1, z1);
                return;
            }
            // Odd shapes: follow the outline block by block (keeping a hand on the outer wall), so the fish never
            // cuts across empty space, then now and then wander through the middle and come back.
            List<int[]> ring = outline(cols, r);
            if (ring.size() < 2) {
                rectLap(p, r, pts, blocks, x0, z0, x1, z1);
                return;
            }
            double in = p.perp + 0.1;
            int laps = 2, total = ring.size() * laps;
            int idx = 0;
            for (int lap = 0; lap < laps; lap++) {
                for (int[] c : ring) {
                    addColumnPoint(p, cols, c[0], c[1], in, (double) idx++ / total, pts, blocks);
                }
                if (r.nextFloat() < 0.6F) { // a detour through the middle, back to where it left the wall
                    int[] from = ring.get(ring.size() - 1);
                    List<int[]> detour = wander(cols, from, ring.get(0), r);
                    for (int[] c : detour) addColumnPoint(p, cols, c[0], c[1], 0.5, (double) idx / total, pts, blocks);
                }
            }
        }

        private static long colKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

        private static final int[][] STEPS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}; // east, south, west, north (clockwise)

        /** The outer ring of columns, walked keeping the open side on one hand (a few laps' worth at most). */
        private static List<int[]> outline(Map<Long, int[]> cols, Random r) {
            List<int[]> edge = new ArrayList<>();
            for (long k : cols.keySet()) {
                int x = (int) (k >> 32), z = (int) k;
                for (int[] d : STEPS) if (!cols.containsKey(colKey(x + d[0], z + d[1]))) { edge.add(new int[]{x, z}); break; }
            }
            if (edge.isEmpty()) return edge;
            edge.sort((u, v) -> u[0] != v[0] ? Integer.compare(u[0], v[0]) : Integer.compare(u[1], v[1]));
            int[] start = edge.get(0);
            boolean right = r.nextBoolean();
            int dir = 1; // start heading south along the west side
            List<int[]> out = new ArrayList<>();
            int x = start[0], z = start[1];
            for (int guard = 0; guard < cols.size() * 4 + 8; guard++) {
                out.add(new int[]{x, z});
                int[] order = right ? new int[]{(dir + 3) % 4, dir, (dir + 1) % 4, (dir + 2) % 4} : new int[]{(dir + 1) % 4, dir, (dir + 3) % 4, (dir + 2) % 4};
                boolean moved = false;
                for (int nd : order) {
                    int nx = x + STEPS[nd][0], nz = z + STEPS[nd][1];
                    if (!cols.containsKey(colKey(nx, nz)) || !isEdge(cols, nx, nz)) continue;
                    x = nx; z = nz; dir = nd; moved = true;
                    break;
                }
                if (!moved || (x == start[0] && z == start[1])) break;
            }
            return out;
        }

        private static boolean isEdge(Map<Long, int[]> cols, int x, int z) {
            for (int[] d : STEPS) if (!cols.containsKey(colKey(x + d[0], z + d[1]))) return true;
            return false;
        }

        /** A few random steps through the footprint from {@code from}, then the shortest way to {@code to}. */
        private static List<int[]> wander(Map<Long, int[]> cols, int[] from, int[] to, Random r) {
            List<int[]> out = new ArrayList<>();
            int x = from[0], z = from[1];
            for (int i = 0, n = 2 + r.nextInt(4); i < n; i++) {
                List<int[]> opts = new ArrayList<>();
                for (int[] d : STEPS) if (cols.containsKey(colKey(x + d[0], z + d[1]))) opts.add(new int[]{x + d[0], z + d[1]});
                if (opts.isEmpty()) break;
                int[] c = opts.get(r.nextInt(opts.size()));
                x = c[0]; z = c[1];
                out.add(c);
            }
            // breadth-first back to the ring
            Map<Long, Long> prev = new HashMap<>();
            java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
            q.add(new int[]{x, z});
            prev.put(colKey(x, z), Long.MIN_VALUE);
            long goal = colKey(to[0], to[1]);
            while (!q.isEmpty() && !prev.containsKey(goal)) {
                int[] c = q.poll();
                for (int[] d : STEPS) {
                    long k = colKey(c[0] + d[0], c[1] + d[1]);
                    if (!cols.containsKey(k) || prev.containsKey(k)) continue;
                    prev.put(k, colKey(c[0], c[1]));
                    q.add(new int[]{c[0] + d[0], c[1] + d[1]});
                }
            }
            List<int[]> back = new ArrayList<>();
            for (Long k = goal; k != null && k != Long.MIN_VALUE && prev.containsKey(k); k = prev.get(k)) back.add(0, new int[]{(int) (k >> 32), (int) (long) k});
            if (!back.isEmpty()) back.remove(back.size() - 1); // the ring's first point is next anyway
            if (!back.isEmpty() && back.get(0)[0] == x && back.get(0)[1] == z) back.remove(0);
            out.addAll(back);
            return out;
        }

        /** A waypoint in a column: pushed toward its glass sides (by {@code in} from the glass), at a lap height. */
        private static void addColumnPoint(Paths p, Map<Long, int[]> cols, int x, int z, double in, double frac, List<Vec3> pts, List<BlockPos> blocks) {
            double px = x + 0.5, pz = z + 0.5, push = Math.max(0, 0.5 - in);
            if (!cols.containsKey(colKey(x - 1, z))) px -= push;
            if (!cols.containsKey(colKey(x + 1, z))) px += push;
            if (!cols.containsKey(colKey(x, z - 1))) pz -= push;
            if (!cols.containsKey(colKey(x, z + 1))) pz += push;
            int[] yr = cols.get(colKey(x, z));
            double lo = yr[0] + 0.35, hi = Math.max(lo, yr[1] + 1 - 0.3);
            double y = lo + (hi - lo) * (0.5 - 0.5 * Math.cos(2 * Math.PI * frac));
            Vec3 v = new Vec3(px, y, pz);
            pts.add(v);
            blocks.add(nearest(p, v));
        }

        /** Rectangular footprints: a rounded-rectangle lap round the outside, rising and falling over four laps. */
        private static void rectLap(Paths p, Random r, List<Vec3> pts, List<BlockPos> blocks, int x0, int z0, int x1, int z1) {
            int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
            for (BlockPos b : p.members) { y0 = Math.min(y0, b.getY()); y1 = Math.max(y1, b.getY()); }
            double in = p.perp + 0.1; // laps run alongside the glass, so they can hug it
            double ax = x0 + in, bx = x1 + 1 - in, az = z0 + in, bz = z1 + 1 - in;
            double lo = y0 + 0.35, hi = Math.max(lo, y1 + 1 - 0.3);
            double rad = Math.min(0.3, Math.min(bx - ax, bz - az) / 2);
            // the loop: corners rounded with three points each, walked clockwise or anticlockwise
            List<double[]> ring = new ArrayList<>();
            double[][] corners = {{bx, bz, 0}, {ax, bz, 1}, {ax, az, 2}, {bx, az, 3}};
            for (double[] c : corners) {
                double cx = c[0] + (c[0] == bx ? -rad : rad), cz = c[1] + (c[1] == bz ? -rad : rad);
                double startA = c[2] * Math.PI / 2;
                for (int k = 0; k < 3; k++) {
                    double a = startA + k * Math.PI / 4;
                    ring.add(new double[]{cx + Math.cos(a) * rad, cz + Math.sin(a) * rad});
                }
            }
            if (r.nextBoolean()) java.util.Collections.reverse(ring);
            int shift = r.nextInt(ring.size());
            int laps = 4, n = ring.size() * laps;
            for (int i = 0; i < n; i++) {
                double[] q = ring.get((i + shift) % ring.size());
                double y = lo + (hi - lo) * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / n));
                Vec3 v = new Vec3(q[0], y, q[1]);
                pts.add(v);
                blocks.add(nearest(p, v));
            }
            // now and then a quick dash across the middle before the next laps
            if (r.nextFloat() < 0.6F) for (int k = 0, m = 2 + r.nextInt(2); k < m; k++) {
                Vec3 v = new Vec3(ax + r.nextDouble() * (bx - ax), lo + r.nextDouble() * (hi - lo), az + r.nextDouble() * (bz - az));
                pts.add(v);
                blocks.add(nearest(p, v));
            }
        }

        private static BlockPos nearest(Paths p, Vec3 v) {
            BlockPos at = BlockPos.containing(v);
            if (p.glass.containsKey(at)) return at;
            BlockPos best = p.members.get(0);
            double bd = Double.MAX_VALUE;
            for (BlockPos b : p.members) {
                double d = v.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
                if (d < bd) { bd = d; best = b; }
            }
            return best;
        }

        /**
         * The next stop in block {@code b}: usually one point (see {@link #lane} and {@link #inside}); sometimes a run
         * along one of the block's glass walls, two points close to it, so the fish cruises alongside the glass.
         * Bottom feeders' runs stay between the corner rocks.
         */
        private static void stop(Paths p, Random r, BlockPos b, boolean bottom, double laneOff, List<Vec3> pts, List<BlockPos> blocks) {
            List<Direction> walls = new ArrayList<>();
            for (Direction d : Direction.Plane.HORIZONTAL) if (p.isGlass(b, d)) walls.add(d);
            if (walls.isEmpty() || r.nextFloat() >= (bottom ? 0.45F : 0.35F)) {
                pts.add(bottom ? lane(p, r, b, laneOff) : inside(p, r, b));
                blocks.add(b);
                return;
            }
            Direction d = walls.get(r.nextInt(walls.size()));
            double off = p.perp + 0.04 + (bottom ? Math.abs(laneOff) * 0.6 : 0);
            double a0 = bottom ? 0.36 : 0.25, a1 = 1 - a0;
            double s0 = a0 + r.nextDouble() * (a1 - a0), s1 = a0 + r.nextDouble() * (a1 - a0);
            if (Math.abs(s1 - s0) < 0.2) s1 = s0 < 0.5 ? a1 : a0;
            double y = bottom ? b.getY() + BOTTOM_Y + r.nextDouble() * 0.03 : inside(p, r, b).y;
            for (double along : new double[]{s0, s1}) {
                double across = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - off : off;
                pts.add(d.getAxis() == Direction.Axis.X ? new Vec3(b.getX() + across, y, b.getZ() + along) : new Vec3(b.getX() + along, y, b.getZ() + across));
                blocks.add(b);
            }
        }

        /** A bottom feeder's stop: low over the floor, on the clear lanes through the middle of the block. */
        private static Vec3 lane(Paths p, Random r, BlockPos b, double laneOff) {
            double j = 0.1;
            boolean alongX = r.nextBoolean();
            double x = b.getX() + 0.5 + (alongX ? (r.nextDouble() * 2 - 1) * 0.2 : laneOff + (r.nextDouble() * 2 - 1) * j * 0.3);
            double z = b.getZ() + 0.5 + (alongX ? laneOff + (r.nextDouble() * 2 - 1) * j * 0.3 : (r.nextDouble() * 2 - 1) * 0.2);
            return new Vec3(x, b.getY() + BOTTOM_Y + r.nextDouble() * 0.03, z);
        }

        /**
         * Face points from {@code a} to {@code b} along a shortest route through touching tank blocks (for bottom
         * feeders, only sideways through the bottom layer). False if there is no such route.
         */
        private static boolean walk(Paths p, Random r, BlockPos a, BlockPos b, List<Vec3> pts, List<BlockPos> blocks, boolean bottom, double laneOff) {
            Map<BlockPos, BlockPos> prev = new HashMap<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
            prev.put(a, a);
            queue.add(a);
            while (!queue.isEmpty() && !prev.containsKey(b)) {
                BlockPos c = queue.poll();
                for (Direction d : Paths.DIRS) {
                    if (p.isGlass(c, d)) continue;
                    BlockPos n = c.relative(d);
                    if (bottom && (d.getAxis() == Direction.Axis.Y || !p.isFloor(n))) continue;
                    if (prev.putIfAbsent(n, c) == null) queue.add(n);
                }
            }
            if (!prev.containsKey(b)) return false;
            List<BlockPos> route = new ArrayList<>();
            for (BlockPos c = b; !c.equals(a); c = prev.get(c)) route.add(0, c);
            BlockPos from = a;
            for (BlockPos to : route) {
                if (bottom) { // the shared face, low down, on this fish's own lane: the lanes line up from block to block
                    double side = laneOff + (r.nextDouble() * 2 - 1) * 0.02;
                    boolean alongX = from.getX() != to.getX();
                    pts.add(new Vec3((from.getX() + to.getX()) / 2.0 + 0.5 + (alongX ? 0 : side), from.getY() + BOTTOM_Y,
                            (from.getZ() + to.getZ()) / 2.0 + 0.5 + (alongX ? side : 0)));
                } else {
                    pts.add(face(r, from, to, p.inset));
                }
                blocks.add(to); // the stretch after a face point runs through the block it enters
                from = to;
            }
            return true;
        }

        /** A point on the face shared by two touching blocks, away from its edges. */
        private static Vec3 face(Random r, BlockPos a, BlockPos b, double inset) {
            double x = a.getX() + 0.5 + (b.getX() - a.getX()) * 0.5;
            double y = a.getY() + 0.5 + (b.getY() - a.getY()) * 0.5;
            double z = a.getZ() + 0.5 + (b.getZ() - a.getZ()) * 0.5;
            double j = Math.max(0, 0.5 - inset);
            if (a.getX() == b.getX()) x += (r.nextDouble() * 2 - 1) * j;
            if (a.getY() == b.getY()) y += (r.nextDouble() * 2 - 1) * j * 0.5;
            if (a.getZ() == b.getZ()) z += (r.nextDouble() * 2 - 1) * j;
            return new Vec3(x, y, z);
        }

        /** A random point in a tank block, clear of its glass sides; a little above the floor and below the top. */
        private static Vec3 inside(Paths p, Random r, BlockPos b) {
            boolean floor = p.isGlass(b, Direction.DOWN), ceiling = p.isGlass(b, Direction.UP);
            double in = p.inset;
            double y0 = floor ? Math.max(0.3, p.margin) : 0.05, y1 = ceiling ? Math.min(0.72, 1 - p.margin) : 0.95;
            return new Vec3(b.getX() + in + r.nextDouble() * (1 - 2 * in), b.getY() + y0 + r.nextDouble() * (y1 - y0), b.getZ() + in + r.nextDouble() * (1 - 2 * in));
        }

        private int index(float t) {
            float total = this.start[this.pts.length];
            float u = t % total;
            if (u < 0) u += total;
            int i = 0;
            while (i < this.pts.length - 1 && u >= this.start[i + 1]) i++;
            return i;
        }

        /** The block the fish is passing through at time {@code t}: the one its current stretch starts in. */
        BlockPos blockAt(float t) {
            return this.blocks[index(t)];
        }

        /** Which way the fish faces at time {@code t}: along its path, or toward its next stop while resting. */
        /**
         * Which way the fish faces: the way it's going, averaged over about 0.8 s around now (weighted toward now),
         * so corners and the turn after a rest become a smooth swing instead of a snap.
         */
        Vec3 heading(float t, Paths paths) {
            double x = 0, y = 0, z = 0;
            for (int k = -3; k <= 3; k++) {
                Vec3 d = rawHeading(t + k * 0.13F, paths);
                double len = d.length();
                if (len < 1.0E-9) continue;
                double w = 4 - Math.abs(k);
                x += d.x / len * w; y += d.y / len * w; z += d.z / len * w;
            }
            Vec3 sum = new Vec3(x, y, z);
            return sum.lengthSqr() < 1.0E-6 ? rawHeading(t, paths) : sum;
        }

        private Vec3 rawHeading(float t, Paths paths) {
            int n = this.pts.length;
            float total = this.start[n];
            float u = t % total;
            if (u < 0) u += total;
            int i = index(t);
            if (u - this.start[i] < this.dwell[i]) {
                Vec3 d = this.pts[(i + 1) % n].subtract(this.pts[i]);
                return d.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : d;
            }
            Vec3 a = paths.clamp(at(t), blockAt(t)), b = paths.clamp(at(t + 0.15F), blockAt(t + 0.15F));
            Vec3 d = b.subtract(a);
            return d.lengthSqr() < 1.0E-8 ? this.pts[(i + 1) % n].subtract(this.pts[i]) : d;
        }

        Vec3 at(float t) {
            int n = this.pts.length;
            float total = this.start[n];
            float u = t % total;
            if (u < 0) u += total;
            int i = index(t);
            float moving = this.start[i + 1] - this.start[i] - this.dwell[i];
            float f = Math.max(0F, u - this.start[i] - this.dwell[i]) / moving;
            Vec3 p0 = this.pts[(i - 1 + n) % n], p1 = this.pts[i], p2 = this.pts[(i + 1) % n], p3 = this.pts[(i + 2) % n];
            Vec3 base = new Vec3(cr(p0.x, p1.x, p2.x, p3.x, f), cr(p0.y, p1.y, p2.y, p3.y, f), cr(p0.z, p1.z, p2.z, p3.z, f));
            if (!this.weave) return base;
            // a sine wave across the way it's going, fading out at each stop so resting fish sit still
            double dx = p2.x - p1.x, dz = p2.z - p1.z, len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4) return base;
            double fade = Math.sin(Math.PI * f), w = Math.sin(t * this.waveRate + this.wavePhase) * this.waveSize * fade;
            double bob = Math.sin(t * this.waveRate * 0.5 + this.wavePhase * 1.7) * 0.025 * fade;
            return base.add(-dz / len * w, Math.max(0, bob), dx / len * w);
        }

        private static double cr(double a, double b, double c, double d, float t) {
            double t2 = t * t, t3 = t2 * t;
            return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
        }
    }
}

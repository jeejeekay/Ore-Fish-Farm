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
 *   <li>its waypoints keep clear of the glass, and each frame its position is clamped to stay at least
 *       {@link #MARGIN} inside any glass side (a nose or tail may still poke out a little).</li>
 * </ul>
 * Paths and each block's glass sides are worked out once per aquarium shape and cached, so a frame only
 * evaluates a spline and one lookup per fish. Every fish has its own timing. At most 24 fish are drawn per
 * aquarium, and none past 32 blocks.
 */
public class AquariumRenderer implements BlockEntityRenderer<TankBlockEntity> {
    private static final float SIZE_ALONE = 0.5F, SIZE_PAIR = 0.38F, SIZE_ROAM = 0.45F;
    /** How far a fish's centre stays from the glass, in blocks. */
    private static final double MARGIN = 0.15;
    private static final int MAX_FISH = 24;
    private static final double MAX_DIST = 32;
    private static final Map<BlockPos, Paths> CACHE = new HashMap<>();

    public AquariumRenderer(BlockEntityRendererProvider.Context ctx) {}

    /** A well-spread offset per fish, so neighbours never move in step. */
    private static float phase(BlockPos p, int slot) {
        int h = (p.hashCode() * 31 + slot) * 0x9E3779B9;
        return ((h >>> 8) & 0xFFFF) / 65536F * Mth.TWO_PI * 4;
    }

    private static void draw(ItemStack bucket, float size, Level level, float phase, float partial, PoseStack pose, MultiBufferSource buffers, int lit) {
        Entity mob = FishLook.mob(bucket);
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
                Path path = paths.get(m * TankBlockEntity.FISH + s, m);
                Vec3 p = paths.clamp(path.at(time), path.blockAt(time));
                Vec3 ahead = paths.clamp(path.at(time + 0.15F), path.blockAt(time + 0.15F));
                double dx = ahead.x - p.x, dy = ahead.y - p.y, dz = ahead.z - p.z;
                float yaw = (float) Math.atan2(-dz, dx);
                float pitch = (float) Mth.clamp(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)), -0.4, 0.4);
                int lit = LevelRenderer.getLightColor(level, BlockPos.containing(p));
                float phase = phase(members.get(m), s);
                pose.pushPose();
                pose.translate(p.x - origin.getX(), p.y - origin.getY(), p.z - origin.getZ());
                pose.mulPose(Axis.YP.rotation(yaw));
                pose.mulPose(Axis.ZP.rotation(pitch));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 1.1F + phase) * 4F));
                draw(bucket, lone ? (pair ? SIZE_PAIR : SIZE_ALONE) : SIZE_ROAM, level, phase, partial, pose, buffers, lit);
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

        Paths(List<BlockPos> members) {
            this.hash = members.hashCode();
            this.members = new ArrayList<>(members);
            Set<BlockPos> set = new HashSet<>(members);
            for (BlockPos b : members) {
                int mask = 0;
                for (Direction d : DIRS) if (!set.contains(b.relative(d))) mask |= 1 << d.ordinal();
                this.glass.put(b.immutable(), mask);
            }
        }

        Path get(int fish, int home) {
            return this.byFish.computeIfAbsent(fish, k -> new Path(this, k, home));
        }

        boolean isGlass(BlockPos b, Direction d) {
            return (this.glass.get(b) & (1 << d.ordinal())) != 0;
        }

        /**
         * Keeps a point inside the tanks: if it has strayed out of every tank block it is pulled back into
         * {@code fallback} (the block the fish is passing through), then held {@link #MARGIN} off any glass side.
         */
        Vec3 clamp(Vec3 p, BlockPos fallback) {
            BlockPos b = BlockPos.containing(p);
            Integer mask = this.glass.get(b);
            if (mask == null) {
                b = fallback;
                mask = this.glass.get(b);
            }
            double x = axis(p.x, b.getX(), mask, Direction.WEST, Direction.EAST);
            double y = axis(p.y, b.getY(), mask, Direction.DOWN, Direction.UP);
            double z = axis(p.z, b.getZ(), mask, Direction.NORTH, Direction.SOUTH);
            return x == p.x && y == p.y && z == p.z ? p : new Vec3(x, y, z);
        }

        private static double axis(double v, int base, int mask, Direction low, Direction high) {
            double lo = base + ((mask & (1 << low.ordinal())) != 0 ? MARGIN : 0);
            double hi = base + 1 - ((mask & (1 << high.ordinal())) != 0 ? MARGIN : 1.0E-4);
            return Mth.clamp(v, lo, hi);
        }
    }

    /**
     * A closed loop of waypoints inside the aquarium, followed with a Catmull-Rom spline at a slow, even pace.
     * The loop wanders between tank blocks that share a face, and crossing from one block to the next always
     * goes through a point on the shared face.
     */
    private static final class Path {
        private static final int STOPS = 10;
        private static final double SPEED = 0.35; // blocks per second
        private static final double INSET = 0.22; // waypoints keep this far from the glass
        final Vec3[] pts;
        final BlockPos[] blocks;
        final float[] start;

        Path(Paths p, int seed, int home) {
            Random r = new Random(seed * 341873128712L + p.hash);
            List<Vec3> pts = new ArrayList<>();
            List<BlockPos> blocks = new ArrayList<>();
            BlockPos first = p.members.get(home), cur = first;
            for (int i = 0; i <= STOPS; i++) {
                // Next block: stay, or step through a face into a touching tank block; the last step heads home.
                BlockPos next = cur;
                if (i == STOPS) {
                    next = first;
                } else if (i > 0) {
                    List<BlockPos> near = new ArrayList<>();
                    near.add(cur);
                    for (Direction d : Paths.DIRS) if (!p.isGlass(cur, d)) near.add(cur.relative(d));
                    next = near.get(r.nextInt(near.size()));
                }
                if (!next.equals(cur)) walk(p, r, cur, next, pts, blocks);
                cur = next;
                if (i == STOPS) break;
                pts.add(inside(p, r, cur));
                blocks.add(cur);
            }
            int n = pts.size();
            this.pts = pts.toArray(new Vec3[0]);
            this.blocks = blocks.toArray(new BlockPos[0]);
            this.start = new float[n + 1];
            for (int i = 0; i < n; i++) {
                double d = this.pts[i].distanceTo(this.pts[(i + 1) % n]);
                this.start[i + 1] = this.start[i] + (float) Math.max(1.0, d / SPEED);
            }
        }

        /** Face points from {@code a} to {@code b} along a shortest route through touching tank blocks. */
        private static void walk(Paths p, Random r, BlockPos a, BlockPos b, List<Vec3> pts, List<BlockPos> blocks) {
            Map<BlockPos, BlockPos> prev = new HashMap<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
            prev.put(a, a);
            queue.add(a);
            while (!queue.isEmpty() && !prev.containsKey(b)) {
                BlockPos c = queue.poll();
                for (Direction d : Paths.DIRS) {
                    if (p.isGlass(c, d)) continue;
                    BlockPos n = c.relative(d);
                    if (prev.putIfAbsent(n, c) == null) queue.add(n);
                }
            }
            List<BlockPos> route = new ArrayList<>();
            for (BlockPos c = b; !c.equals(a); c = prev.get(c)) route.add(0, c);
            BlockPos from = a;
            for (BlockPos to : route) {
                pts.add(face(r, from, to));
                blocks.add(to); // the stretch after a face point runs through the block it enters
                from = to;
            }
        }

        /** A point on the face shared by two touching blocks, away from its edges. */
        private static Vec3 face(Random r, BlockPos a, BlockPos b) {
            double x = a.getX() + 0.5 + (b.getX() - a.getX()) * 0.5;
            double y = a.getY() + 0.5 + (b.getY() - a.getY()) * 0.5;
            double z = a.getZ() + 0.5 + (b.getZ() - a.getZ()) * 0.5;
            double j = 0.5 - INSET;
            if (a.getX() == b.getX()) x += (r.nextDouble() * 2 - 1) * j;
            if (a.getY() == b.getY()) y += (r.nextDouble() * 2 - 1) * j * 0.5;
            if (a.getZ() == b.getZ()) z += (r.nextDouble() * 2 - 1) * j;
            return new Vec3(x, y, z);
        }

        /** A random point in a tank block, clear of its glass sides; a little above the floor and below the top. */
        private static Vec3 inside(Paths p, Random r, BlockPos b) {
            boolean floor = p.isGlass(b, Direction.DOWN), ceiling = p.isGlass(b, Direction.UP);
            double y0 = floor ? 0.3 : 0.05, y1 = ceiling ? 0.72 : 0.95;
            return new Vec3(b.getX() + INSET + r.nextDouble() * (1 - 2 * INSET), b.getY() + y0 + r.nextDouble() * (y1 - y0), b.getZ() + INSET + r.nextDouble() * (1 - 2 * INSET));
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

        Vec3 at(float t) {
            int n = this.pts.length;
            float total = this.start[n];
            float u = t % total;
            if (u < 0) u += total;
            int i = index(t);
            float f = (u - this.start[i]) / (this.start[i + 1] - this.start[i]);
            Vec3 p0 = this.pts[(i - 1 + n) % n], p1 = this.pts[i], p2 = this.pts[(i + 1) % n], p3 = this.pts[(i + 2) % n];
            return new Vec3(cr(p0.x, p1.x, p2.x, p3.x, f), cr(p0.y, p1.y, p2.y, p3.y, f), cr(p0.z, p1.z, p2.z, p3.z, f));
        }

        private static double cr(double a, double b, double c, double d, float t) {
            double t2 = t * t, t3 = t2 * t;
            return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
        }
    }
}

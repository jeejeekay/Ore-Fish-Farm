package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.block.TankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
 * Draws an aquarium's fish, once per aquarium (from its controller block). A lone tank keeps the 1.x look: the
 * fish hovers in place with a slow bob and sway, two fish face each other. In a joined aquarium every fish swims
 * a slow, smooth loop through the whole aquarium. Paths are worked out once per aquarium shape and cached, so a
 * frame only evaluates a spline per fish. At most 24 fish are drawn per aquarium, and none past 32 blocks.
 */
public class AquariumRenderer implements BlockEntityRenderer<TankBlockEntity> {
    private static final float LONE_SCALE = 0.62F, PAIR_SCALE = 0.45F, ROAM_SCALE = 0.5F;
    private static final int MAX_FISH = 24;
    private static final double MAX_DIST = 32;
    private static final Map<BlockPos, Paths> CACHE = new HashMap<>();

    public AquariumRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TankBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.isController()) return;
        List<BlockPos> members = be.members();
        float time = (level.getGameTime() + partial) / 20F;
        if (members.size() <= 1) {
            single(be, time, pose, buffers, level);
            return;
        }
        BlockPos origin = be.getBlockPos();
        Paths paths = CACHE.get(origin);
        if (paths == null || paths.hash != members.hashCode()) {
            if (CACHE.size() > 512) CACHE.clear();
            paths = new Paths(members);
            CACHE.put(origin.immutable(), paths);
        }
        int drawn = 0;
        for (int m = 0; m < members.size() && drawn < MAX_FISH; m++) {
            if (!(level.getBlockEntity(members.get(m)) instanceof TankBlockEntity t)) continue;
            for (int s = 0; s < TankBlockEntity.FISH && drawn < MAX_FISH; s++) {
                ItemStack fish = IGoByJake.orefishfarm.data.FishData.display(t.fishStack(s));
                if (fish.isEmpty()) continue;
                Path path = paths.get(m * TankBlockEntity.FISH + s, m);
                Vec3 p = path.at(time);
                Vec3 ahead = path.at(time + 0.15F);
                double dx = ahead.x - p.x, dy = ahead.y - p.y, dz = ahead.z - p.z;
                float yaw = (float) Math.atan2(-dz, dx);
                float pitch = (float) Mth.clamp(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)), -0.4, 0.4);
                int lit = LevelRenderer.getLightColor(level, BlockPos.containing(p));
                pose.pushPose();
                pose.translate(p.x - origin.getX(), p.y - origin.getY(), p.z - origin.getZ());
                pose.mulPose(Axis.YP.rotation(yaw));
                pose.mulPose(Axis.ZP.rotation(pitch));
                pose.scale(ROAM_SCALE, ROAM_SCALE, ROAM_SCALE);
                FishRender.render(fish, pose, buffers, lit, FishRender.tailWag(time * 1.4F, m + s));
                pose.popPose();
                drawn++;
            }
        }
    }

    /** One block on its own: the fish swim in place, a pair face each other. */
    private static void single(TankBlockEntity be, float time, PoseStack pose, MultiBufferSource buffers, Level level) {
        ItemStack a = be.fishStack(0), b = be.fishStack(1); // only checked for presence
        boolean pair = !a.isEmpty() && !b.isEmpty();
        float phase0 = (be.getBlockPos().asLong() & 255) / 40F;
        int lit = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        for (int i = 0; i < TankBlockEntity.FISH; i++) {
            ItemStack fish = IGoByJake.orefishfarm.data.FishData.display(be.fishStack(i));
            if (fish.isEmpty()) continue;
            float phase = phase0 + i * 1.7F;
            pose.pushPose();
            pose.translate(0.5, 0.52, 0.5);
            if (pair) {
                pose.translate(i == 0 ? -0.2 : 0.2, 0, 0);
                if (i == 1) pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            pose.translate(0, Mth.sin(time * 2.0F + phase) * 0.03F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * 2.7F + phase) * 10F));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 1.3F + phase) * 3F));
            float sc = pair ? PAIR_SCALE : LONE_SCALE;
            pose.scale(sc, sc, sc);
            FishRender.render(fish, pose, buffers, lit, FishRender.tailWag(time, phase));
            pose.popPose();
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

    /** Swim paths for one aquarium shape. */
    private static final class Paths {
        final int hash;
        final List<BlockPos> members;
        final Set<BlockPos> set;
        final Map<Integer, Path> byFish = new HashMap<>();

        Paths(List<BlockPos> members) {
            this.hash = members.hashCode();
            this.members = new ArrayList<>(members);
            this.set = new HashSet<>(members);
        }

        Path get(int fish, int home) {
            return this.byFish.computeIfAbsent(fish, k -> new Path(this, k, home));
        }
    }

    /** A closed loop of waypoints inside the aquarium, followed with a Catmull-Rom spline at a slow, even pace. */
    private static final class Path {
        private static final int POINTS = 10;
        private static final double SPEED = 0.35; // blocks per second
        final Vec3[] pts = new Vec3[POINTS];
        final float[] start = new float[POINTS + 1];

        Path(Paths p, int seed, int home) {
            Random r = new Random(seed * 341873128712L + p.hash);
            int cur = home;
            for (int i = 0; i < POINTS; i++) {
                if (i > 0) {
                    // Next block: a random one close to the current, so fish wander rather than dart across.
                    BlockPos c = p.members.get(cur);
                    List<Integer> near = new ArrayList<>();
                    for (int j = 0; j < p.members.size(); j++) if (p.members.get(j).distManhattan(c) <= 2) near.add(j);
                    cur = near.get(r.nextInt(near.size()));
                }
                BlockPos b = p.members.get(cur);
                boolean floor = !p.set.contains(b.below()), ceiling = !p.set.contains(b.above());
                double y0 = floor ? 0.3 : 0.05, y1 = ceiling ? 0.72 : 0.95;
                this.pts[i] = new Vec3(b.getX() + 0.2 + r.nextDouble() * 0.6, b.getY() + y0 + r.nextDouble() * (y1 - y0), b.getZ() + 0.2 + r.nextDouble() * 0.6);
            }
            for (int i = 0; i < POINTS; i++) {
                double d = this.pts[i].distanceTo(this.pts[(i + 1) % POINTS]);
                this.start[i + 1] = this.start[i] + (float) Math.max(1.5, d / SPEED);
            }
        }

        Vec3 at(float t) {
            float total = this.start[POINTS];
            float u = t % total;
            if (u < 0) u += total;
            int i = 0;
            while (i < POINTS - 1 && u >= this.start[i + 1]) i++;
            float f = (u - this.start[i]) / (this.start[i + 1] - this.start[i]);
            Vec3 p0 = this.pts[(i - 1 + POINTS) % POINTS], p1 = this.pts[i], p2 = this.pts[(i + 1) % POINTS], p3 = this.pts[(i + 2) % POINTS];
            return new Vec3(cr(p0.x, p1.x, p2.x, p3.x, f), cr(p0.y, p1.y, p2.y, p3.y, f), cr(p0.z, p1.z, p2.z, p3.z, f));
        }

        private static double cr(double a, double b, double c, double d, float t) {
            double t2 = t * t, t3 = t2 * t;
            return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
        }
    }
}

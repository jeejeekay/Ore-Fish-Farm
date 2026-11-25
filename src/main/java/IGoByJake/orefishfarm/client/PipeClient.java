package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.pipe.PipeMenu;
import IGoByJake.orefishfarm.pipe.PipePackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

/**
 * Client side of the Pipe Tanks: the network screen's snapshots, and the fish swimming through the pipes.
 *
 * <p>Each leg arrives once (path, start and end time); drawing it is a lerp along the path each frame, from one
 * render hook for all of them (no block entities, no per-pipe renderers). Only legs within {@link #MAX_DIST} of
 * the camera are drawn, at most {@link #MAX_DRAWN}. A fish stays drawn a moment where its leg ended, then is
 * gone until its next leg.
 */
@Mod.EventBusSubscriber(modid = OreFishFarm.MODID, value = Dist.CLIENT)
public final class PipeClient {
    private static final double MAX_DIST = 48;
    private static final int MAX_DRAWN = 256;
    /** Ticks a fish stays drawn where its leg ended. */
    private static final long LINGER = 30;
    private static final float SIZE = 0.2F;
    /** How far right of the pipe's centre a swimming fish keeps (blocks; the water is 5 px across). */
    private static final double KEEP_RIGHT = 1.25 / 16;

    private record Trip(long[] path, long start, long end, ItemStack fish, ItemStack cargo) {}

    private static final Long2ObjectOpenHashMap<Trip> TRIPS = new Long2ObjectOpenHashMap<>();
    private static ClientLevel tripsLevel;

    private PipeClient() {}

    public static void snapshot(PipePackets.Snapshot msg) {
        var p = Minecraft.getInstance().player;
        if (p != null && p.containerMenu instanceof PipeMenu m && m.containerId == msg.containerId()) m.applySnapshot(msg.data());
    }

    public static void leg(PipePackets.Leg msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != tripsLevel) {
            TRIPS.clear();
            tripsLevel = level;
        }
        if (msg.path().length == 0) TRIPS.remove(msg.uid());
        else TRIPS.put(msg.uid(), new Trip(msg.path(), msg.start(), msg.end(), msg.fish(), msg.cargo()));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRIPS.clear();
        tripsLevel = null;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || TRIPS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != tripsLevel) {
            TRIPS.clear();
            return;
        }
        float partial = event.getPartialTick();
        double now = level.getGameTime() + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int drawn = 0;
        for (Iterator<Long2ObjectMap.Entry<Trip>> it = TRIPS.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
            var entry = it.next();
            Trip t = entry.getValue();
            if (now > t.end + LINGER || now < t.start - 200) {
                it.remove();
                continue;
            }
            if (drawn >= MAX_DRAWN) continue;
            double f = t.end <= t.start ? 1 : Mth.clamp((now - t.start) / (t.end - t.start), 0, 1);
            int n = t.path.length;
            double along = f * (n - 1);
            int i = Math.min(n - 2, (int) Math.floor(along));
            Vec3 a, b;
            if (n == 1) {
                a = b = centre(t.path[0]);
            } else {
                a = centre(t.path[Math.max(0, i)]);
                b = centre(t.path[Math.max(0, i) + 1]);
            }
            double k = n == 1 ? 0 : along - Math.max(0, i);
            Vec3 p = a.lerp(b, k);
            // Keep to the right of the way it's going, so fish going opposite ways pass side by side.
            Vec3 heading = b.subtract(a);
            Vec3 right = Math.abs(heading.y) > Math.abs(heading.x) + Math.abs(heading.z)
                    ? new Vec3(heading.y > 0 ? 1 : -1, 0, 0)
                    : new Vec3(-heading.z, 0, heading.x).normalize();
            p = p.add(right.scale(KEEP_RIGHT));
            if (p.distanceToSqr(cam) > MAX_DIST * MAX_DIST) continue;
            Vec3 dir = b.subtract(a);
            if (dir.lengthSqr() < 1.0E-6) dir = new Vec3(1, 0, 0);
            float yaw = Math.abs(dir.x) + Math.abs(dir.z) < 1.0E-6 ? 0F : (float) Math.atan2(-dir.z, dir.x);
            float pitch = dir.y == 0 ? 0F : (float) Math.copySign(Math.toRadians(70), dir.y);
            float wob = (float) Math.sin(now * 0.35 + (entry.getLongKey() & 0xFF)) * 0.02F;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            pose.pushPose();
            pose.translate(p.x - cam.x, p.y - cam.y + wob, p.z - cam.z);
            pose.mulPose(Axis.YP.rotation(yaw));
            pose.mulPose(Axis.ZP.rotation(pitch));
            Entity mob = FishLook.mob(t.fish);
            if (mob != null) {
                FishLook.render(mob, SIZE, (long) now + (entry.getLongKey() & 0xFFF), partial, pose, buffers, light);
            } else {
                pose.pushPose();
                pose.scale(SIZE, SIZE, SIZE);
                mc.getItemRenderer().renderStatic(t.fish, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
                pose.popPose();
            }
            if (!t.cargo.isEmpty()) {
                // What it carries, held at its nose.
                pose.pushPose();
                pose.translate(SIZE * 0.62F, 0, 0);
                pose.mulPose(Axis.YP.rotationDegrees(90));
                pose.scale(0.2F, 0.2F, 0.2F);
                mc.getItemRenderer().renderStatic(t.cargo, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
                pose.popPose();
            }
            pose.popPose();
            drawn++;
        }
        buffers.endBatch();
    }

    private static Vec3 centre(long p) {
        return new Vec3(BlockPos.getX(p) + 0.5, BlockPos.getY(p) + 0.5, BlockPos.getZ(p) + 0.5);
    }
}

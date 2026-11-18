package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.block.TankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws an aquarium's fish, once per aquarium (from its controller block). Each fish stays in its own tank
 * block and bobs gently in place, side-on to the viewer: the mob from its bucket, or the bucket's own sprite if
 * it has no mob. Two fish in one block sit side by side. At most 24 fish are drawn per aquarium, and none past
 * 32 blocks.
 */
public class AquariumRenderer implements BlockEntityRenderer<TankBlockEntity> {
    private static final float SIZE_ALONE = 0.5F, SIZE_PAIR = 0.38F;
    private static final int MAX_FISH = 24;
    private static final double MAX_DIST = 32;

    public AquariumRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TankBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.isController()) return;
        List<BlockPos> members = be.members();
        BlockPos origin = be.getBlockPos();
        float time = (level.getGameTime() + partial) / 20F;
        // Side-on to the camera: turn to face it around the vertical axis.
        float camYaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        int drawn = 0;
        for (BlockPos p : members) {
            if (drawn >= MAX_FISH) break;
            if (!(level.getBlockEntity(p) instanceof TankBlockEntity t)) continue;
            ItemStack a = t.fishStack(0), b = t.fishStack(1);
            boolean pair = !a.isEmpty() && !b.isEmpty();
            int lit = LevelRenderer.getLightColor(level, p.above());
            for (int s = 0; s < TankBlockEntity.FISH && drawn < MAX_FISH; s++) {
                ItemStack bucket = t.fishStack(s);
                if (bucket.isEmpty()) continue;
                float phase = (p.asLong() & 255) / 40F + s * 1.7F;
                pose.pushPose();
                pose.translate(p.getX() - origin.getX() + 0.5, p.getY() - origin.getY() + 0.55, p.getZ() - origin.getZ() + 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(-camYaw));
                if (pair) pose.translate(s == 0 ? -0.18 : 0.18, s == 0 ? 0.04 : -0.04, 0);
                pose.translate(0, Mth.sin(time * 1.6F + phase) * 0.035F, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 1.1F + phase) * 3F));
                if (pair && s == 1) pose.mulPose(Axis.YP.rotationDegrees(180));
                float size = pair ? SIZE_PAIR : SIZE_ALONE;
                Entity mob = FishLook.mob(bucket);
                if (mob != null) {
                    FishLook.render(mob, size, pose, buffers, lit);
                } else {
                    pose.scale(size, size, size);
                    Minecraft.getInstance().getItemRenderer().renderStatic(bucket, ItemDisplayContext.FIXED, lit,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
                }
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
}

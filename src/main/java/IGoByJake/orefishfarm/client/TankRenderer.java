package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.block.BaseTankBlockEntity;
import IGoByJake.orefishfarm.block.TankBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the fish inside a tank, side-on to the tank's front (see FishRender for model lookup).
 * One fish swims slowly from side to side and turns round at the ends; in a Breeding Tank the two fish face
 * each other and drift together. Only drawn within 48 blocks.
 */
public class TankRenderer implements BlockEntityRenderer<BaseTankBlockEntity> {
    private static final float LONE_SCALE = 0.5F;
    private static final float PAIR_SCALE = 0.36F;

    public TankRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(BaseTankBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        Direction facing = be.getBlockState().hasProperty(TankBlock.FACING) ? be.getBlockState().getValue(TankBlock.FACING) : Direction.SOUTH;
        int fishCount = be.items().getSlots() - 2;
        float t = (be.getLevel().getGameTime() + partial) / 20F + (be.getBlockPos().asLong() & 255) / 7F;
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());

        pose.pushPose();
        pose.translate(0.5, 0.53, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        if (fishCount == 1) {
            drawFish(be.fish(0), pose, buffers, lit, FishRender.swimOffset(t, 0.25F) * 0.17F, Mth.sin(t * 0.8F) * 0.012F,
                    FishRender.swimYaw(t, 0.25F), LONE_SCALE, FishRender.tailWag(t, 0));
        } else {
            float drift = Mth.sin(t * 0.22F) * 0.06F;
            float gap = 0.19F + Mth.sin(t * 0.5F) * 0.015F;
            drawFish(be.fish(0), pose, buffers, lit, drift - gap, Mth.sin(t * 0.8F) * 0.012F, 0, PAIR_SCALE, FishRender.tailWag(t, 0));
            drawFish(be.fish(1), pose, buffers, lit, drift + gap, Mth.sin(t * 0.8F + 1.3F) * 0.012F, 180, PAIR_SCALE, FishRender.tailWag(t, 1.7F));
        }
        pose.popPose();
    }

    private static void drawFish(ItemStack fish, PoseStack pose, MultiBufferSource buffers, int light, float x, float y, float yaw, float scale, float tail) {
        if (fish.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.scale(scale, scale, scale);
        FishRender.render(fish, pose, buffers, light, tail);
        pose.popPose();
    }

    @Override
    public boolean shouldRender(BaseTankBlockEntity be, Vec3 camera) {
        return Vec3.atCenterOf(be.getBlockPos()).closerThan(camera, 48);
    }
}

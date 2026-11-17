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
 * Draws the fish inside a tank, side-on to the tank's front (see FishRender for model lookup). Each fish swims
 * in place: a slow bob, a gentle sway and a slight roll. In a Breeding Tank the two face each other.
 * Only drawn within 48 blocks.
 */
public class TankRenderer implements BlockEntityRenderer<BaseTankBlockEntity> {
    private static final float LONE_SCALE = 0.62F;
    private static final float PAIR_SCALE = 0.45F;

    public TankRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(BaseTankBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        Direction facing = be.getBlockState().hasProperty(TankBlock.FACING) ? be.getBlockState().getValue(TankBlock.FACING) : Direction.SOUTH;
        int fishCount = be.items().getSlots() - 2;
        float time = (be.getLevel().getGameTime() + partial) / 20F;
        float phase0 = (be.getBlockPos().asLong() & 255) / 40F;
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        for (int i = 0; i < fishCount; i++) {
            ItemStack fish = be.fish(i);
            if (fish.isEmpty()) continue;
            float phase = phase0 + i * 1.7F;
            pose.pushPose();
            pose.translate(0.5, 0.52, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            if (fishCount == 2) {
                pose.translate(i == 0 ? -0.2 : 0.2, 0, 0);
                if (i == 1) pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            pose.translate(0, Mth.sin(time * 2.0F + phase) * 0.03F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * 2.7F + phase) * 10F));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 1.3F + phase) * 3F));
            float s = fishCount == 2 ? PAIR_SCALE : LONE_SCALE;
            pose.scale(s, s, s);
            FishRender.render(fish, pose, buffers, lit, FishRender.tailWag(time, phase));
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRender(BaseTankBlockEntity be, Vec3 camera) {
        return Vec3.atCenterOf(be.getBlockPos()).closerThan(camera, 48);
    }
}

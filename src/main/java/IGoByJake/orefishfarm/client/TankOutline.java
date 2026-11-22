package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.block.TankBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The outline on a looked-at tank. Vanilla draws block outlines before the translucent layer, so a tank's own
 * glass and water drew over it and it looked faded. Here vanilla's is skipped for tanks and the same outline
 * (black, 40% like vanilla's) is drawn after the translucent blocks, a hair outside the block so the glass faces
 * can't cover it.
 */
@Mod.EventBusSubscriber(modid = OreFishFarm.MODID, value = Dist.CLIENT)
public final class TankOutline {
    private TankOutline() {}

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof TankBlock) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui || mc.getCameraEntity() == null) return;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bh.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof TankBlock) || !mc.level.getWorldBorder().isWithinBounds(pos)) return;
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(mc.getCameraEntity()));
        if (shape.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - cam.x + 0.5, pos.getY() - cam.y + 0.5, pos.getZ() - cam.z + 0.5);
        pose.scale(1.004F, 1.004F, 1.004F);
        pose.translate(-0.5, -0.5, -0.5);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len == 0) return;
            dx /= len;
            dy /= len;
            dz /= len;
            vc.vertex(last.pose(), (float) x1, (float) y1, (float) z1).color(0F, 0F, 0F, 0.4F).normal(last.normal(), dx, dy, dz).endVertex();
            vc.vertex(last.pose(), (float) x2, (float) y2, (float) z2).color(0F, 0F, 0F, 0.4F).normal(last.normal(), dx, dy, dz).endVertex();
        });
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}

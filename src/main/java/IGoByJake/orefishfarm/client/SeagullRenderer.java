package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.entity.Seagull;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SeagullRenderer extends MobRenderer<Seagull, SeagullModel> {
    private static final ResourceLocation TEX = new ResourceLocation(OreFishFarm.MODID, "textures/entity/seagull.png");

    public SeagullRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SeagullModel(ctx.bakeLayer(SeagullModel.LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(Seagull gull) {
        return TEX;
    }

    @Override
    protected void setupRotations(Seagull gull, PoseStack pose, float age, float bodyYaw, float partial) {
        super.setupRotations(gull, pose, age, bodyYaw, partial);
        if (!gull.isPerched()) {
            // Pitch with its flight path (nose down when diving) and a slight bank while circling.
            pose.mulPose(Axis.XP.rotationDegrees(gull.getViewXRot(partial)));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.02F) * 8F));
        }
    }

    @Override
    public void render(Seagull gull, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(gull, yaw, partial, pose, buffers, light);
        ItemStack held = gull.carried();
        if (held.isEmpty()) return;
        // Whatever it's carrying dangles from its beak.
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, gull.yBodyRotO, gull.yBodyRot)));
        pose.translate(0, 0.42, 0.62);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(0.4F, 0.4F, 0.4F);
        Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, gull.level(), gull.getId());
        pose.popPose();
    }
}

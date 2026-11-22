package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.entity.OreFish;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Living ore fish: the same side-view model the tanks use, turned to face where it swims, tail wagging. */
public class OreFishRenderer extends EntityRenderer<OreFish> {
    private static final float SCALE = 0.55F;

    public OreFishRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(OreFish fish, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = fish.tickCount + partial;
        boolean wet = fish.isInWater();
        pose.pushPose();
        pose.translate(0, 0.2F, 0);
        // Entity yaw 0 faces +z; the model's head is +x, so turn it a quarter.
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, fish.yBodyRotO, fish.yBodyRot) - 90F));
        if (!wet) {
            pose.translate(0, -0.05F, 0);
            pose.mulPose(Axis.XP.rotationDegrees(90)); // flopping on its side
        }
        float big = SCALE * fish.sizeScale();
        pose.scale(big, big, big);
        float wag = Mth.sin(age * (wet ? 0.6F : 1.4F)) * (wet ? 18F : 28F);
        String variant = fish instanceof IGoByJake.orefishfarm.entity.DataFish df && df.isSheared() ? "_sheared" : "";
        FishRender.render(fish.fishItem(), variant, pose, buffers, light, wag);
        pose.popPose();
        super.render(fish, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(OreFish fish) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

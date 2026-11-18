package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.FishBucketItem;
import IGoByJake.orefishfarm.data.FishData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a Fish Bucket as its habitat's bucket (water, lava or void) with the fish's own sprite in front of it,
 * so one item can show any species, including fish added by datapacks.
 */
public class FishBucketRenderer extends BlockEntityWithoutLevelRenderer {
    private static FishBucketRenderer instance;

    public static FishBucketRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new FishBucketRenderer(mc);
        }
        return instance;
    }

    private FishBucketRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var items = Minecraft.getInstance().getItemRenderer();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        items.renderStatic(FishBucketItem.look(stack), ItemDisplayContext.NONE, light, overlay, pose, buffers, null, 0);
        ItemStack fish = FishData.display(stack);
        if (!fish.isEmpty() && fish.getItem() != stack.getItem()) {
            pose.translate(0.14, -0.12, 0.04);
            pose.scale(0.62F, 0.62F, 0.62F);
            items.renderStatic(fish, ItemDisplayContext.NONE, light, overlay, pose, buffers, null, 0);
        }
        pose.popPose();
    }
}

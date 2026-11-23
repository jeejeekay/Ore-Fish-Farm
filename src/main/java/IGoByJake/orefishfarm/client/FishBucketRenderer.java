package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.FishBucketItem;
import IGoByJake.orefishfarm.data.FishData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a Fish Bucket. A species with its own bucket sprite ({@code <ns>:models/item/fish_bucket/<fish>.json}, like
 * vanilla's bucket of cod: the fish lying across the top of a bucket) shows that. Any other species (fish added by
 * datapacks or other mods without one) shows its habitat's bucket with the fish's sprite in front of it.
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
        ItemStack species = FishData.display(stack);
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(species.getItem());
        Item vanilla = species.isEmpty() ? null : FishData.vanillaBucket(species.getItem());
        if (vanilla != null) {
            // An older Fish Bucket of a fish that has its own bucket (cod, salmon): draw that bucket.
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            items.renderStatic(new ItemStack(vanilla), ItemDisplayContext.NONE, light, overlay, pose, buffers, null, 0);
            pose.popPose();
            return;
        }
        if (!species.isEmpty() && id != null) {
            var models = Minecraft.getInstance().getModelManager();
            var own = models.getModel(new net.minecraft.resources.ResourceLocation(id.getNamespace(), "item/fish_bucket/" + id.getPath()));
            if (own != null && own != models.getMissingModel()) {
                pose.pushPose();
                pose.translate(0.5, 0.5, 0.5);
                items.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, own);
                pose.popPose();
                return;
            }
        }
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

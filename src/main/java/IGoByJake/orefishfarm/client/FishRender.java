package IGoByJake.orefishfarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws a fish side-on, for tanks, tank screens and the living fish mobs.
 *
 * <p>For an item {@code ns:path} it looks for a side-view model at {@code assets/ns/models/fish/path.json} (head
 * toward +x, in a 16x16x16 box), plus an optional {@code path_tail.json} that wags. Any resource pack can add one
 * for any mod's fish. With no model it falls back to the item's own sprite.
 */
public final class FishRender {
    private FishRender() {}

    private record Fish(BakedModel body, BakedModel tail, float jointX) {}

    private static final Map<ResourceLocation, Fish> CACHE = new HashMap<>();
    private static final Fish NONE = new Fish(null, null, 0);

    /** Registers every models/fish/*.json so it gets baked. */
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        CACHE.clear();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        rm.listResources("models/fish", p -> p.getPath().endsWith(".json")).keySet().forEach(file -> {
            String path = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
            event.register(new ResourceLocation(file.getNamespace(), path));
        });
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Fish lookup(ItemStack stack) {
        return lookup(stack, "");
    }

    /** The model {@code fish/<item path><suffix>} (and its {@code _tail}); NONE if there isn't one. */
    private static Fish lookup(ItemStack stack, String suffix) {
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (item == null) return NONE;
        ResourceLocation id = new ResourceLocation(item.getNamespace(), item.getPath() + suffix);
        return CACHE.computeIfAbsent(id, k -> {
            var models = Minecraft.getInstance().getModelManager();
            BakedModel missing = models.getMissingModel();
            BakedModel body = models.getModel(new ResourceLocation(k.getNamespace(), "fish/" + k.getPath()));
            if (body == null || body == missing) return NONE;
            BakedModel tail = models.getModel(new ResourceLocation(k.getNamespace(), "fish/" + k.getPath() + "_tail"));
            if (tail == missing && !suffix.isEmpty()) // a variant without its own tail uses the plain one
                tail = models.getModel(new ResourceLocation(item.getNamespace(), "fish/" + item.getPath() + "_tail"));
            if (tail == missing) tail = null;
            return new Fish(body, tail, tail == null ? 0.5F : maxX(tail));
        });
    }

    /** The tail's joint is its front edge (largest x). */
    private static float maxX(BakedModel model) {
        float[] max = {0};
        RandomSource r = RandomSource.create(0);
        Consumer<BakedQuad> scan = q -> {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) max[0] = Math.max(max[0], Float.intBitsToFloat(v[i * stride]));
        };
        model.getQuads(null, null, r).forEach(scan);
        for (Direction d : Direction.values()) model.getQuads(null, d, r).forEach(scan);
        return max[0];
    }

    public static boolean hasModel(ItemStack stack) {
        return !stack.isEmpty() && lookup(stack).body != null;
    }

    /**
     * Draws the fish centred on the current origin, head toward +x, 1 unit long at scale 1.
     * tailDeg: tail wag angle. Falls back to the item sprite (facing +z) when the fish has no model.
     */
    public static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, float tailDeg) {
        render(stack, "", pose, buffers, light, tailDeg);
    }

    /** As {@link #render(ItemStack, PoseStack, MultiBufferSource, int, float)}, with a model variant (e.g. "_sheared") if it exists. */
    public static void render(ItemStack stack, String variant, PoseStack pose, MultiBufferSource buffers, int light, float tailDeg) {
        if (stack.isEmpty()) return;
        Fish fish = variant.isEmpty() ? NONE : lookup(stack, variant);
        if (fish.body == null) fish = lookup(stack);
        var items = Minecraft.getInstance().getItemRenderer();
        if (fish.body == null) {
            pose.pushPose();
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
            pose.popPose();
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.translate(-0.5F, -0.5F, -0.5F);
        items.renderModelLists(fish.body, stack, light, OverlayTexture.NO_OVERLAY, pose, vc);
        if (fish.tail != null) {
            pose.pushPose();
            pose.translate(fish.jointX, 0.5F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(tailDeg));
            pose.translate(-fish.jointX, -0.5F, -0.5F);
            items.renderModelLists(fish.tail, stack, light, OverlayTexture.NO_OVERLAY, pose, vc);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Back-and-forth swimming: position from -1 to 1 for time t (seconds). */
    public static float swimOffset(float t, float speed) {
        return Mth.sin(t * speed);
    }

    /** Heading for {@link #swimOffset}: 0 = head toward +x, 180 = toward -x, turning through 90 (facing you) at each end. */
    public static float swimYaw(float t, float speed) {
        float v = Mth.cos(t * speed);
        float turn = Mth.clamp(v / 0.35F, -1F, 1F);
        return 90F - 90F * turn;
    }

    public static float tailWag(float t, float phase) {
        return Mth.sin(t * 5.0F + phase) * 14F;
    }

    /** A fish in a screen: centred at (x, y), sizePx long, yaw as in {@link #swimYaw}. */
    public static void renderGui(GuiGraphics g, ItemStack stack, float x, float y, float sizePx, float yaw, float tailDeg) {
        if (stack.isEmpty()) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        if (hasModel(stack)) {
            pose.translate(x, y, 150);
            pose.scale(sizePx, sizePx, sizePx);
            pose.mulPose(Axis.XP.rotationDegrees(180)); // screen y points down
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            Lighting.setupForFlatItems();
            render(stack, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, tailDeg);
            g.flush();
            Lighting.setupFor3DItems();
        } else {
            float s = sizePx / 16F;
            pose.translate(x, y, 0);
            pose.scale(s, s, 1);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            g.renderItem(stack, -8, -8);
        }
        pose.popPose();
    }
}

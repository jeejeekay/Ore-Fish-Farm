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
 *
 * <p>Articulated fish (salmon-style swimming) also have {@code path_front.json}: the front stays still and the body
 * model swings, carrying the tail with it.
 * <p>Joints: where the body swings and the tail wags come from the pivots of the "body" and "tail" groups in the
 * shape's Blockbench file (src/main/fishmodels, split at build time into assets/orefishfarm/fish_joints.json, keyed
 * by the model the fish file points at). A model with no entry there falls back to its pieces' edges: the front's
 * back edge for the body, the tail's front edge for the tail.
 */
public final class FishRender {
    private FishRender() {}

    private record Fish(BakedModel body, BakedModel tail, float jointX, @org.jetbrains.annotations.Nullable BakedModel front, float bodyJoint, float minY, float maxY) {}

    private static final Map<ResourceLocation, Fish> CACHE = new HashMap<>();
    private static final Fish NONE = new Fish(null, null, 0, null, 0, 0.375F, 0.625F);

    /** Registers every models/fish/*.json so it gets baked. */
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        CACHE.clear();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        rm.listResources("models/item/fish_bucket", p -> p.getPath().endsWith(".json")).keySet().forEach(file -> {
            String path = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
            event.register(new ResourceLocation(file.getNamespace(), path));
        });
        rm.listResources("models/fish", p -> p.getPath().endsWith(".json")).keySet().forEach(file -> {
            String path = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
            event.register(new ResourceLocation(file.getNamespace(), path));
        });
    }

    public static void clearCache() {
        CACHE.clear();
        joints = null;
    }

    /** Model id ("orefishfarm:fish_category/sturgeon") -> {body joint x, tail joint x} in blocks; NaN = not set. */
    private static Map<String, float[]> joints;

    private static Map<String, float[]> joints() {
        if (joints != null) return joints;
        Map<String, float[]> out = new HashMap<>();
        var rm = Minecraft.getInstance().getResourceManager();
        rm.getResource(new ResourceLocation("orefishfarm", "fish_joints.json")).ifPresent(res -> {
            try (var reader = res.openAsReader()) {
                var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                for (var e : json.entrySet()) {
                    var o = e.getValue().getAsJsonObject();
                    float body = o.has("body") ? o.getAsJsonArray("body").get(0).getAsFloat() / 16 : Float.NaN;
                    float tail = o.has("tail") ? o.getAsJsonArray("tail").get(0).getAsFloat() / 16 : Float.NaN;
                    out.put(e.getKey(), new float[]{body, tail});
                }
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger("orefishfarm").warn("Couldn't read fish_joints.json", ex);
            }
        });
        return joints = out;
    }

    /** The joints for fish model {@code fish/<path>}: its own entry, or the shape its file points at (parent). */
    private static float @org.jetbrains.annotations.Nullable [] jointsFor(ResourceLocation fishModel) {
        Map<String, float[]> all = joints();
        String own = fishModel.getNamespace() + ":fish/" + fishModel.getPath();
        if (all.containsKey(own)) return all.get(own);
        var rm = Minecraft.getInstance().getResourceManager();
        var file = new ResourceLocation(fishModel.getNamespace(), "models/fish/" + fishModel.getPath() + ".json");
        try (var reader = rm.getResource(file).map(r -> { try { return r.openAsReader(); } catch (Exception e) { return null; } }).orElse(null)) {
            if (reader == null) return null;
            var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            if (!json.has("parent")) return null;
            String parent = json.get("parent").getAsString();
            if (!parent.contains(":")) parent = "minecraft:" + parent;
            return all.get(parent);
        } catch (Exception e) {
            return null;
        }
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
            BakedModel front = models.getModel(new ResourceLocation(k.getNamespace(), "fish/" + k.getPath() + "_front"));
            if (front == missing && !suffix.isEmpty())
                front = models.getModel(new ResourceLocation(item.getNamespace(), "fish/" + item.getPath() + "_front"));
            if (front == missing) front = null;
            float[] y = {1, 0};
            for (BakedModel part : new BakedModel[]{body, tail, front}) if (part != null) yRange(part, y);
            float[] j = jointsFor(k);
            float tailJoint = tail == null ? 0.5F : j != null && !Float.isNaN(j[1]) ? j[1] : maxX(tail);
            float bodyJoint = front == null ? 0.5F : j != null && !Float.isNaN(j[0]) ? j[0] : minX(front);
            return new Fish(body, tail, tailJoint, front, bodyJoint, y[0], y[1]);
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

    /** Widens {@code y} (min, max) to the model's lowest and highest points. */
    private static void yRange(BakedModel model, float[] y) {
        RandomSource r = RandomSource.create(0);
        Consumer<BakedQuad> scan = q -> {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) {
                float vy = Float.intBitsToFloat(v[i * stride + 1]);
                y[0] = Math.min(y[0], vy);
                y[1] = Math.max(y[1], vy);
            }
        };
        model.getQuads(null, null, r).forEach(scan);
        for (Direction d : Direction.values()) model.getQuads(null, d, r).forEach(scan);
    }

    /**
     * How high to lift a fish drawn {@code scale} long so it sits in a hitbox {@code boxHeight} tall: its model
     * centred in the box, and never with any part below the box's bottom (so big or deep-bodied fish don't sink
     * into the ground).
     */
    public static float lift(ItemStack stack, String variant, float scale, float boxHeight) {
        Fish fish = variant.isEmpty() ? NONE : lookup(stack, variant);
        if (fish.body == null) fish = lookup(stack);
        float centred = boxHeight / 2 - ((fish.minY + fish.maxY) / 2 - 0.5F) * scale;
        return Math.max(centred, (0.5F - fish.minY) * scale + 0.01F);
    }

    /** The front's joint is its back edge (smallest x). */
    private static float minX(BakedModel model) {
        float[] min = {1};
        RandomSource r = RandomSource.create(0);
        Consumer<BakedQuad> scan = q -> {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) min[0] = Math.min(min[0], Float.intBitsToFloat(v[i * stride]));
        };
        model.getQuads(null, null, r).forEach(scan);
        for (Direction d : Direction.values()) model.getQuads(null, d, r).forEach(scan);
        return min[0];
    }

    /** How much of the wag the swinging body takes, and how much the tail adds on top, for articulated fish. */
    private static final float BODY_SWING = 0.35F, TAIL_SWING = 0.75F;

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
        float tailOwn = tailDeg;
        if (fish.front != null) {
            // Salmon-style: the front holds still, the body swings from the front's back edge, the tail follows.
            items.renderModelLists(fish.front, stack, light, OverlayTexture.NO_OVERLAY, pose, vc);
            pose.pushPose();
            pose.translate(fish.bodyJoint, 0.5F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(tailDeg * BODY_SWING));
            pose.translate(-fish.bodyJoint, -0.5F, -0.5F);
            tailOwn = tailDeg * TAIL_SWING;
        }
        items.renderModelLists(fish.body, stack, light, OverlayTexture.NO_OVERLAY, pose, vc);
        if (fish.tail != null) {
            pose.pushPose();
            pose.translate(fish.jointX, 0.5F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(tailOwn));
            pose.translate(-fish.jointX, -0.5F, -0.5F);
            items.renderModelLists(fish.tail, stack, light, OverlayTexture.NO_OVERLAY, pose, vc);
            pose.popPose();
        }
        if (fish.front != null) pose.popPose();
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

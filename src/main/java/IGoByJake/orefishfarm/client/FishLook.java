package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.FishBucketItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws whatever mob is in a fish bucket: our Fish Buckets (the species' mob), vanilla buckets (cod, axolotl,
 * tropical fish with its pattern) and any mod's mob bucket. One copy of each mob is kept per bucket and posed by
 * the tank: no AI and no ticking, only its age is set before each draw so its own swim animation (tail, fins) plays.
 */
public final class FishLook {
    private static final Map<String, Entity> CACHE = new HashMap<>();
    private static Method fishType;

    private FishLook() {}

    /** The mob in this bucket, posed and ready to draw; null if there is none. */
    @Nullable
    public static Entity mob(ItemStack bucket) {
        var level = Minecraft.getInstance().level;
        if (level == null || bucket.isEmpty()) return null;
        String key = ForgeRegistries.ITEMS.getKey(bucket.getItem()) + "|" + bucket.getTag();
        Entity e = CACHE.get(key);
        if (e != null && e.level() == level) return e;
        if (CACHE.size() > 256) CACHE.clear();
        EntityType<?> type = typeOf(bucket);
        if (type == null) return null;
        e = type.create(level);
        if (e == null) return null;
        CompoundTag tag = bucket.getTag();
        if (e instanceof Bucketable b && tag != null) b.loadFromBucketTag(tag.copy());
        e.setYRot(0);
        e.setXRot(0);
        // Count as in water, so fish renderers don't lay the mob on its side as if it were flopping on land.
        try {
            net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Entity.class, e, true, "f_19798_");
        } catch (Exception ignored) {
        }
        if (e instanceof LivingEntity l) {
            l.yBodyRot = 0;
            l.yHeadRot = 0;
            l.yBodyRotO = 0;
            l.yHeadRotO = 0;
        }
        CACHE.put(key, e);
        return e;
    }

    @Nullable
    private static EntityType<?> typeOf(ItemStack bucket) {
        {
            var species = FishBucketItem.species(bucket);
            if (species != null) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(species);
                return id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
            }
        }
        if (bucket.getItem() instanceof MobBucketItem mb) {
            try {
                if (fishType == null) {
                    fishType = MobBucketItem.class.getDeclaredMethod("getFishType");
                    fishType.setAccessible(true);
                }
                return (EntityType<?>) fishType.invoke(mb);
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Draws the mob centred on the current pose origin, side-on (its length along x), fitted into a box of the
     * given size.
     */
    public static void render(Entity e, float size, long age, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        e.tickCount = (int) age;
        EntityRenderDispatcher d = Minecraft.getInstance().getEntityRenderDispatcher();
        float big = Math.max(e.getBbWidth(), e.getBbHeight());
        float sc = size / Math.max(0.2F, big);
        pose.pushPose();
        pose.scale(sc, sc, sc);
        pose.translate(0, -e.getBbHeight() / 2, 0);
        // Mobs face +z at yaw 0; turn them side-on.
        pose.mulPose(Axis.YP.rotationDegrees(90));
        d.setRenderShadow(false);
        d.render(e, 0, 0, 0, 0, partial, pose, buffers, light);
        d.setRenderShadow(true);
        pose.popPose();
    }
}

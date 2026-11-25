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
        // Pufferfish are shown fully puffed (their big model; the hitbox grows with it).
        if (e instanceof net.minecraft.world.entity.animal.Pufferfish p) p.setPuffState(2);
        e.refreshDimensions(); // big species (fish file "scale") are fitted by their scaled hitbox
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
                if (id == null) return null;
                // The species' own mob, or the shared data fish (it takes its species from the bucket).
                return ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : IGoByJake.orefishfarm.Registry.DATA_FISH_MOB.get();
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
     * How long this mob is drawn in a tank whose normal fish size is {@code base} (blocks): relative to a cod by
     * (adult size / referenceSize) ^ sizePower, between minSize and the tank's maximum, then times any sizeOverrides
     * entry and the mob's baby scale (so babies stay smaller). See the tank_display config section.
     */
    public static float tankSize(Entity e, float base, boolean lone) {
        float scale = e instanceof LivingEntity l ? l.getScale() : 1F;
        float adult = Math.max(e.getBbWidth(), e.getBbHeight()) / Math.max(0.05F, scale);
        double rel = Math.pow(Math.max(0.01, adult) / IGoByJake.orefishfarm.Config.TANK_REFERENCE_SIZE.get(), IGoByJake.orefishfarm.Config.TANK_SIZE_POWER.get());
        double max = lone ? IGoByJake.orefishfarm.Config.TANK_MAX_LONE.get() : IGoByJake.orefishfarm.Config.TANK_MAX_JOINED.get();
        double d = net.minecraft.util.Mth.clamp(base * rel, Math.min(IGoByJake.orefishfarm.Config.TANK_MIN.get(), max), max);
        var id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id != null) d *= IGoByJake.orefishfarm.Config.tankSizeOverride(id.toString());
        return (float) (d * scale);
    }

    /** This mob's modelSizeFixes multiplier (1 if none): about hitbox length / model length. */
    public static float modelFix(Entity e) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? 1F : IGoByJake.orefishfarm.Config.tankSizeOverride(id.toString());
    }

    /**
     * The hitbox size this mob is fitted to in a Pipe Tank (see {@link #render}), for a pipe whose biggest fish is
     * {@code max}: the tank's sizing relative to a cod (tank_display referenceSize and sizePower), capped at max and
     * at least half of it, then the modelSizeFixes entry (so a mob's model, not its hitbox, is what fits) and the
     * baby scale.
     */
    public static float pipeSize(Entity e, float max) {
        float scale = e instanceof LivingEntity l ? l.getScale() : 1F;
        float adult = Math.max(e.getBbWidth(), e.getBbHeight()) / Math.max(0.05F, scale);
        double rel = Math.pow(Math.max(0.01, adult) / IGoByJake.orefishfarm.Config.TANK_REFERENCE_SIZE.get(), IGoByJake.orefishfarm.Config.TANK_SIZE_POWER.get());
        double d = net.minecraft.util.Mth.clamp(max * rel, max * 0.5, max);
        var id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id != null) d *= IGoByJake.orefishfarm.Config.tankSizeOverride(id.toString());
        return (float) (d * Math.min(1F, scale));
    }

    /**
     * Draws the mob centred on the current pose origin, side-on (its length along x), its hitbox's longest side
     * fitted to {@code size} (from {@link #tankSize}).
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

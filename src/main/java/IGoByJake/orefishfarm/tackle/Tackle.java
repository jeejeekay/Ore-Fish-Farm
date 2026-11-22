package IGoByJake.orefishfarm.tackle;

import IGoByJake.orefishfarm.OreFishFarm;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tackle: one piece (hook, float or sinker) clipped onto a fishing rod. Each piece stands for a tech age and
 * raises the chance of certain fish in certain liquids and dimensions; there is one slot, so nothing stacks.
 * <p>What a piece does is data: {@code data/<ns>/orefishfarm_tackle/<name>.json}
 * <pre>{ "item": "orefishfarm:squid_spinner",
 *   "bonuses": [ { "fish": "orefishfarm:ferrofin", "chance": 0.06, "liquid": "water", "dimension": "minecraft:overworld" } ] }</pre>
 * {@code liquid} (water, lava or void) and {@code dimension} are optional. Bonuses are tried in order; the first
 * that rolls replaces the catch (the {@code orefishfarm:tackle} loot modifier).
 * <p>Any item in {@code #orefishfarm:tackle} can be clipped on. The piece is kept on the rod in the rod's NBT
 * ({@link #KEY}), with its own damage. Items in {@code #orefishfarm:custom_bobber} change the cast bobber's look to
 * {@code <ns>:textures/entity/bobber/<path>.png}.
 */
public final class Tackle {
    public static final String KEY = "OreFishTackle";
    public static final TagKey<Item> TACKLE = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "tackle"));
    public static final TagKey<Item> CUSTOM_BOBBER = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "custom_bobber"));
    private static final Logger LOG = LogUtils.getLogger();

    public record Bonus(Item fish, float chance, @Nullable String liquid, @Nullable ResourceLocation dimension) {
    }

    private static Map<Item, List<Bonus>> bonuses = Map.of();

    private Tackle() {
    }

    public static boolean isRod(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ToolActions.FISHING_ROD_CAST);
    }

    public static boolean isTackle(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TACKLE);
    }

    /** The piece on this rod, or empty. */
    public static ItemStack get(ItemStack rod) {
        CompoundTag tag = rod.getTag();
        if (tag == null || !tag.contains(KEY)) return ItemStack.EMPTY;
        return ItemStack.of(tag.getCompound(KEY));
    }

    /** Puts a piece on the rod (or takes it off, for an empty stack). */
    public static void set(ItemStack rod, ItemStack tackle) {
        if (tackle.isEmpty()) {
            CompoundTag tag = rod.getTag();
            if (tag != null) {
                tag.remove(KEY);
                if (tag.isEmpty()) rod.setTag(null);
            }
        } else {
            ItemStack one = tackle.copyWithCount(1);
            rod.getOrCreateTag().put(KEY, one.save(new CompoundTag()));
        }
    }

    /** The rod a player is fishing with: main hand first, like vanilla. */
    public static ItemStack rodOf(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isRod(main)) return main;
        ItemStack off = player.getOffhandItem();
        return isRod(off) ? off : ItemStack.EMPTY;
    }

    /** Every tackle piece and its bonuses (for JEI). */
    public static Map<Item, List<Bonus>> all() {
        return bonuses;
    }

    /** Whether this side has the tackle data (sent from the server on login and reload). */
    public static boolean loaded() {
        return loaded;
    }

    private static boolean loaded;

    /** The server's tackle data, sent to clients so JEI can show it. */
    public record Synced(Map<Item, List<Bonus>> data) {}

    public static void write(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(bonuses.size());
        bonuses.forEach((item, list) -> {
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(item));
            buf.writeVarInt(list.size());
            for (Bonus b : list) {
                buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(b.fish()));
                buf.writeFloat(b.chance());
                buf.writeUtf(b.liquid() == null ? "" : b.liquid());
                buf.writeUtf(b.dimension() == null ? "" : b.dimension().toString());
            }
        });
    }

    public static Synced read(net.minecraft.network.FriendlyByteBuf buf) {
        Map<Item, List<Bonus>> out = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            int m = buf.readVarInt();
            List<Bonus> list = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                Item fish = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
                float chance = buf.readFloat();
                String liquid = buf.readUtf(), dim = buf.readUtf();
                list.add(new Bonus(fish, chance, liquid.isEmpty() ? null : liquid, dim.isEmpty() ? null : new ResourceLocation(dim)));
            }
            out.put(item, List.copyOf(list));
        }
        return new Synced(Map.copyOf(out));
    }

    public static void applySynced(Synced s) {
        bonuses = s.data();
        loaded = true;
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) IGoByJake.orefishfarm.compat.jei.OreFishJei.lateTackle();
    }

    public static List<Bonus> bonusesOf(ItemStack tackle) {
        return tackle.isEmpty() ? List.of() : bonuses.getOrDefault(tackle.getItem(), List.of());
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    private static final class Listener extends SimpleJsonResourceReloadListener {
        Listener() {
            super(new com.google.gson.Gson(), "orefishfarm_tackle");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<Item, List<Bonus>> out = new HashMap<>();
            files.forEach((id, json) -> {
                try {
                    JsonObject o = json.getAsJsonObject();
                    Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(GsonHelper.getAsString(o, "item")));
                    List<Bonus> list = new ArrayList<>();
                    for (JsonElement e : GsonHelper.getAsJsonArray(o, "bonuses", new com.google.gson.JsonArray())) {
                        JsonObject b = e.getAsJsonObject();
                        ResourceLocation fishId = new ResourceLocation(GsonHelper.getAsString(b, "fish"));
                        if (!BuiltInRegistries.ITEM.containsKey(fishId)) continue; // a fish from a mod that isn't here
                        String liquid = b.has("liquid") ? GsonHelper.getAsString(b, "liquid") : null;
                        ResourceLocation dim = b.has("dimension") ? new ResourceLocation(GsonHelper.getAsString(b, "dimension")) : null;
                        list.add(new Bonus(BuiltInRegistries.ITEM.get(fishId), GsonHelper.getAsFloat(b, "chance"), liquid, dim));
                    }
                    out.put(item, List.copyOf(list));
                } catch (RuntimeException ex) {
                    LOG.error("Bad tackle file {}: {}", id, ex.getMessage());
                }
            });
            bonuses = Map.copyOf(out);
            loaded = true;
        }
    }
}

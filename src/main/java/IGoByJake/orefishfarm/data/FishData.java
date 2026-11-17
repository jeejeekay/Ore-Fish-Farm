package IGoByJake.orefishfarm.data;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.OreFishFarm;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything about which items are fish, what they make and what they breed into is datapack data:
 *
 * <pre>
 * data/&lt;ns&gt;/orefishfarm_fish/&lt;name&gt;.json
 *   { "fish": "orefishfarm:ferrofin",
 *     "product": { "item": "orefishfarm:iron_scale", "count": 1 },   (optional: a fish can make nothing)
 *     "product_chance": 0.25,   chance per food eaten in a Fish Tank
 *     "breed_chance": 0.08,     chance per food eaten in a Breeding Tank with two of this fish
 *     "power": 30 }             FE/tick while eating in power mode (optional; 0 = not a power fish)
 *
 * data/&lt;ns&gt;/orefishfarm_breeding/&lt;name&gt;.json
 *   { "parents": ["orefishfarm:cuprum_eel", "orefishfarm:zincray"],
 *     "result": { "item": "orefishfarm:brass_bream", "count": 1 }, "chance": 0.04 }
 *
 * data/&lt;ns&gt;/orefishfarm_food/&lt;name&gt;.json
 *   { "item": "orefishfarm:fish_food", "ticks": 400 }
 * </pre>
 *
 * Any item can be made a fish this way. Items in {@code #orefishfarm:tank_fish} without a file are fish that
 * make nothing but can breed; items in {@code #orefishfarm:fish_food} without a file last {@code defaultFoodTicks}.
 * Files naming an item that isn't installed are skipped, so packs can ship entries for optional mods.
 */
public final class FishData {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    public static final TagKey<Item> TANK_FISH = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "tank_fish"));
    public static final TagKey<Item> FISH_FOOD = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "fish_food"));

    public record Fish(Item item, ItemStack product, float productChance, float breedChance, int power) {
        public boolean makesPower() { return this.power > 0; }
        public boolean makesProduct() { return !this.product.isEmpty() && this.productChance > 0; }
    }

    public record Cross(Item a, Item b, ItemStack result, float chance) {
        boolean matches(Item x, Item y) { return (x == a && y == b) || (x == b && y == a); }
    }

    private static Map<Item, Fish> fish = Map.of();
    private static List<Cross> crosses = List.of();
    private static Map<Item, Integer> foods = Map.of();
    /** False on a client connected to a remote server, where the files aren't known: slots then accept anything and the server decides. */
    private static boolean loaded;
    /** True where the files were read from disk (a server, or singleplayer); clients elsewhere get them over the network. */
    private static boolean fromDisk;

    private FishData() {}

    public static boolean loaded() { return loaded; }

    /** FE/tick this fish makes in a Power Tank, after the config multiplier. */
    public static int powerPerTick(Fish f) {
        return fromDisk ? (int) Math.round(f.power() * Config.POWER.get()) : f.power();
    }

    /** Sent to each player on join and /reload (see Network): fish (power already multiplied) and foods. */
    public static void write(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(fish.size());
        for (Fish f : fish.values()) {
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(f.item()));
            buf.writeItem(f.product());
            buf.writeFloat(f.productChance());
            buf.writeFloat(f.breedChance());
            buf.writeVarInt(powerPerTick(f));
        }
        buf.writeVarInt(foods.size());
        foods.forEach((item, ticks) -> {
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(item));
            buf.writeVarInt(ticks);
        });
    }

    public record Synced(Map<Item, Fish> fish, Map<Item, Integer> foods) {}

    public static Synced read(net.minecraft.network.FriendlyByteBuf buf) {
        Map<Item, Fish> f = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item item = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            ItemStack product = buf.readItem();
            Fish fish = new Fish(item, product, buf.readFloat(), buf.readFloat(), buf.readVarInt());
            if (item != null) f.put(item, fish);
        }
        Map<Item, Integer> food = new HashMap<>();
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item item = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            int t = buf.readVarInt();
            if (item != null) food.put(item, t);
        }
        return new Synced(f, food);
    }

    /** Client side: use the server's lists unless this game already read them from disk (singleplayer). */
    public static void applySynced(Synced s) {
        if (fromDisk) return;
        fish = s.fish();
        foods = s.foods();
        loaded = true;
    }

    @Nullable
    public static Fish fish(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Fish f = fish.get(stack.getItem());
        if (f != null) return f;
        if (stack.is(TANK_FISH)) return new Fish(stack.getItem(), ItemStack.EMPTY, 0, Config.DEFAULT_BREED_CHANCE.get().floatValue(), 0);
        return null;
    }

    public static boolean isFish(ItemStack stack) {
        return !loaded || fish(stack) != null;
    }

    public static int foodTicks(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer t = foods.get(stack.getItem());
        if (t != null) return t;
        return stack.is(FISH_FOOD) ? Config.DEFAULT_FOOD_TICKS.get() : 0;
    }

    public static boolean isFood(ItemStack stack) {
        return !loaded || foodTicks(stack) > 0;
    }

    @Nullable
    public static Cross cross(Item a, Item b) {
        for (Cross c : crosses) if (c.matches(a, b)) return c;
        return null;
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Listener("orefishfarm_fish", FishData::loadFish));
        event.addListener(new Listener("orefishfarm_breeding", FishData::loadCrosses));
        event.addListener(new Listener("orefishfarm_food", FishData::loadFoods));
    }

    private interface Loader { void load(Map<ResourceLocation, JsonElement> files); }

    private static final class Listener extends SimpleJsonResourceReloadListener {
        private final Loader loader;
        Listener(String dir, Loader loader) { super(GSON, dir); this.loader = loader; }
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            this.loader.load(files);
            loaded = true;
            fromDisk = true;
        }
    }

    @Nullable
    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return null;
        return ForgeRegistries.ITEMS.getValue(rl);
    }

    /** {"item": "...", "count": n} or just "id". Null if the item isn't installed. */
    @Nullable
    private static ItemStack stack(JsonElement e) {
        if (e.isJsonPrimitive()) {
            Item i = item(e.getAsString());
            return i == null ? null : new ItemStack(i);
        }
        JsonObject o = e.getAsJsonObject();
        Item i = item(GsonHelper.getAsString(o, "item"));
        return i == null ? null : new ItemStack(i, GsonHelper.getAsInt(o, "count", 1));
    }

    private static void loadFish(Map<ResourceLocation, JsonElement> files) {
        Map<Item, Fish> out = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                JsonObject o = json.getAsJsonObject();
                Item f = item(GsonHelper.getAsString(o, "fish"));
                if (f == null) return;
                ItemStack product = ItemStack.EMPTY;
                if (o.has("product")) {
                    ItemStack p = stack(o.get("product"));
                    if (p == null) return; // product from a mod that isn't installed: skip the whole fish
                    product = p;
                }
                out.put(f, new Fish(f, product, GsonHelper.getAsFloat(o, "product_chance", 0.25F),
                        GsonHelper.getAsFloat(o, "breed_chance", 0.1F), GsonHelper.getAsInt(o, "power", 0)));
            } catch (Exception ex) {
                LOG.error("Bad fish file {}: {}", id, ex.getMessage());
            }
        });
        fish = out;
        LOG.info("Ore Fish Farm: {} fish", out.size());
    }

    private static void loadCrosses(Map<ResourceLocation, JsonElement> files) {
        List<Cross> out = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                JsonObject o = json.getAsJsonObject();
                JsonArray p = GsonHelper.getAsJsonArray(o, "parents");
                Item a = item(p.get(0).getAsString());
                Item b = item(p.get(1).getAsString());
                ItemStack r = stack(o.get("result"));
                if (a == null || b == null || r == null) return;
                out.add(new Cross(a, b, r, GsonHelper.getAsFloat(o, "chance", 0.05F)));
            } catch (Exception ex) {
                LOG.error("Bad breeding file {}: {}", id, ex.getMessage());
            }
        });
        crosses = out;
    }

    private static void loadFoods(Map<ResourceLocation, JsonElement> files) {
        Map<Item, Integer> out = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                JsonObject o = json.getAsJsonObject();
                Item i = item(GsonHelper.getAsString(o, "item"));
                if (i != null) out.put(i, Math.max(1, GsonHelper.getAsInt(o, "ticks", 400)));
            } catch (Exception ex) {
                LOG.error("Bad fish food file {}: {}", id, ex.getMessage());
            }
        });
        foods = out;
    }
}

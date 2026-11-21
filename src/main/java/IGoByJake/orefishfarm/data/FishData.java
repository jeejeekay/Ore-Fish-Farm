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
 *     "product_chance": 0.75,   chance per bite (every biteTicks of eating) in a Fish Tank
 *     "breed_chance": 0.08,     chance per bite for a pair of this fish (while the tank's breeding is on)
 *     "power": 30,              FE/tick while eating in a power tank (optional; 0 = not a power fish)
 *     "habitat": "orefishfarm:water",  which tank it lives in (optional; water by default)
 *     "bucket": "somemod:fish_bucket" } optional: this mod's own bucket for the fish (else the generic Fish Bucket)
 *
 * data/&lt;ns&gt;/orefishfarm_breeding/&lt;name&gt;.json
 *   { "parents": ["orefishfarm:cuprum_eel",
 *                 { "species": "orefishfarm:zincray", "genes": ["orefishfarm:mutagenic"] }],   a parent can need genes
 *     "requires": ["somemod:gene"],   optional: genes at least one of the two parents must have
 *     "result": { "item": "orefishfarm:brass_bream", "count": 1 }, "chance": 0.04 }
 *
 * data/&lt;ns&gt;/orefishfarm_gene/&lt;gene&gt;.json   (optional; the gene itself is a genes/ item tag, see FishGenes)
 *   { "color": "#C040C0" }
 *
 * data/&lt;ns&gt;/orefishfarm_food/&lt;name&gt;.json
 *   { "item": "orefishfarm:fish_food", "ticks": 4800,   how long one piece feeds one fish
 *     "habitat": "orefishfarm:void" }   optional: while it is being eaten, any fish survives in a tank of this habitat
 *
 * data/&lt;ns&gt;/orefishfarm_habitat/&lt;name&gt;.json   (the file name is the habitat id)
 *   { "look": "water" | "lava" | "void",   how the tank is drawn
 *     "color": "#3F9FE4",                  tint of the fill (water look only)
 *     "kit": "minecraft:lava_bucket",     item that turns a Fish Tank into this habitat (its bucket)
 *     "bucket": "minecraft:lava_bucket",   filled container that carries this habitat's fish
 *     "empty": "minecraft:bucket" }        what is left when such a fish is let out
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

    /** Every fish lives here unless its file says otherwise. */
    public static final ResourceLocation WATER = new ResourceLocation(OreFishFarm.MODID, "water");

    /**
     * bucket: the filled container that carries a fish of this habitat (water bucket, lava bucket, or a plain
     * bucket for void fish); it also scoops a newborn out of a tank. empty: what is left when such a fish is let out;
     * crafting a fish bucket into something gives it back.
     */
    public record Habitat(ResourceLocation id, String look, int color, @Nullable Item kit, Item bucket, Item empty) {}

    public record Food(int ticks, @Nullable ResourceLocation habitat) {}

    private static final Habitat DEFAULT_WATER = new Habitat(WATER, "water", 0x3F9FE4, null, net.minecraft.world.item.Items.WATER_BUCKET, net.minecraft.world.item.Items.BUCKET);

    /** bucket: another mod's own bucket for this fish, if it has one (else the generic Fish Bucket is used). */
    public record Fish(Item item, ItemStack product, float productChance, float breedChance, int power, ResourceLocation habitat, @Nullable Item bucket) {
        public boolean makesPower() { return this.power > 0; }
        public boolean makesProduct() { return !this.product.isEmpty() && this.productChance > 0; }
    }

    /** aGenes / bGenes: genes that parent must have; requires: genes at least one parent must have. */
    public record Cross(Item a, Item b, ItemStack result, float chance, List<ResourceLocation> aGenes, List<ResourceLocation> bGenes,
                        List<ResourceLocation> requires) {
        boolean matches(Item x, Item y) { return (x == a && y == b) || (x == b && y == a); }

        boolean matches(ItemStack x, Item xi, ItemStack y, Item yi) {
            boolean ok;
            if (xi == a && yi == b && FishGenes.hasAll(x, aGenes) && FishGenes.hasAll(y, bGenes)) ok = true;
            else ok = xi == b && yi == a && FishGenes.hasAll(x, bGenes) && FishGenes.hasAll(y, aGenes);
            if (!ok) return false;
            for (ResourceLocation g : requires) if (!FishGenes.hasGene(x, g) && !FishGenes.hasGene(y, g)) return false;
            return true;
        }
    }

    private static Map<Item, Fish> fish = Map.of();
    private static Map<Item, Fish> byBucket = Map.of();
    private static List<Cross> crosses = List.of();
    private static Map<Item, Food> foods = Map.of();
    private static Map<ResourceLocation, Habitat> habitats = Map.of();
    private static Map<ResourceLocation, Integer> geneColors = Map.of();
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

    /** Chances after the config multipliers (synced copies already have them applied). */
    public static float productChance(Fish f) {
        return fromDisk ? (float) Math.min(1, f.productChance() * Config.PRODUCT_CHANCE.get()) : f.productChance();
    }

    public static float breedChance(Fish f) {
        return fromDisk ? (float) Math.min(1, f.breedChance() * Config.BREED_CHANCE.get()) : f.breedChance();
    }

    public static float crossChance(Cross c) {
        return fromDisk ? (float) Math.min(1, c.chance() * Config.CROSSBREED_CHANCE.get()) : c.chance();
    }

    public static java.util.Collection<Fish> allFish() {
        return fish.values();
    }

    public static List<Cross> allCrosses() {
        return crosses;
    }

    /** Every item that works as fish food (files plus the tag). */
    public static List<ItemStack> allFoods() {
        java.util.LinkedHashSet<Item> out = new java.util.LinkedHashSet<>(foods.keySet());
        ForgeRegistries.ITEMS.tags().getTag(FISH_FOOD).forEach(out::add);
        List<ItemStack> list = new ArrayList<>();
        out.forEach(i -> list.add(new ItemStack(i)));
        return list;
    }

    /** Sent to each player on join and /reload (see Network): fish (power already multiplied) and foods. */
    public static void write(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(fish.size());
        for (Fish f : fish.values()) {
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(f.item()));
            buf.writeItem(f.product());
            buf.writeFloat(productChance(f));
            buf.writeFloat(breedChance(f));
            buf.writeVarInt(powerPerTick(f));
            buf.writeResourceLocation(f.habitat());
            buf.writeBoolean(f.bucket() != null);
            if (f.bucket() != null) buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(f.bucket()));
        }
        buf.writeVarInt(crosses.size());
        for (Cross c : crosses) {
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(c.a()));
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(c.b()));
            buf.writeItem(c.result());
            buf.writeFloat(crossChance(c));
            writeIds(buf, c.aGenes());
            writeIds(buf, c.bGenes());
            writeIds(buf, c.requires());
        }
        buf.writeVarInt(foods.size());
        foods.forEach((item, food) -> {
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(item));
            buf.writeVarInt(food.ticks());
            buf.writeBoolean(food.habitat() != null);
            if (food.habitat() != null) buf.writeResourceLocation(food.habitat());
        });
        buf.writeVarInt(habitats.size());
        for (Habitat h : habitats.values()) {
            buf.writeResourceLocation(h.id());
            buf.writeUtf(h.look());
            buf.writeInt(h.color());
            buf.writeBoolean(h.kit() != null);
            if (h.kit() != null) buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(h.kit()));
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(h.bucket()));
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(h.empty()));
        }
        buf.writeVarInt(geneColors.size());
        geneColors.forEach((id, c) -> {
            buf.writeResourceLocation(id);
            buf.writeInt(c);
        });
    }

    private static void writeIds(net.minecraft.network.FriendlyByteBuf buf, List<ResourceLocation> ids) {
        buf.writeVarInt(ids.size());
        ids.forEach(buf::writeResourceLocation);
    }

    private static List<ResourceLocation> readIds(net.minecraft.network.FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ResourceLocation> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(buf.readResourceLocation());
        return out;
    }

    public record Synced(Map<Item, Fish> fish, List<Cross> crosses, Map<Item, Food> foods, Map<ResourceLocation, Habitat> habitats,
                         Map<ResourceLocation, Integer> geneColors) {}

    public static Synced read(net.minecraft.network.FriendlyByteBuf buf) {
        Map<Item, Fish> f = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item item = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            ItemStack product = buf.readItem();
            float pc = buf.readFloat(), bc = buf.readFloat();
            int pw = buf.readVarInt();
            ResourceLocation hab = buf.readResourceLocation();
            Item bucket = buf.readBoolean() ? ForgeRegistries.ITEMS.getValue(buf.readResourceLocation()) : null;
            Fish fish = new Fish(item, product, pc, bc, pw, hab, bucket);
            if (item != null) f.put(item, fish);
        }
        List<Cross> x = new ArrayList<>();
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item a = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            Item b = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            ItemStack r = buf.readItem();
            float c = buf.readFloat();
            List<ResourceLocation> ag = readIds(buf), bg = readIds(buf), rq = readIds(buf);
            if (a != null && b != null) x.add(new Cross(a, b, r, c, ag, bg, rq));
        }
        Map<Item, Food> food = new HashMap<>();
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item item = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            int t = buf.readVarInt();
            ResourceLocation h = buf.readBoolean() ? buf.readResourceLocation() : null;
            if (item != null) food.put(item, new Food(t, h));
        }
        Map<ResourceLocation, Habitat> hab = new HashMap<>();
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String look = buf.readUtf();
            int color = buf.readInt();
            Item kit = buf.readBoolean() ? ForgeRegistries.ITEMS.getValue(buf.readResourceLocation()) : null;
            Item bucket = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            Item empty = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            hab.put(id, new Habitat(id, look, color, kit, bucket, empty));
        }
        Map<ResourceLocation, Integer> gc = new HashMap<>();
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) gc.put(buf.readResourceLocation(), buf.readInt());
        return new Synced(f, x, food, hab, gc);
    }

    /** Client side: use the server's lists unless this game already read them from disk (singleplayer). */
    public static void applySynced(Synced s) {
        if (fromDisk) return;
        fish = s.fish();
        byBucket = indexBuckets(s.fish());
        crosses = s.crosses();
        foods = s.foods();
        habitats = s.habitats();
        geneColors = s.geneColors();
        loaded = true;
        // JEI may already have built its lists before this arrived (on a server): give it the tank pages now.
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) IGoByJake.orefishfarm.compat.jei.OreFishJei.lateRecipes();
    }

    /** The fish a stack is or carries: a plain fish item, a Fish Bucket, or another mod's fish bucket. */
    @Nullable
    public static Fish fish(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item species = IGoByJake.orefishfarm.FishBucketItem.species(stack);
        if (species != null) return byItem(species);
        Fish b = byBucket.get(stack.getItem());
        if (b != null) return b;
        Fish f = byItem(stack.getItem());
        if (f != null) return f;
        // Any other mob bucket (cod, axolotl, another mod's fish): a plain water fish with no product.
        if (stack.getItem() instanceof net.minecraft.world.item.MobBucketItem) {
            return new Fish(stack.getItem(), ItemStack.EMPTY, 0, Config.DEFAULT_BREED_CHANCE.get().floatValue(), 0, WATER, stack.getItem());
        }
        return null;
    }

    @Nullable
    private static Fish byItem(Item item) {
        Fish f = fish.get(item);
        if (f != null) return f;
        if (new ItemStack(item).is(TANK_FISH)) return new Fish(item, ItemStack.EMPTY, 0, Config.DEFAULT_BREED_CHANCE.get().floatValue(), 0, WATER, null);
        return null;
    }

    /** A live fish in a bucket: what tanks hold. */
    public static boolean isBucketed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item species = IGoByJake.orefishfarm.FishBucketItem.species(stack);
        if (species != null) return !loaded || byItem(species) != null;
        return byBucket.containsKey(stack.getItem()) || stack.getItem() instanceof net.minecraft.world.item.MobBucketItem;
    }

    /** Whether a tank takes this stack as a fish: only live, bucketed fish. */
    public static boolean isFish(ItemStack stack) {
        return isBucketed(stack);
    }

    /** The fish's own item, for drawing it. */
    public static ItemStack display(ItemStack stack) {
        Item species = IGoByJake.orefishfarm.FishBucketItem.species(stack);
        if (species != null) return new ItemStack(species);
        Fish b = byBucket.get(stack.getItem());
        return b != null ? new ItemStack(b.item()) : stack;
    }

    /** A live fish of this kind in its bucket: the fish's own mod bucket if it has one, else a Fish Bucket. */
    public static ItemStack bucketOf(Fish f) {
        return f.bucket() != null ? new ItemStack(f.bucket()) : IGoByJake.orefishfarm.FishBucketItem.of(f.item());
    }

    /** A plain fish item from before buckets (1.x and 2.0 tanks) becomes a bucketed fish. */
    public static ItemStack wrapLegacy(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof IGoByJake.orefishfarm.FishBucketItem) return stack;
        if (stack.getItem() instanceof IGoByJake.orefishfarm.FishItem) return IGoByJake.orefishfarm.FishBucketItem.of(stack.getItem());
        return stack;
    }

    /** The container that scoops this fish out of a tank: its habitat's bucket (water, lava, or an empty bucket). */
    public static Item scoop(ItemStack fish) {
        return habitat(habitatOf(fish)).bucket();
    }

    /** A gene's colour from its orefishfarm_gene file, or null. */
    @Nullable
    public static Integer geneColor(ResourceLocation gene) {
        return geneColors.get(gene);
    }

    private static Map<Item, Fish> indexBuckets(Map<Item, Fish> all) {
        Map<Item, Fish> m = new HashMap<>();
        for (Fish f : all.values()) if (f.bucket() != null) m.put(f.bucket(), f);
        return m;
    }

    public static int foodTicks(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Food f = foods.get(stack.getItem());
        if (f != null) return f.ticks();
        return stack.is(FISH_FOOD) ? Config.DEFAULT_FOOD_TICKS.get() : 0;
    }

    /** The habitat this food makes livable while it is eaten, or null for an ordinary food. */
    @Nullable
    public static ResourceLocation foodHabitat(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Food f = foods.get(stack.getItem());
        return f == null ? null : f.habitat();
    }

    /** Where a fish lives (water if it isn't a known fish). */
    public static ResourceLocation habitatOf(ItemStack fish) {
        Fish f = fish(fish);
        return f == null ? WATER : f.habitat();
    }

    public static Habitat habitat(@Nullable ResourceLocation id) {
        if (id == null) return habitats.getOrDefault(WATER, DEFAULT_WATER);
        Habitat h = habitats.get(id);
        if (h != null) return h;
        return id.equals(WATER) ? DEFAULT_WATER : habitats.getOrDefault(WATER, DEFAULT_WATER);
    }

    public static boolean isHabitat(ResourceLocation id) {
        return id.equals(WATER) || habitats.containsKey(id);
    }

    /** The habitat an item converts a tank into, if it is a habitat kit. */
    @Nullable
    public static Habitat habitatForKit(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Habitat h : habitats.values()) if (h.kit() != null && stack.is(h.kit())) return h;
        return null;
    }

    public static java.util.Collection<Habitat> allHabitats() {
        return habitats.values();
    }

    /** Display name of a habitat: lang key habitat.<ns>.<path>. */
    public static net.minecraft.network.chat.Component habitatName(ResourceLocation id) {
        return net.minecraft.network.chat.Component.translatable("habitat." + id.getNamespace() + "." + id.getPath());
    }

    public static boolean isFood(ItemStack stack) {
        return !loaded || foodTicks(stack) > 0;
    }

    @Nullable
    public static Cross cross(Item a, Item b) {
        for (Cross c : crosses) if (c.matches(a, b)) return c;
        return null;
    }

    /** The crossbreed these two fish make, checking the genes each entry asks for. */
    @Nullable
    public static Cross cross(ItemStack x, ItemStack y) {
        Fish fx = fish(x), fy = fish(y);
        if (fx == null || fy == null) return null;
        for (Cross c : crosses) if (c.matches(x, fx.item(), y, fy.item())) return c;
        return null;
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Listener("orefishfarm_fish", FishData::loadFish));
        event.addListener(new Listener("orefishfarm_breeding", FishData::loadCrosses));
        event.addListener(new Listener("orefishfarm_food", FishData::loadFoods));
        event.addListener(new Listener("orefishfarm_habitat", FishData::loadHabitats));
        event.addListener(new Listener("orefishfarm_gene", FishData::loadGenes));
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
                ResourceLocation hab = o.has("habitat") ? new ResourceLocation(GsonHelper.getAsString(o, "habitat")) : WATER;
                Item bucket = o.has("bucket") ? item(GsonHelper.getAsString(o, "bucket")) : null;
                out.put(f, new Fish(f, product, GsonHelper.getAsFloat(o, "product_chance", 0.75F),
                        GsonHelper.getAsFloat(o, "breed_chance", 0.1F), GsonHelper.getAsInt(o, "power", 0), hab, bucket));
            } catch (Exception ex) {
                LOG.error("Bad fish file {}: {}", id, ex.getMessage());
            }
        });
        fish = out;
        byBucket = indexBuckets(out);
        LOG.info("Ore Fish Farm: {} fish", out.size());
    }

    private static void loadCrosses(Map<ResourceLocation, JsonElement> files) {
        List<Cross> out = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                JsonObject o = json.getAsJsonObject();
                JsonArray p = GsonHelper.getAsJsonArray(o, "parents");
                Item a = parentItem(p.get(0)), b = parentItem(p.get(1));
                ItemStack r = stack(o.get("result"));
                if (a == null || b == null || r == null) return;
                out.add(new Cross(a, b, r, GsonHelper.getAsFloat(o, "chance", 0.05F), parentGenes(p.get(0)), parentGenes(p.get(1)),
                        o.has("requires") ? ids(GsonHelper.getAsJsonArray(o, "requires")) : List.of()));
            } catch (Exception ex) {
                LOG.error("Bad breeding file {}: {}", id, ex.getMessage());
            }
        });
        crosses = out;
    }

    @Nullable
    private static Item parentItem(JsonElement e) {
        return item(e.isJsonPrimitive() ? e.getAsString() : GsonHelper.getAsString(e.getAsJsonObject(), "species"));
    }

    private static List<ResourceLocation> parentGenes(JsonElement e) {
        if (e.isJsonPrimitive() || !e.getAsJsonObject().has("genes")) return List.of();
        return ids(GsonHelper.getAsJsonArray(e.getAsJsonObject(), "genes"));
    }

    private static List<ResourceLocation> ids(JsonArray a) {
        List<ResourceLocation> out = new ArrayList<>();
        for (JsonElement e : a) out.add(new ResourceLocation(e.getAsString()));
        return out;
    }

    private static void loadGenes(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, Integer> out = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                String c = GsonHelper.getAsString(json.getAsJsonObject(), "color").replace("#", "");
                out.put(id, Integer.parseInt(c, 16) & 0xFFFFFF);
            } catch (Exception ex) {
                LOG.error("Bad gene file {}: {}", id, ex.getMessage());
            }
        });
        geneColors = out;
    }

    private static void loadFoods(Map<ResourceLocation, JsonElement> files) {
        Map<Item, Food> out = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                JsonObject o = json.getAsJsonObject();
                Item i = item(GsonHelper.getAsString(o, "item"));
                ResourceLocation hab = o.has("habitat") ? new ResourceLocation(GsonHelper.getAsString(o, "habitat")) : null;
                if (i != null) out.put(i, new Food(Math.max(1, GsonHelper.getAsInt(o, "ticks", 4800)), hab));
            } catch (Exception ex) {
                LOG.error("Bad fish food file {}: {}", id, ex.getMessage());
            }
        });
        foods = out;
    }

    private static void loadHabitats(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, Habitat> out = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                JsonObject o = json.getAsJsonObject();
                String look = GsonHelper.getAsString(o, "look", "water");
                if (!look.equals("water") && !look.equals("lava") && !look.equals("void")) look = "water";
                String c = GsonHelper.getAsString(o, "color", "#3F9FE4").replace("#", "");
                Item kit = o.has("kit") ? item(GsonHelper.getAsString(o, "kit")) : null;
                Item bucket = o.has("bucket") ? item(GsonHelper.getAsString(o, "bucket")) : null;
                Item empty = o.has("empty") ? item(GsonHelper.getAsString(o, "empty")) : null;
                if (bucket == null) bucket = net.minecraft.world.item.Items.WATER_BUCKET;
                if (empty == null) empty = net.minecraft.world.item.Items.BUCKET;
                out.put(id, new Habitat(id, look, Integer.parseInt(c, 16) & 0xFFFFFF, kit, bucket, empty));
            } catch (Exception ex) {
                LOG.error("Bad habitat file {}: {}", id, ex.getMessage());
            }
        });
        if (!out.containsKey(WATER)) out.put(WATER, DEFAULT_WATER);
        habitats = out;
    }
}

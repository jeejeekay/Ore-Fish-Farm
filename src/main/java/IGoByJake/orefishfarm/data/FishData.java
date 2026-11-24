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
import net.minecraft.util.Mth;
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
 *     "products": [ { "item": "orefishfarm:brass_scale", "count": 1, "weight": 6 },     optional: a weighted list;
 *                   { "item": "...", "weight": 2, "food": "orefishfarm:brine_flakes" },   only while the aquarium eats
 *                   { "item": "...", "weight": 2, "traits": ["orefishfarm:charged"] } ],  this (item or "#tag"), only
 *                                                 if the fish carries these genes. None met = the plain "product".
 *     "product_tag": "orefishfarm:bark_scales",   optional: shed a random item of this tag instead (product = the count
 *                                                 and what shows when the tag is empty)
 *     "product_chance": 0.75,   chance per bite (every biteTicks of eating) in a Fish Tank
 *     "breed_time": 180,        seconds of eating between this fish's breeding tries (optional; config default 180).
 *                               0 = never breeds, except where a breeding recipe names it.
 *     "power": 30,              FE/tick while eating in a power tank (optional; 0 = not a power fish)
 *     "heat": 0.6,              how much this fish warms its aquarium while it eats (optional; 0 = not a heat fish).
 *                               The aquarium adds up its eating heat fish (each times its Heat stat factor) per block:
 *                               0.25 = smouldering, 0.7 = kindled, 1.4 = seething (read by Create as burner heat).
 *     "habitat": "orefishfarm:water",  which tank it lives in (optional; water by default)
 *     "bucket": "somemod:fish_bucket", optional: this mod's own bucket for the fish (else the generic Fish Bucket)
 *     "scale": 2.0,               optional: the living mob (model and hitbox) is this many times bigger; tanks and screens unchanged
 *     "shear": { "item": "minecraft:string", "min": 1, "max": 3, "regrow": 6000 } }
 *                               optional: the living fish can be sheared like a sheep (only fish without a mob of
 *                               their own, i.e. drawn by Ore Fish Farm's data fish). Drops min..max of the item, then
 *                               shows {@code models/fish/<fish>_sheared.json} (and {@code _sheared_tail}, if there is one)
 *                               until the coat grows back after "regrow" ticks.
 *
 * data/&lt;ns&gt;/orefishfarm_breeding/&lt;name&gt;.json
 *   { "parents": ["orefishfarm:cuprum_eel", "orefishfarm:zincray"],
 *     "traits": ["orefishfarm:hot_blooded"],   optional: genes (traits) the baby must inherit
 *     "result": "orefishfarm:brass_bream",
 *     "chance": 0.1 }   optional: chance the result is born on a try (default: the config's hybridChance)
 *   A recipe: any baby of these two that inherited all the traits becomes the result (with no traits, every baby
 *   does). The two are then a valid pair whatever their breeding groups. Where several recipes fit, the one with
 *   the most traits wins. How often fish try is each fish's breed_time (see TankBlockEntity.breed).
 *   (Older files: genes on a parent and "requires" are read as traits too.)
 *
 * data/&lt;ns&gt;/tags/items/breeding/&lt;group&gt;.json   breeding groups (hidden from players; recipes are what JEI shows)
 *   Two fish of different species can breed when they share a breeding group tag (any namespace); the baby is one
 *   of the two species at random, 3 levels lower. Two of the same species can always breed (breed_chance above 0).
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
    /** How big each species is drawn (fish file "scale", default 1); synced to clients. */
    private static Map<Item, Float> scales = Map.of();
    /** Fish that shed a random item from a tag each time ("product_tag"), e.g. the Barkacuda's bark scales. */
    private static Map<Item, ResourceLocation> productTags = Map.of();
    private static Map<Item, ResourceLocation> pendingProductTags = Map.of();

    /**
     * One entry of a fish's "products" list: the item, its weight against the other entries, and what it needs: a
     * food the aquarium is eating (an item id, or "#tag") and genes the fish carries.
     */
    public record Drop(ItemStack item, int weight, @Nullable String food, List<ResourceLocation> traits) {
        public boolean fedBy(ItemStack eating) {
            if (food == null) return true;
            if (eating == null || eating.isEmpty()) return false;
            if (food.startsWith("#")) return eating.is(net.minecraft.tags.ItemTags.create(new ResourceLocation(food.substring(1))));
            return String.valueOf(ForgeRegistries.ITEMS.getKey(eating.getItem())).equals(food);
        }
    }

    private static Map<Item, List<Drop>> drops = Map.of();
    private static Map<Item, List<Drop>> pendingDrops = Map.of();

    /** This fish's "products" list, or empty if it has none. */
    public static List<Drop> drops(Fish f) {
        return drops.getOrDefault(f.item(), List.of());
    }

    /**
     * What this fish sheds this time. With a "products" list: a weighted pick among the entries whose food and genes
     * are met (none met = its plain product). Otherwise a random item of its product tag, or its product.
     */
    public static ItemStack productFor(Fish f, net.minecraft.util.RandomSource random, ItemStack fish, ItemStack eating) {
        List<Drop> list = drops(f);
        if (list.isEmpty()) return productFor(f, random);
        List<Drop> ok = new ArrayList<>();
        int total = 0;
        for (Drop d : list) {
            if (d.weight() > 0 && d.fedBy(eating) && FishGenes.hasAll(fish, d.traits())) { ok.add(d); total += d.weight(); }
        }
        if (ok.isEmpty()) return f.product().copy();
        int roll = random.nextInt(total);
        for (Drop d : ok) {
            roll -= d.weight();
            if (roll < 0) return d.item().copy();
        }
        return f.product().copy();
    }

    /** What this fish sheds this time: a random item from its product tag, or its product. */
    public static ItemStack productFor(Fish f, net.minecraft.util.RandomSource random) {
        List<ItemStack> all = productChoices(f);
        return all.isEmpty() ? f.product().copy() : all.get(random.nextInt(all.size())).copy();
    }

    /** Every item this fish can shed (the tag's items, or just its product). */
    public static List<ItemStack> productChoices(Fish f) {
        ResourceLocation tag = productTags.get(f.item());
        if (tag == null) return List.of(f.product().copy());
        List<ItemStack> out = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.ItemTags.create(tag))
                .forEach(h -> out.add(new ItemStack(h.value(), Math.max(1, f.product().getCount()))));
        return out.isEmpty() ? List.of(f.product().copy()) : out;
    }

    public static float scale(Item fish) {
        return scales.getOrDefault(fish, 1F);
    }

    /** What shearing a living fish drops, and how long its coat takes to grow back (server side only). */
    public record Shear(Item drop, int min, int max, int regrow) {}

    private static Map<Item, Shear> shears = Map.of();

    @Nullable
    public static Shear shear(ItemStack fish) {
        return fish.isEmpty() ? null : shears.get(fish.getItem());
    }

    public record Fish(Item item, ItemStack product, float productChance, float breedTime, int power, ResourceLocation habitat, @Nullable Item bucket, float heat) {
        public boolean makesPower() { return this.power > 0; }
        public boolean makesHeat() { return this.heat > 0; }
        public boolean makesProduct() { return !this.product.isEmpty() && this.productChance > 0; }
    }

    /**
     * A breeding recipe: a baby of these two species that inherited every one of the traits becomes the result.
     * With no traits, every baby of the pair does. The recipe also makes the two a valid breeding pair. chance: the
     * chance the result is born (below 0 = the config's hybridChance).
     */
    public record Cross(Item a, Item b, ItemStack result, List<ResourceLocation> traits, float chance) {
        boolean matches(Item x, Item y) { return (x == a && y == b) || (x == b && y == a); }
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

    /** Ticks of eating between this fish's breeding tries (0 = it never tries). */
    public static int breedTicks(Fish f) {
        return f.breedTime() <= 0 ? 0 : Math.max(1, (int) Math.round(f.breedTime() * 20 * Config.BREED_TIME.get()));
    }

    public static boolean canBreed(Fish f) { return f.breedTime() > 0; }

    /** The chance a baby is born, by what it is: one species (both parents and baby), a mix of two, or a recipe fish. */
    public static float sameSpeciesChance() { return Config.SAME_SPECIES_CHANCE.get().floatValue(); }
    public static float crossChance() { return Config.CROSS_CHANCE.get().floatValue(); }
    public static float hybridChance(Cross c) { return c.chance() >= 0 ? c.chance() : Config.HYBRID_CHANCE.get().floatValue(); }

    /**
     * Whether these two fish can breed with each other: a breeding recipe names them, or they're the same species, or
     * they share a breeding group tag (#*:breeding/*). Without a recipe, a fish with breed_time 0 can't breed at all.
     */
    public static boolean canPair(Fish fx, Fish fy) {
        if (cross(fx.item(), fy.item()) != null) return true;
        if (!canBreed(fx) || !canBreed(fy)) return false;
        if (fx.item() == fy.item()) return true;
        java.util.Set<ResourceLocation> gx = breedingGroups(fx.item());
        for (ResourceLocation g : breedingGroups(fy.item())) if (gx.contains(g)) return true;
        return false;
    }

    /** The breeding group tags (any namespace, path breeding/...) this fish is in. */
    public static java.util.Set<ResourceLocation> breedingGroups(Item item) {
        java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
        net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(item).tags().forEach(t -> {
            if (t.location().getPath().startsWith("breeding/")) out.add(t.location());
        });
        return out;
    }

    /** The hybrid rules for these two species (either way round). */
    public static List<Cross> hybridsFor(Item x, Item y) {
        List<Cross> out = new ArrayList<>();
        for (Cross c : crosses) if (c.matches(x, y)) out.add(c);
        return out;
    }

    public static java.util.Collection<Fish> allFish() {
        return fish.values();
    }

    /**
     * Fish Buckets for the fish that have neither their own bucket nor their own mob (data-pack fish such as
     * Quicreate's glass eel), sorted by id, so the creative tab and JEI can list them next to the built-in ones.
     */
    public static List<ItemStack> extraBuckets() {
        java.util.Set<ResourceLocation> mobs = new java.util.HashSet<>();
        for (var m : IGoByJake.orefishfarm.Registry.FISH_MOBS) mobs.add(m.getId());
        List<ItemStack> out = new java.util.ArrayList<>();
        fish.values().stream()
                .filter(f -> f.bucket() == null)
                .map(Fish::item)
                .filter(i -> { ResourceLocation id = ForgeRegistries.ITEMS.getKey(i); return id != null && !mobs.contains(id); })
                .sorted(java.util.Comparator.comparing(i -> String.valueOf(ForgeRegistries.ITEMS.getKey(i))))
                .forEach(i -> out.add(IGoByJake.orefishfarm.FishBucketItem.of(i)));
        return out;
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
            buf.writeFloat(f.breedTime());
            buf.writeVarInt(powerPerTick(f));
            buf.writeResourceLocation(f.habitat());
            buf.writeBoolean(f.bucket() != null);
            if (f.bucket() != null) buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(f.bucket()));
            buf.writeFloat(f.heat());
            buf.writeFloat(scale(f.item()));
            ResourceLocation pt = productTags.get(f.item());
            buf.writeUtf(pt == null ? "" : pt.toString());
            List<Drop> dl = drops.getOrDefault(f.item(), List.of());
            buf.writeVarInt(dl.size());
            for (Drop d : dl) {
                buf.writeItem(d.item());
                buf.writeVarInt(d.weight());
                buf.writeUtf(d.food() == null ? "" : d.food());
                writeIds(buf, d.traits());
            }
        }
        buf.writeVarInt(crosses.size());
        for (Cross c : crosses) {
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(c.a()));
            buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(c.b()));
            buf.writeItem(c.result());
            writeIds(buf, c.traits());
            buf.writeFloat(c.chance());
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
                         Map<ResourceLocation, Integer> geneColors, Map<Item, Float> scales) {}

    public static Synced read(net.minecraft.network.FriendlyByteBuf buf) {
        Map<Item, Fish> f = new HashMap<>();
        Map<Item, Float> sc = new HashMap<>();
        Map<Item, ResourceLocation> tagsIn = new HashMap<>();
        Map<Item, List<Drop>> dropsIn = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item item = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            ItemStack product = buf.readItem();
            float pc = buf.readFloat(), bc = buf.readFloat();
            int pw = buf.readVarInt();
            ResourceLocation hab = buf.readResourceLocation();
            Item bucket = buf.readBoolean() ? ForgeRegistries.ITEMS.getValue(buf.readResourceLocation()) : null;
            float heat = buf.readFloat();
            float size = buf.readFloat();
            String pt = buf.readUtf();
            if (item != null && !pt.isEmpty()) tagsIn.put(item, new ResourceLocation(pt));
            int nd = buf.readVarInt();
            List<Drop> dl = new ArrayList<>();
            for (int k = 0; k < nd; k++) {
                ItemStack di = buf.readItem();
                int w = buf.readVarInt();
                String food = buf.readUtf();
                dl.add(new Drop(di, w, food.isEmpty() ? null : food, readIds(buf)));
            }
            if (item != null && !dl.isEmpty()) dropsIn.put(item, dl);
            Fish fish = new Fish(item, product, pc, bc, pw, hab, bucket, heat);
            if (item != null) {
                f.put(item, fish);
                if (size != 1F) sc.put(item, size);
            }
        }
        List<Cross> x = new ArrayList<>();
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Item a = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            Item b = ForgeRegistries.ITEMS.getValue(buf.readResourceLocation());
            ItemStack r = buf.readItem();
            List<ResourceLocation> t = readIds(buf);
            float ch = buf.readFloat();
            if (a != null && b != null) x.add(new Cross(a, b, r, t, ch));
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
        pendingProductTags = tagsIn;
        pendingDrops = dropsIn;
        return new Synced(f, x, food, hab, gc, sc);
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
        scales = s.scales();
        productTags = pendingProductTags;
        drops = pendingDrops;
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
        Item own = speciesOfBucket(stack.getItem());
        if (own != null) {
            Fish o = byItem(own);
            if (o != null) return o.bucket() != null ? o : new Fish(o.item(), o.product(), o.productChance(), o.breedTime(), o.power(), o.habitat(), stack.getItem(), o.heat());
        }
        // Any other mob bucket (cod, axolotl, another mod's fish): a plain water fish with no product.
        if (stack.getItem() instanceof net.minecraft.world.item.MobBucketItem) {
            return new Fish(stack.getItem(), ItemStack.EMPTY, 0, Config.DEFAULT_BREED_TIME.get().floatValue(), 0, WATER, stack.getItem(), 0);
        }
        return null;
    }

    @Nullable
    private static Fish byItem(Item item) {
        Fish f = fish.get(item);
        if (f != null) return f;
        if (new ItemStack(item).is(TANK_FISH)) return new Fish(item, ItemStack.EMPTY, 0, Config.DEFAULT_BREED_TIME.get().floatValue(), 0, WATER, vanillaBucket(item), 0);
        return null;
    }

    /**
     * The species' own vanilla-style mob bucket, {@code <ns>:<fish>_bucket} (minecraft:cod gives minecraft:cod_bucket),
     * or null. Fish without a bucket of their own in their fish file are caught, bred and drawn in it.
     */
    @Nullable
    public static Item vanillaBucket(Item species) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(species);
        if (id == null) return null;
        Item b = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(id.getNamespace(), id.getPath() + "_bucket"));
        return b instanceof net.minecraft.world.item.MobBucketItem && !(b instanceof IGoByJake.orefishfarm.FishBucketItem) ? b : null;
    }

    /** The species whose own bucket this is (minecraft:cod_bucket gives minecraft:cod), or null. */
    @Nullable
    private static Item speciesOfBucket(Item bucket) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(bucket);
        if (id == null || !id.getPath().endsWith("_bucket")) return null;
        Item sp = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 7)));
        return sp != null && sp != net.minecraft.world.item.Items.AIR && vanillaBucket(sp) == bucket ? sp : null;
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
        if (b != null) return new ItemStack(b.item());
        Item own = speciesOfBucket(stack.getItem());
        return own != null ? new ItemStack(own) : stack;
    }

    /** A live fish of this kind in its bucket: the fish's own mod bucket if it has one, else a Fish Bucket. */
    public static ItemStack bucketOf(Fish f) {
        if (f.bucket() != null) return new ItemStack(f.bucket());
        Item own = vanillaBucket(f.item());
        return own != null ? new ItemStack(own) : IGoByJake.orefishfarm.FishBucketItem.of(f.item());
    }

    /** A plain fish item from before buckets (1.x and 2.0 tanks) becomes a bucketed fish. */
    public static ItemStack wrapLegacy(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof IGoByJake.orefishfarm.FishBucketItem) return stack;
        if (stack.getItem() instanceof IGoByJake.orefishfarm.FishItem) return IGoByJake.orefishfarm.FishBucketItem.of(stack.getItem());
        return stack;
    }

    /** The container that scoops this fish out of a tank: its habitat's bucket (water, lava or void bucket). */
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
        Map<Item, Shear> shearOut = new HashMap<>();
        Map<Item, Float> scaleOut = new HashMap<>();
        Map<Item, ResourceLocation> tagOut = new HashMap<>();
        Map<Item, List<Drop>> dropOut = new HashMap<>();
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
                        breedTime(o), GsonHelper.getAsInt(o, "power", 0), hab, bucket,
                        GsonHelper.getAsFloat(o, "heat", 0F)));
                if (o.has("scale")) scaleOut.put(f, Mth.clamp(GsonHelper.getAsFloat(o, "scale"), 0.25F, 4F));
                if (o.has("product_tag")) tagOut.put(f, new ResourceLocation(GsonHelper.getAsString(o, "product_tag")));
                if (o.has("products")) {
                    List<Drop> dl = new ArrayList<>();
                    for (JsonElement e : GsonHelper.getAsJsonArray(o, "products")) {
                        JsonObject d = e.getAsJsonObject();
                        ItemStack di = stack(d);
                        if (di == null) continue; // an item from a mod that isn't installed
                        dl.add(new Drop(di, Math.max(0, GsonHelper.getAsInt(d, "weight", 1)),
                                d.has("food") ? GsonHelper.getAsString(d, "food") : null,
                                d.has("traits") ? ids(GsonHelper.getAsJsonArray(d, "traits")) : List.of()));
                    }
                    if (!dl.isEmpty()) dropOut.put(f, dl);
                }
                if (o.has("shear")) {
                    JsonObject sh = GsonHelper.getAsJsonObject(o, "shear");
                    Item drop = item(GsonHelper.getAsString(sh, "item"));
                    int min = Math.max(0, GsonHelper.getAsInt(sh, "min", 1));
                    if (drop != null) shearOut.put(f, new Shear(drop, min, Math.max(min, GsonHelper.getAsInt(sh, "max", min)),
                            Math.max(20, GsonHelper.getAsInt(sh, "regrow", 6000))));
                }
            } catch (Exception ex) {
                LOG.error("Bad fish file {}: {}", id, ex.getMessage());
            }
        });
        fish = out;
        shears = shearOut;
        scales = scaleOut;
        productTags = tagOut;
        drops = dropOut;
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
                JsonElement re = o.get("result");
                ItemStack r = re != null && re.isJsonPrimitive() ? stackOf(re.getAsString()) : stack(re);
                if (a == null || b == null || r == null) { LOG.warn("Breeding file {} names a fish that isn't there; skipped", id); return; }
                java.util.LinkedHashSet<ResourceLocation> traits = new java.util.LinkedHashSet<>();
                if (o.has("traits")) traits.addAll(ids(GsonHelper.getAsJsonArray(o, "traits")));
                if (o.has("requires")) traits.addAll(ids(GsonHelper.getAsJsonArray(o, "requires")));
                traits.addAll(parentGenes(p.get(0)));
                traits.addAll(parentGenes(p.get(1)));
                out.add(new Cross(a, b, r, new ArrayList<>(traits), GsonHelper.getAsFloat(o, "chance", -1F)));
            } catch (Exception ex) {
                LOG.error("Bad breeding file {}: {}", id, ex.getMessage());
            }
        });
        out.sort(java.util.Comparator.comparingInt((Cross c) -> c.traits().size()).reversed());
        crosses = out;
        LOG.info("Ore Fish Farm: {} breeding recipes", out.size());
    }

    /** breed_time in seconds; an old file's breed_chance 0 still means "never breeds". */
    private static float breedTime(JsonObject o) {
        if (o.has("breed_time")) return Math.max(0, GsonHelper.getAsFloat(o, "breed_time"));
        if (o.has("breed_chance") && GsonHelper.getAsFloat(o, "breed_chance") <= 0) return 0;
        return Config.DEFAULT_BREED_TIME.get();
    }

    @Nullable
    private static ItemStack stackOf(String id) {
        Item i = item(id);
        return i == null ? null : new ItemStack(i);
    }

    @Nullable
    private static Item parentItem(JsonElement e) {
        if (e.isJsonPrimitive()) return item(e.getAsString());
        JsonObject o = e.getAsJsonObject();
        return item(GsonHelper.getAsString(o, o.has("fish") ? "fish" : "species"));
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

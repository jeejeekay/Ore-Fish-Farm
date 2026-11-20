package IGoByJake.orefishfarm.data;

import IGoByJake.orefishfarm.OreFishFarm;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe ingredient {@code orefishfarm:fish}: a live fish (in a bucket), optionally of a species, with genes.
 * Works anywhere a recipe reads a normal ingredient (crafting, Create, other mods). Every field but "type" is optional:
 * <pre>
 * { "type": "orefishfarm:fish",
 *   "species": "orefishfarm:ferrofin"  or  "#orefishfarm:ore_fish",   (any fish when left out)
 *   "genes": ["orefishfarm:mutagenic"],      the fish must have all of these
 *   "none_of": ["somemod:gene"],             and none of these
 *   "habitat": "orefishfarm:heated" }        and live in this habitat
 * </pre>
 * Crafting gives the empty bucket back. Item tags can't see a fish's genes, which is why this exists.
 */
public class FishIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = new ResourceLocation(OreFishFarm.MODID, "fish");

    @Nullable private final Item species;
    @Nullable private final TagKey<Item> speciesTag;
    private final List<ResourceLocation> genes, noneOf;
    @Nullable private final ResourceLocation habitat;

    public FishIngredient(@Nullable Item species, @Nullable TagKey<Item> speciesTag, List<ResourceLocation> genes, List<ResourceLocation> noneOf,
                          @Nullable ResourceLocation habitat) {
        this.species = species;
        this.speciesTag = speciesTag;
        this.genes = genes;
        this.noneOf = noneOf;
        this.habitat = habitat;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || !FishData.isBucketed(stack)) return false;
        FishData.Fish f = FishData.fish(stack);
        if (f == null) return false;
        if (this.species != null && f.item() != this.species) return false;
        if (this.speciesTag != null && !new ItemStack(f.item()).is(this.speciesTag)) return false;
        if (this.habitat != null && !this.habitat.equals(f.habitat())) return false;
        if (!FishGenes.hasAll(stack, this.genes)) return false;
        for (ResourceLocation g : this.noneOf) if (FishGenes.hasGene(stack, g)) return false;
        return true;
    }

    /** What JEI and recipe books show: each matching species, in a bucket, carrying the genes asked for. */
    @Override
    public ItemStack[] getItems() {
        List<ItemStack> out = new ArrayList<>();
        for (FishData.Fish f : FishData.allFish()) {
            if (this.species != null && f.item() != this.species) continue;
            if (this.speciesTag != null && !new ItemStack(f.item()).is(this.speciesTag)) continue;
            if (this.habitat != null && !this.habitat.equals(f.habitat())) continue;
            ItemStack s = FishData.bucketOf(f);
            if (!this.genes.isEmpty()) {
                ListTag l = new ListTag();
                for (ResourceLocation g : this.genes) l.add(StringTag.valueOf(g.toString()));
                s.getOrCreateTagElement(FishGenes.KEY).put("Genes", l);
            }
            out.add(s);
        }
        return out.toArray(ItemStack[]::new);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends net.minecraft.world.item.crafting.Ingredient> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", ID.toString());
        if (this.species != null) o.addProperty("species", String.valueOf(ForgeRegistries.ITEMS.getKey(this.species)));
        if (this.speciesTag != null) o.addProperty("species", "#" + this.speciesTag.location());
        if (!this.genes.isEmpty()) o.add("genes", array(this.genes));
        if (!this.noneOf.isEmpty()) o.add("none_of", array(this.noneOf));
        if (this.habitat != null) o.addProperty("habitat", this.habitat.toString());
        return o;
    }

    private static JsonArray array(List<ResourceLocation> ids) {
        JsonArray a = new JsonArray();
        ids.forEach(id -> a.add(id.toString()));
        return a;
    }

    private static List<ResourceLocation> ids(JsonObject o, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : GsonHelper.getAsJsonArray(o, key)) out.add(new ResourceLocation(e.getAsString()));
        return out;
    }

    public static final IIngredientSerializer<FishIngredient> SERIALIZER = new IIngredientSerializer<>() {
        @Override
        public FishIngredient parse(JsonObject o) {
            Item species = null;
            TagKey<Item> tag = null;
            if (o.has("species")) {
                String s = GsonHelper.getAsString(o, "species");
                if (s.startsWith("#")) tag = TagKey.create(Registries.ITEM, new ResourceLocation(s.substring(1)));
                else species = ForgeRegistries.ITEMS.getValue(new ResourceLocation(s));
            }
            ResourceLocation hab = o.has("habitat") ? new ResourceLocation(GsonHelper.getAsString(o, "habitat")) : null;
            return new FishIngredient(species, tag, ids(o, "genes"), ids(o, "none_of"), hab);
        }

        @Override
        public FishIngredient parse(FriendlyByteBuf buf) {
            Item species = buf.readBoolean() ? ForgeRegistries.ITEMS.getValue(buf.readResourceLocation()) : null;
            TagKey<Item> tag = buf.readBoolean() ? TagKey.create(Registries.ITEM, buf.readResourceLocation()) : null;
            List<ResourceLocation> g = buf.readList(FriendlyByteBuf::readResourceLocation);
            List<ResourceLocation> n = buf.readList(FriendlyByteBuf::readResourceLocation);
            ResourceLocation hab = buf.readBoolean() ? buf.readResourceLocation() : null;
            return new FishIngredient(species, tag, g, n, hab);
        }

        @Override
        public void write(FriendlyByteBuf buf, FishIngredient i) {
            buf.writeBoolean(i.species != null);
            if (i.species != null) buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(i.species));
            buf.writeBoolean(i.speciesTag != null);
            if (i.speciesTag != null) buf.writeResourceLocation(i.speciesTag.location());
            buf.writeCollection(i.genes, FriendlyByteBuf::writeResourceLocation);
            buf.writeCollection(i.noneOf, FriendlyByteBuf::writeResourceLocation);
            buf.writeBoolean(i.habitat != null);
            if (i.habitat != null) buf.writeResourceLocation(i.habitat);
        }
    };
}

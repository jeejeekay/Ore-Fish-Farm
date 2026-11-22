package IGoByJake.orefishfarm.data;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What one live fish carries, stored on its bucket under {@value #KEY}:
 * <ul>
 *   <li><b>Stats</b>: Yield, Hunger and Charge, each 0 to 1 (0.5 = the species' normal). They change how the fish
 *   works: Yield scales its product chance, Hunger how fast it eats its share of the food, Charge its FE (power
 *   fish only). Each factor is 0.5 + stat, so 0.5x to 1.5x. New stats are code.</li>
 *   <li><b>Genes</b>: up to {@value #MAX_GENES} names, plain data with no effect. Breeding entries and recipes can
 *   ask for them. A gene is any item tag in a {@code genes/} folder ({@code data/<ns>/tags/items/genes/<gene>.json}),
 *   listing the species born with it; the gene's id is {@code <ns>:<gene>}. Colour: an optional
 *   {@code orefishfarm_gene/<gene>.json} ({"color": "#C040C0"}), else one picked from the name. Name: lang key
 *   {@code gene.<ns>.<gene>}.</li>
 * </ul>
 * A fish gets its stats and genes when it is bucketed (caught alive, scooped, or put in a tank without them);
 * a fish bred in a tank gets them at birth from its parents.
 *
 * <p>Newborns: a fish bred in a tank is flagged {@code Newborn} until someone scoops it out with its habitat's
 * bucket, and {@code Unseen} (the screen shows an asterisk) until a player hovers it.
 */
public final class FishGenes {
    public static final String KEY = "OreFishGenes";
    public static final int MAX_GENES = 3;
    public static final String YIELD = "yield", HUNGER = "hunger", CHARGE = "charge", HEAT = "heat";
    public static final List<String> STATS = List.of(YIELD, HUNGER, CHARGE, HEAT);
    private static final String GENE_FOLDER = "genes/";

    private FishGenes() {}

    // ---------------------------------------------------------------- reading

    @Nullable
    private static CompoundTag data(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(KEY, Tag.TAG_COMPOUND) ? t.getCompound(KEY) : null;
    }

    /** Whether this fish has had its stats and genes given yet. */
    public static boolean has(ItemStack stack) {
        return data(stack) != null;
    }

    /** A stat, 0 to 1 (0.5 when not set). */
    public static float stat(ItemStack stack, String stat) {
        CompoundTag d = data(stack);
        if (d == null || !d.getCompound("Stats").contains(stat)) return 0.5F;
        return Mth.clamp(d.getCompound("Stats").getFloat(stat), 0F, 1F);
    }

    /** A stat as a multiplier: 0.5x at 0, 1x at 0.5, 1.5x at 1. */
    public static float factor(ItemStack stack, String stat) {
        return 0.5F + stat(stack, stat);
    }

    /** The fish's genes, oldest first. */
    public static List<ResourceLocation> genes(ItemStack stack) {
        CompoundTag d = data(stack);
        List<ResourceLocation> out = new ArrayList<>();
        if (d == null) return out;
        ListTag l = d.getList("Genes", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(l.getString(i));
            if (id != null) out.add(id);
        }
        return out;
    }

    public static boolean hasGene(ItemStack stack, ResourceLocation gene) {
        return genes(stack).contains(gene);
    }

    public static boolean hasAll(ItemStack stack, List<ResourceLocation> genes) {
        if (genes.isEmpty()) return true;
        List<ResourceLocation> mine = genes(stack);
        return mine.containsAll(genes);
    }

    public static boolean isNewborn(ItemStack stack) {
        CompoundTag d = data(stack);
        return d != null && d.getBoolean("Newborn");
    }

    public static boolean isUnseen(ItemStack stack) {
        CompoundTag d = data(stack);
        return d != null && d.getBoolean("Unseen");
    }

    // ---------------------------------------------------------------- writing

    private static CompoundTag write(ItemStack stack) {
        CompoundTag t = stack.getOrCreateTag();
        if (!t.contains(KEY, Tag.TAG_COMPOUND)) t.put(KEY, new CompoundTag());
        return t.getCompound(KEY);
    }

    private static void set(ItemStack stack, float[] stats, List<ResourceLocation> genes) {
        CompoundTag d = write(stack);
        CompoundTag s = new CompoundTag();
        for (int i = 0; i < STATS.size(); i++) s.putFloat(STATS.get(i), Mth.clamp(stats[i], 0F, 1F));
        d.put("Stats", s);
        ListTag l = new ListTag();
        for (ResourceLocation g : genes) l.add(StringTag.valueOf(g.toString()));
        d.put("Genes", l);
    }

    /** Scooped out of a tank: no longer a newborn. */
    public static void clearNewborn(ItemStack stack) {
        CompoundTag d = data(stack);
        if (d == null) return;
        d.remove("Newborn");
        d.remove("Unseen");
    }

    public static void markSeen(ItemStack stack) {
        CompoundTag d = data(stack);
        if (d != null) d.remove("Unseen");
    }

    /** Gives a fish its stats (rolled around 0.5) and its species' genes, if it has none yet. */
    public static void ensure(ItemStack stack, RandomSource random) {
        if (stack.isEmpty() || has(stack)) return;
        Item species = species(stack);
        if (species == null) return;
        float[] stats = new float[STATS.size()];
        for (int i = 0; i < stats.length; i++) stats[i] = 0.5F + (random.nextFloat() - 0.5F) * 0.3F;
        set(stack, stats, speciesGenes(species));
    }

    /**
     * A baby's stats and genes, written onto its bucket. Stats: each from one parent at random, nudged a little.
     * Genes: its species' own, then the parents' in turn (A's first, B's first, A's second, ...), each at 50%,
     * skipping ones it has, stopping once it has {@value #MAX_GENES}.
     */
    public static void inherit(ItemStack baby, ItemStack a, ItemStack b, RandomSource random) {
        float[] stats = new float[STATS.size()];
        for (int i = 0; i < stats.length; i++) {
            float v = stat(random.nextBoolean() ? a : b, STATS.get(i));
            float r = random.nextFloat();
            float shift = r < 0.7F ? 0 : r < 0.95F ? 0.05F : 0.1F;
            stats[i] = v + (random.nextBoolean() ? shift : -shift);
        }
        Item species = species(baby);
        List<ResourceLocation> genes = new ArrayList<>(species == null ? List.of() : speciesGenes(species));
        List<ResourceLocation> ga = genes(a), gb = genes(b);
        for (int i = 0; i < Math.max(ga.size(), gb.size()) && genes.size() < MAX_GENES; i++) {
            for (List<ResourceLocation> from : List.of(ga, gb)) {
                if (genes.size() >= MAX_GENES || i >= from.size()) continue;
                ResourceLocation g = from.get(i);
                if (!genes.contains(g) && random.nextBoolean()) genes.add(g);
            }
        }
        while (genes.size() > MAX_GENES) genes.remove(0);
        set(baby, stats, genes);
        CompoundTag d = write(baby);
        d.putBoolean("Newborn", true);
        d.putBoolean("Unseen", true);
    }

    // ---------------------------------------------------------------- genes from tags

    /** The genes a species is born with: every {@code genes/} item tag it is in, at most {@value #MAX_GENES}. */
    public static List<ResourceLocation> speciesGenes(Item species) {
        List<ResourceLocation> out = new ArrayList<>();
        BuiltInRegistries.ITEM.wrapAsHolder(species).tags().forEach(tag -> {
            ResourceLocation id = tag.location();
            if (id.getPath().startsWith(GENE_FOLDER)) out.add(new ResourceLocation(id.getNamespace(), id.getPath().substring(GENE_FOLDER.length())));
        });
        out.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        while (out.size() > MAX_GENES) out.remove(out.size() - 1);
        return out;
    }

    /** Every gene that exists: all {@code genes/} item tags. */
    public static List<ResourceLocation> allGenes() {
        List<ResourceLocation> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagNames().forEach(tag -> {
            ResourceLocation id = tag.location();
            if (id.getPath().startsWith(GENE_FOLDER)) out.add(new ResourceLocation(id.getNamespace(), id.getPath().substring(GENE_FOLDER.length())));
        });
        out.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        return out;
    }

    @Nullable
    private static Item species(ItemStack stack) {
        FishData.Fish f = FishData.fish(stack);
        return f == null ? null : f.item();
    }

    // ---------------------------------------------------------------- display

    public static int color(ResourceLocation gene) {
        Integer c = FishData.geneColor(gene);
        if (c != null) return c;
        float hue = (gene.toString().hashCode() & 0xFFFF) / 65535F;
        return Mth.hsvToRgb(hue, 0.55F, 1F);
    }

    public static MutableComponent geneName(ResourceLocation gene) {
        return Component.translatableWithFallback("gene." + gene.getNamespace() + "." + gene.getPath(), gene.getPath())
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color(gene))));
    }

    public static int statColor(String stat) {
        return switch (stat) {
            case YIELD -> 0xE8B830;
            case HUNGER -> 0x6BBF4A;
            case HEAT -> 0xF0702A;
            default -> 0x3FD0E0;
        };
    }

    /** "Yield ▮▮▮▮▮▯▯▯▯▯": a 10-step bar. */
    public static Component statLine(ItemStack stack, String stat) {
        int filled = Math.round(stat(stack, stat) * 10);
        MutableComponent line = Component.translatable("stat.orefishfarm." + stat).withStyle(ChatFormatting.GRAY).append(" ");
        line.append(Component.literal("▮".repeat(filled)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(statColor(stat)))));
        line.append(Component.literal("▮".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
        return line;
    }

    /** Tooltip lines for a live fish (nothing for one that hasn't been bucketed yet). */
    public static void tooltip(ItemStack stack, List<Component> lines, boolean power) {
        tooltip(stack, lines, power, false);
    }

    /** As above; heat fish also show their Heat stat. */
    public static void tooltip(ItemStack stack, List<Component> lines, boolean power, boolean heat) {
        if (!has(stack)) return;
        if (isNewborn(stack)) lines.add(Component.translatable("tooltip.orefishfarm.newborn").withStyle(ChatFormatting.YELLOW));
        lines.add(statLine(stack, YIELD));
        lines.add(statLine(stack, HUNGER));
        if (power) lines.add(statLine(stack, CHARGE));
        if (heat) lines.add(statLine(stack, HEAT));
        List<ResourceLocation> genes = genes(stack);
        if (!genes.isEmpty()) {
            MutableComponent g = Component.translatable("tooltip.orefishfarm.genes").withStyle(ChatFormatting.GRAY).append(" ");
            for (int i = 0; i < genes.size(); i++) {
                if (i > 0) g.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                g.append(geneName(genes.get(i)));
            }
            lines.add(g);
        }
    }
}

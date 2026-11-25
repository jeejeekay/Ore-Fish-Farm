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
import IGoByJake.orefishfarm.Config;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What one live fish carries, stored on its bucket under {@value #KEY}:
 * <ul>
 *   <li><b>Stats</b>: Yield, Satiety, Charge and Heat. Each has a <b>level</b> and a <b>potential</b>, both whole
 *   numbers 0 to 10, all shown in the tooltip (nothing hidden). Only the level changes how the fish works: Yield
 *   scales its product, Satiety how long food lasts for it, Charge its FE (power fish), Heat its heat (heat fish).
 *   The multiplier for each level is {@code statMultipliers} in the config (level 3 = 1x by default, +25% a level,
 *   10 = 3x). Potential is the highest level a baby can reach in that stat.</li>
 *   <li><b>Wild fish</b> (caught, scooped, or put in a tank without stats) roll each level from
 *   {@code wildLevelWeights} (each level a third as likely as the next one towards 3), and each potential as
 *   round(base - perLevel x level +- spread): low fish tend to have high potential.</li>
 *   <li><b>Babies</b>, for each stat: potential = {@link #roll} of the parents' two potentials added, then
 *   level = {@link #roll} of the parents' two levels added, at most that potential. A roll of a total t makes
 *   t + breedExtraRolls tries, each won at breedRollChance + breedRollChancePerLevel x t; the result is the
 *   number of wins, at most 10.</li>
 *   <li><b>Genes</b>: up to {@value #MAX_GENES} names, plain data with no effect. Breeding entries and recipes can
 *   ask for them. A gene is any item tag in a {@code genes/} folder ({@code data/<ns>/tags/items/genes/<gene>.json}),
 *   listing the species born with it; the gene's id is {@code <ns>:<gene>}. Colour: an optional
 *   {@code orefishfarm_gene/<gene>.json} ({"color": "#C040C0"}), else one picked from the name. Name: lang key
 *   {@code gene.<ns>.<gene>}.</li>
 * </ul>
 * Fish from before 2.8.0 carry 0-1 "Stats"; they read as the nearest level and are rewritten (with a wild
 * potential) the next time they are bucketed or put in a tank.
 *
 * <p>Newborns: a fish bred in a tank is flagged {@code Newborn} until someone scoops it out with its habitat's
 * bucket, and {@code Unseen} (the screen shows an asterisk) until a player hovers it.
 */
public final class FishGenes {
    public static final String KEY = "OreFishGenes";
    public static final int MAX_GENES = 3;
    /** Levels a cross-species baby loses in every stat. */
    public static final int CROSS_PENALTY = 3;
    /** How often a parent's gene passes to a baby of its own species, and to a baby of the other species. */
    public static final float SAME_SPECIES_PASS = 0.5F, CROSS_SPECIES_PASS = 0.25F;

    /** Copies a fish's stats and genes (and newborn flags) onto another fish, e.g. a baby that turned out a hybrid. */
    public static void copyData(ItemStack from, ItemStack to) {
        CompoundTag d = from.getTag() == null ? null : from.getTag().getCompound(KEY);
        if (d != null && !d.isEmpty()) to.getOrCreateTag().put(KEY, d.copy());
    }

    /** A display copy of a fish carrying these genes (JEI shows what a parent needs). */
    public static ItemStack withGenes(ItemStack stack, List<ResourceLocation> genes) {
        ItemStack s = stack.copy();
        if (!genes.isEmpty()) setGenes(s, new ArrayList<>(genes));
        return s;
    }
    public static final int MAX_LEVEL = 10;
    public static final String YIELD = "yield", SATIETY = "satiety", CHARGE = "charge", HEAT = "heat", HAUL = "haul";
    /** Haul (2.23.0): how much a hauling fish carries per trip in Pipe Tanks. Fish from before it read as level 3. */
    public static final List<String> STATS = List.of(YIELD, SATIETY, CHARGE, HEAT, HAUL);
    private static final String GENE_FOLDER = "genes/";
    private static final String LEVELS = "Levels", POTENTIAL = "Potential", OLD_STATS = "Stats";

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

    /** A stat's level, 0 to 10 (3 when not set). */
    public static int level(ItemStack stack, String stat) {
        CompoundTag d = data(stack);
        if (d == null) return 3;
        if (d.getCompound(LEVELS).contains(stat)) return Mth.clamp(d.getCompound(LEVELS).getInt(stat), 0, MAX_LEVEL);
        return oldLevel(d, stat);
    }

    /** A stat's potential, 0 to 10: the highest level its babies can have in it. */
    public static int potential(ItemStack stack, String stat) {
        CompoundTag d = data(stack);
        if (d != null && d.getCompound(POTENTIAL).contains(stat)) return Mth.clamp(d.getCompound(POTENTIAL).getInt(stat), 0, MAX_LEVEL);
        return wildPotential(level(stack, stat), 0);
    }

    /** A stat as a multiplier: the config's statMultipliers entry for its level. */
    public static float factor(ItemStack stack, String stat) {
        return (float) Config.statMultiplier(level(stack, stat));
    }

    /** A pre-2.8.0 0-1 stat as the level whose multiplier is closest to its old 0.5x-1.5x factor. */
    private static int oldLevel(CompoundTag d, String stat) {
        CompoundTag old = d.getCompound(OLD_STATS);
        String key = stat.equals(SATIETY) ? "hunger" : stat;
        if (!old.contains(key)) return 3;
        double f = 0.5 + Mth.clamp(old.getFloat(key), 0F, 1F);
        if (stat.equals(SATIETY)) f = 1 / f; // old Hunger: more ate more; Satiety: more eats less
        int best = 3;
        for (int l = 0; l <= MAX_LEVEL; l++) if (Math.abs(Config.statMultiplier(l) - f) < Math.abs(Config.statMultiplier(best) - f)) best = l;
        return best;
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

    // ---------------------------------------------------------------- genes with effects (2.28.0)
    /** Hot Blooded: an aquarium with an eating heat fish that carries it heats one Create level higher (at most seething). */
    public static final ResourceLocation HOT_BLOODED = new ResourceLocation("orefishfarm", "hot_blooded");
    /** Hardy: lives in a tank of any habitat without being hurt, and needs no habitat food there. */
    public static final ResourceLocation HARDY = new ResourceLocation("orefishfarm", "hardy");
    /** Charged: a power fish makes {@value #CHARGED_POWER}x its FE. */
    public static final ResourceLocation CHARGED = new ResourceLocation("orefishfarm", "charged");
    public static final float CHARGED_POWER = 1.25F;

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

    private static void setStats(ItemStack stack, int[] levels, int[] potential) {
        CompoundTag d = write(stack);
        CompoundTag l = new CompoundTag(), p = new CompoundTag();
        for (int i = 0; i < STATS.size(); i++) {
            l.putInt(STATS.get(i), Mth.clamp(levels[i], 0, MAX_LEVEL));
            p.putInt(STATS.get(i), Mth.clamp(potential[i], 0, MAX_LEVEL));
        }
        d.put(LEVELS, l);
        d.put(POTENTIAL, p);
        d.remove(OLD_STATS);
    }

    /** Adds genes a baby is always born with (a breeding file's result_genes), up to {@value #MAX_GENES}. */
    public static void addGenes(ItemStack stack, List<ResourceLocation> add) {
        if (add.isEmpty()) return;
        List<ResourceLocation> g = new ArrayList<>(genes(stack));
        for (ResourceLocation r : add) {
            if (g.contains(r)) continue;
            if (g.size() >= MAX_GENES) g.remove(g.size() - 1);
            g.add(0, r);
        }
        setGenes(stack, g);
    }

    private static void setGenes(ItemStack stack, List<ResourceLocation> genes) {
        ListTag l = new ListTag();
        for (ResourceLocation g : genes) l.add(StringTag.valueOf(g.toString()));
        write(stack).put("Genes", l);
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

    /**
     * Gives a fish wild stats and its species' genes if it has none yet; a fish with pre-2.8.0 stats gets them
     * turned into levels (and a wild potential).
     */
    public static void ensure(ItemStack stack, RandomSource random) {
        if (stack.isEmpty()) return;
        CompoundTag d = data(stack);
        if (d != null) {
            if (!d.contains(LEVELS) && d.contains(OLD_STATS)) {
                int[] l = new int[STATS.size()], p = new int[STATS.size()];
                for (int i = 0; i < l.length; i++) {
                    l[i] = oldLevel(d, STATS.get(i));
                    p[i] = wildPotential(l[i], random);
                }
                setStats(stack, l, p);
            }
            // A stat added after this fish got its stats (Haul in 2.23.0): roll it wild now, once.
            CompoundTag lv = d.getCompound(LEVELS), pt = d.getCompound(POTENTIAL);
            if (d.contains(LEVELS)) for (String stat : STATS) {
                if (lv.contains(stat)) continue;
                int l = wildLevel(random);
                lv.putInt(stat, l);
                pt.putInt(stat, wildPotential(l, random));
                d.put(LEVELS, lv);
                d.put(POTENTIAL, pt);
            }
            return;
        }
        Item species = species(stack);
        if (species == null) return;
        int[] l = new int[STATS.size()], p = new int[STATS.size()];
        for (int i = 0; i < l.length; i++) {
            l[i] = wildLevel(random);
            p[i] = wildPotential(l[i], random);
        }
        setStats(stack, l, p);
        List<ResourceLocation> genes = new ArrayList<>(speciesGenes(species));
        // "wild_genes" in the fish's file: maybe one random gene from its list (e.g. a Barkacuda's tree).
        FishData.WildGenes w = FishData.wildGenes(species);
        if (w != null && genes.size() < MAX_GENES && random.nextFloat() < w.chance()) {
            ResourceLocation g = w.oneOf().get(random.nextInt(w.oneOf().size()));
            if (!genes.contains(g)) genes.add(g);
        }
        setGenes(stack, genes);
    }

    /** A wild level: picked by the config's wildLevelWeights. */
    public static int wildLevel(RandomSource random) {
        List<Double> w = Config.wildLevelWeights();
        double total = 0;
        for (double v : w) total += Math.max(0, v);
        if (total <= 0) return 3;
        double r = random.nextDouble() * total;
        for (int l = 0; l < w.size(); l++) {
            r -= Math.max(0, w.get(l));
            if (r < 0) return l;
        }
        return w.size() - 1;
    }

    private static int wildPotential(int level, RandomSource random) {
        double spread = Config.WILD_POTENTIAL_SPREAD.get();
        return wildPotential(level, (random.nextDouble() * 2 - 1) * spread);
    }

    /** round(base - perLevel x level + noise), 0 to 10. */
    private static int wildPotential(int level, double noise) {
        double v = Config.WILD_POTENTIAL_BASE.get() - Config.WILD_POTENTIAL_PER_LEVEL.get() * level + noise;
        return Mth.clamp((int) Math.floor(v + 0.5), 0, MAX_LEVEL);
    }

    /**
     * The breeding roll for a total (two parents' levels, or two potentials, added): total + breedExtraRolls
     * tries, each won at breedRollChance + breedRollChancePerLevel x total; the number of wins, at most 10.
     */
    public static int roll(int total, RandomSource random) {
        return roll(total, Config.BREED_EXTRA_ROLLS.get(), random);
    }

    /** The same roll with total + extra tries (potential uses breedPotentialBonusRolls). */
    public static int roll(int total, int extra, RandomSource random) {
        int tries = Math.max(0, total + extra);
        double p = Mth.clamp(Config.BREED_ROLL_CHANCE.get() + Config.BREED_ROLL_CHANCE_PER_LEVEL.get() * total, 0, Config.BREED_ROLL_CHANCE_MAX.get());
        int wins = 0;
        for (int i = 0; i < tries && wins < MAX_LEVEL; i++) if (random.nextDouble() < p) wins++;
        return wins;
    }

    /**
     * A baby's stats and genes, written onto its bucket. Each stat: potential from the parents' potentials, then
     * level from the parents' levels, capped at that potential (see {@link #roll}).
     * Genes: its species' own, then the parents' in turn (A's first, B's first, A's second, ...), each at 50%,
     * skipping ones it has, stopping once it has {@value #MAX_GENES}.
     */
    public static void inherit(ItemStack baby, ItemStack a, ItemStack b, RandomSource random) {
        int[] l = new int[STATS.size()], p = new int[STATS.size()];
        // A cross between two species comes out CROSS_PENALTY levels lower in every stat (potential unchanged).
        boolean cross = species(a) != species(b);
        for (int i = 0; i < l.length; i++) {
            String stat = STATS.get(i);
            p[i] = roll(potential(a, stat) + potential(b, stat), Config.BREED_POTENTIAL_BONUS_ROLLS.get(), random);
            l[i] = Math.min(p[i], roll(level(a, stat) + level(b, stat), random));
            if (cross) l[i] = Math.max(0, l[i] - CROSS_PENALTY);
        }
        Item species = species(baby);
        List<ResourceLocation> genes = new ArrayList<>(species == null ? List.of() : speciesGenes(species));
        List<ResourceLocation> ga = genes(a), gb = genes(b);
        // Each parent gene passes on SAME_SPECIES_PASS of the time from a parent of the baby's own species, and only
        // CROSS_SPECIES_PASS of the time from a parent of the other species (traits jump species less easily).
        float ca = species(a) == species ? SAME_SPECIES_PASS : CROSS_SPECIES_PASS;
        float cb = species(b) == species ? SAME_SPECIES_PASS : CROSS_SPECIES_PASS;
        // A gene both parents carry always passes on (before the rolls, so it gets a slot first).
        for (ResourceLocation g : ga) if (gb.contains(g) && !genes.contains(g) && genes.size() < MAX_GENES) genes.add(g);
        for (int i = 0; i < Math.max(ga.size(), gb.size()) && genes.size() < MAX_GENES; i++) {
            for (int k = 0; k < 2; k++) {
                List<ResourceLocation> from = k == 0 ? ga : gb;
                if (genes.size() >= MAX_GENES || i >= from.size()) continue;
                ResourceLocation g = from.get(i);
                if (!genes.contains(g) && random.nextFloat() < (k == 0 ? ca : cb)) genes.add(g);
            }
        }
        while (genes.size() > MAX_GENES) genes.remove(0);
        setStats(baby, l, p);
        setGenes(baby, genes);
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
            case SATIETY -> 0x6BBF4A;
            case HEAT -> 0xF0702A;
            case HAUL -> 0xA77BE0;
            default -> 0x3FD0E0;
        };
    }

    /** The stats a fish shows when it has drops and eats: Yield, Satiety, Charge for power fish, Heat for heat fish. */
    public static List<String> shownStats(boolean power, boolean heat) {
        return shownStats(true, true, power, heat, false);
    }

    /**
     * Only the stats that do something for this fish: Yield with drops, Satiety if it eats (drops, power or heat),
     * Charge for power fish, Heat for heat fish, Haul for hauling fish (#orefishfarm:hauling_fish).
     */
    public static List<String> shownStats(boolean drops, boolean eats, boolean power, boolean heat, boolean haul) {
        List<String> out = new ArrayList<>();
        if (drops) out.add(YIELD);
        if (eats) out.add(SATIETY);
        if (power) out.add(CHARGE);
        if (heat) out.add(HEAT);
        if (haul) out.add(HAUL);
        return out;
    }

    /** The stats this fish shows. */
    public static List<String> shownStats(FishData.Fish fish) {
        return shownStats(FishData.hasDrops(fish), FishData.eats(fish), fish.makesPower(), fish.makesHeat(), FishData.isHauler(fish));
    }

    /**
     * Tooltip lines for a live fish (nothing for one that hasn't been bucketed yet). The stats go in as one
     * {@link StatBars#marker} line that the client swaps for the drawn bars (see TooltipEvents); anything that
     * shows plain text sees "Yield 3/7 · Satiety 4/6 ..." instead.
     */
    public static void tooltip(ItemStack stack, List<Component> lines, boolean power, boolean heat) {
        tooltip(stack, lines, shownStats(power, heat));
    }

    public static void tooltip(ItemStack stack, List<Component> lines, List<String> stats) {
        if (!has(stack)) return;
        if (isNewborn(stack)) lines.add(Component.translatable("tooltip.orefishfarm.newborn").withStyle(ChatFormatting.YELLOW));
        lines.add(StatBars.marker(stack, stats));
        List<ResourceLocation> genes = genes(stack);
        if (!genes.isEmpty()) {
            MutableComponent g = Component.translatable("tooltip.orefishfarm.genes").withStyle(ChatFormatting.GRAY).append(" ");
            for (int i = 0; i < genes.size(); i++) {
                if (i > 0) g.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                g.append(geneName(genes.get(i)));
            }
            lines.add(g);
            // What a gene does, for genes with a "gene.<ns>.<gene>.desc" lang line (Hardy, Hot Blooded, Charged).
            for (ResourceLocation gene : genes) {
                String key = "gene." + gene.getNamespace() + "." + gene.getPath() + ".desc";
                if (net.minecraft.locale.Language.getInstance().has(key))
                    lines.add(Component.literal(" ").append(geneName(gene)).append(": ")
                            .append(Component.translatable(key)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}

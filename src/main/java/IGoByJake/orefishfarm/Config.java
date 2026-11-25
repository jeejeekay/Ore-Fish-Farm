package IGoByJake.orefishfarm;

import net.minecraftforge.common.ForgeConfigSpec;

/** Global multipliers on top of the per-fish numbers in the datapack files. */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PRODUCT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SAME_SPECIES_CHANCE, CROSS_CHANCE, HYBRID_CHANCE, BREED_TIME;
    public static final ForgeConfigSpec.DoubleValue POWER;
    public static final ForgeConfigSpec.DoubleValue FOOD_TIME;
    public static final ForgeConfigSpec.IntValue DEFAULT_BREED_TIME;
    public static final ForgeConfigSpec.IntValue DEFAULT_FOOD_TICKS;
    public static final ForgeConfigSpec.IntValue BITE_TICKS;
    public static final ForgeConfigSpec.IntValue BITE_JITTER;
    public static final ForgeConfigSpec.IntValue ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue ENERGY_OUTPUT;
    public static final ForgeConfigSpec.IntValue MAX_AQUARIUM_SIZE;
    public static final ForgeConfigSpec.IntValue HABITAT_GRACE;
    public static final ForgeConfigSpec.IntValue HABITAT_HURT;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> STAT_MULTIPLIERS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> WILD_LEVEL_WEIGHTS;
    public static final ForgeConfigSpec.DoubleValue WILD_POTENTIAL_BASE;
    public static final ForgeConfigSpec.DoubleValue WILD_POTENTIAL_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue WILD_POTENTIAL_SPREAD;
    public static final ForgeConfigSpec.IntValue BREED_EXTRA_ROLLS;
    public static final ForgeConfigSpec.DoubleValue BREED_ROLL_CHANCE;
    public static final ForgeConfigSpec.DoubleValue BREED_ROLL_CHANCE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue BREED_ROLL_CHANCE_MAX;
    public static final ForgeConfigSpec.IntValue BREED_POTENTIAL_BONUS_ROLLS;
    public static final ForgeConfigSpec.DoubleValue TANK_SIZE_POWER;
    public static final ForgeConfigSpec.DoubleValue TANK_REFERENCE_SIZE;
    public static final ForgeConfigSpec.DoubleValue TANK_MAX_JOINED;
    public static final ForgeConfigSpec.DoubleValue TANK_MAX_LONE;
    public static final ForgeConfigSpec.DoubleValue TANK_MIN;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> TANK_SIZE_OVERRIDES;
    public static final ForgeConfigSpec.DoubleValue HAUL_BASE, PIPE_SWIM_SPEED;
    public static final ForgeConfigSpec.IntValue PIPE_SCAN_TICKS, PIPE_MAX_SCAN_TICKS, PIPES_PER_FISH_SLOT, INVENTORIES_PER_FISH_SLOT, PIPE_MAX_FISH, PIPE_MAX_NETWORK, PIPE_MAX_COMMANDS;

    static final java.util.List<Double> DEFAULT_MULTIPLIERS = java.util.List.of(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 3.0);
    static final java.util.List<Double> DEFAULT_WEIGHTS = java.util.List.of(81.0, 243.0, 729.0, 2187.0, 729.0, 243.0, 81.0, 27.0, 9.0, 3.0, 1.0);

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Chances are rolled once per bite: every biteTicks (see [food]) that a tank spends eating.",
                "Each fish's own numbers live in data/<namespace>/orefishfarm_fish/*.json; these multiply them.").push("odds");
        PRODUCT_CHANCE = b.comment("Multiplier on every fish's scale/product chance.").defineInRange("productChanceMultiplier", 1.0, 0.0, 100.0);
        b.pop().push("breeding");
        b.comment("Each fish has a breeding timer (breed_time in its fish file) that runs while it eats. When it runs out the",
                "fish picks a random partner it can breed with, makes the baby, and the baby is born with one of these chances.");
        SAME_SPECIES_CHANCE = b.comment("Chance a baby of two fish of the same species is born.").defineInRange("sameSpeciesChance", 0.5, 0.0, 1.0);
        CROSS_CHANCE = b.comment("Chance a baby of two different species (not a recipe fish) is born.").defineInRange("crossChance", 0.3, 0.0, 1.0);
        HYBRID_CHANCE = b.comment("Chance a recipe fish (orefishfarm_breeding/*.json) is born, unless the recipe gives its own chance.").defineInRange("hybridChance", 0.1, 0.0, 1.0);
        BREED_TIME = b.comment("Multiplier on every fish's breeding timer.").defineInRange("breedTimeMultiplier", 1.0, 0.01, 100.0);
        DEFAULT_BREED_TIME = b.comment("Breeding timer in seconds for fish with no breed_time (and fish in #orefishfarm:tank_fish with no fish file, e.g. cod).").defineInRange("defaultBreedTime", 180, 1, 86400);
        b.pop().push("food");
        FOOD_TIME = b.comment("Multiplier on how long each piece of food lasts.").defineInRange("foodTimeMultiplier", 1.0, 0.01, 100.0);
        DEFAULT_FOOD_TICKS = b.comment("Ticks one item lasts for foods in the #orefishfarm:fish_food tag that have no food file.").defineInRange("defaultFoodTicks", 4800, 1, 72000);
        BITE_TICKS = b.comment("Ticks of eating between bites. Every bite, each eating fish rolls its scale/product chance and pairs roll to breed.",
                "How long a piece of food lasts does not change how often fish roll.").defineInRange("biteTicks", 400, 1, 72000);
        BITE_JITTER = b.comment("Each bite comes this many ticks early or late at random (any number up to it), and every fish",
                "keeps its own bite timer, so fish shed at staggered times instead of all at once. 50 = 2.5 seconds.")
                .defineInRange("biteJitterTicks", 50, 0, 72000);
        b.pop().push("power");
        POWER = b.comment("Multiplier on the FE/tick power fish make.").defineInRange("powerMultiplier", 1.0, 0.0, 100.0);
        ENERGY_CAPACITY = b.comment("FE each block of a power tank can hold (an aquarium holds this times its blocks).").defineInRange("energyCapacity", 20000, 100, Integer.MAX_VALUE);
        ENERGY_OUTPUT = b.comment("Most FE per tick a tank pushes into neighbouring blocks.").defineInRange("energyOutput", 1000, 1, Integer.MAX_VALUE);
        b.pop().push("aquarium");
        MAX_AQUARIUM_SIZE = b.comment("Largest aquarium joined tanks can form, in blocks along each axis.").defineInRange("maxAquariumSize", 8, 1, 16);
        b.pop().push("habitat");
        HABITAT_GRACE = b.comment("Ticks a fish in the wrong habitat waits for its habitat food before it starts getting hurt.").defineInRange("graceTicks", 200, 0, 72000);
        HABITAT_HURT = b.comment("Ticks a fish is hurt (with a hurt sound each second) before it dies. Pull it out in time to save it.").defineInRange("hurtTicks", 60, 1, 72000);
        b.pop().push("stats");
        STAT_MULTIPLIERS = b.comment("What each stat level (0 to 10) does: 11 multipliers, level 0 first. Yield multiplies the product",
                "(above 1x it makes extra), Satiety how long food lasts for that fish, Charge its FE, Heat its heat.",
                "Default: level 3 = 1x, +25% a level, 10 = 3x.")
                .defineList("statMultipliers", DEFAULT_MULTIPLIERS, o -> o instanceof Number);
        WILD_LEVEL_WEIGHTS = b.comment("How likely each level (0 to 10) is on a wild fish: 11 weights, level 0 first.",
                "Default: each level a third as likely as the next one towards 3 (3 is half of all wild fish, 10 is 1 in 4,333).")
                .defineList("wildLevelWeights", DEFAULT_WEIGHTS, o -> o instanceof Number);
        WILD_POTENTIAL_BASE = b.comment("A wild fish's potential in a stat is round(base - perLevel x its level +- spread), 0 to 10,",
                "so low fish tend to have high potential. Potential is the highest level its babies can reach.")
                .defineInRange("wildPotentialBase", 9.0, -10.0, 20.0);
        WILD_POTENTIAL_PER_LEVEL = b.defineInRange("wildPotentialPerLevel", 0.5, -5.0, 5.0);
        WILD_POTENTIAL_SPREAD = b.defineInRange("wildPotentialSpread", 1.5, 0.0, 10.0);
        // (new key names in 2.19.0, so the new defaults replace the old ones in existing config files)
        BREED_EXTRA_ROLLS = b.comment("Breeding, for each stat: the baby's potential rolls the parents' two potentials added, then its level",
                "rolls their two levels added (at most that potential). A roll of a total makes total + breedBonusRolls tries,",
                "each won at breedRollBaseChance + breedRollChancePerPoint x total (at most breedRollChanceMax);",
                "the baby gets the number of wins (at most 10).")
                .defineInRange("breedBonusRolls", 1, 0, 40);
        BREED_ROLL_CHANCE = b.defineInRange("breedRollBaseChance", 0.25, 0.0, 1.0);
        BREED_ROLL_CHANCE_PER_LEVEL = b.defineInRange("breedRollChancePerPoint", 0.025, -1.0, 1.0);
        BREED_ROLL_CHANCE_MAX = b.defineInRange("breedRollChanceMax", 0.5, 0.0, 1.0);
        BREED_POTENTIAL_BONUS_ROLLS = b.comment("The same roll for the baby's potential, but with total + this many tries (default one fewer than the potentials added).")
                .defineInRange("breedPotentialBonusRolls", -1, -40, 40);
        b.pop().push("tank_display");
        TANK_SIZE_POWER = b.comment("How big each mob is drawn in a tank. A mob as long as referenceSize (a cod, 0.5) is drawn at the tank's",
                "normal size (0.45 blocks in a joined aquarium); others at normal x (their size / referenceSize) ^ sizePower.",
                "Size = the adult hitbox's longest side (fish-file \"scale\" included). 0 = every mob the same, 0.5 = square root,",
                "1 = true to size. Babies are drawn at their own baby scale of that (half for vanilla babies).")
                .defineInRange("sizePower", 0.5, 0.0, 1.0);
        TANK_REFERENCE_SIZE = b.defineInRange("referenceSize", 0.5, 0.05, 8.0);
        TANK_MAX_JOINED = b.comment("Largest drawn length, in blocks, in an aquarium of 2+ tank blocks and in a lone tank block.")
                .defineInRange("maxSizeJoined", 0.8, 0.1, 2.0);
        TANK_MAX_LONE = b.defineInRange("maxSizeLone", 0.6, 0.1, 1.0);
        TANK_MIN = b.comment("Smallest drawn length, in blocks (babies can go below it).").defineInRange("minSize", 0.2, 0.02, 1.0);
        TANK_SIZE_OVERRIDES = b.comment("Extra size multipliers for mobs whose model doesn't match their hitbox: \"<entity id>=<multiplier>\",",
                "e.g. \"alexscaves:gossamer_worm=0.5\". Applied after the maximum.")
                .defineList("sizeOverrides", java.util.List.of(), o -> o instanceof String str && str.contains("="));
        b.pop().push("pipes");
        HAUL_BASE = b.comment("Items a hauling fish carries per trip at Haul 1x (level 3 by default): it carries this x its Haul stat multiplier",
                "(statMultipliers) x its fish file's \"haul\" (default 1). Defaults: level 0 = 1, level 3 = 4, level 9 = 10, level 10 = 12.",
                "A part item is a roll: 2.5 = 2 items, plus a 50% chance of a third.")
                .defineInRange("haulBase", 4.0, 0.01, 1000.0);
        PIPE_SWIM_SPEED = b.comment("How fast hauling fish swim through Pipe Tanks, in blocks per second (fast swimmers go 1.5x).").defineInRange("swimSpeed", 4.0, 0.25, 64.0);
        PIPE_SCAN_TICKS = b.comment("Ticks between a network's looks for work while it has idle fish and live commands.").defineInRange("scanTicks", 20, 1, 1200);
        PIPE_MAX_SCAN_TICKS = b.comment("A network that finds nothing to do looks less often, up to this many ticks apart (any change or delivery resets it).")
                .defineInRange("maxScanTicks", 80, 1, 2400);
        PIPES_PER_FISH_SLOT = b.comment("A network has 1 fish slot, plus one more for every this many pipes...").defineInRange("pipesPerExtraFishSlot", 4, 1, 4096);
        INVENTORIES_PER_FISH_SLOT = b.comment("...together with this many connected inventories (an extra slot needs both).").defineInRange("inventoriesPerExtraFishSlot", 2, 1, 4096);
        PIPE_MAX_FISH = b.comment("Most fish slots a network can have.").defineInRange("maxFishSlots", 16, 1, 16);
        PIPE_MAX_NETWORK = b.comment("Most pipes in one network; pipes past this form a network of their own.").defineInRange("maxPipesPerNetwork", 2048, 1, 65536);
        PIPE_MAX_COMMANDS = b.defineInRange("maxCommands", 64, 1, 256);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}

    private static java.util.Map<String, Float> overrides;
    private static java.util.List<? extends String> overridesFrom;

    /** The sizeOverrides multiplier for this entity id (1 if none). */
    public static float tankSizeOverride(String entityId) {
        java.util.List<? extends String> list = TANK_SIZE_OVERRIDES.get();
        if (list != overridesFrom) {
            java.util.Map<String, Float> m = new java.util.HashMap<>();
            for (String line : list) {
                int i = line.indexOf('=');
                try {
                    m.put(line.substring(0, i).trim(), Float.parseFloat(line.substring(i + 1).trim()));
                } catch (Exception ignored) {
                }
            }
            overrides = m;
            overridesFrom = list;
        }
        return overrides.getOrDefault(entityId, 1F);
    }

    /** The multiplier for a stat level (from statMultipliers; the default list if that isn't 11 numbers). */
    public static double statMultiplier(int level) {
        java.util.List<? extends Double> l = listOr(STAT_MULTIPLIERS, DEFAULT_MULTIPLIERS);
        return l.get(Math.max(0, Math.min(10, level))).doubleValue();
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<Double> wildLevelWeights() {
        return (java.util.List<Double>) listOr(WILD_LEVEL_WEIGHTS, DEFAULT_WEIGHTS);
    }

    private static java.util.List<? extends Double> listOr(ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> v, java.util.List<Double> fallback) {
        try {
            java.util.List<? extends Number> raw = v.get();
            if (raw == null || raw.size() != 11) return fallback;
            java.util.List<Double> out = new java.util.ArrayList<>(11);
            for (Number n : raw) out.add(n.doubleValue());
            return out;
        } catch (IllegalStateException | ClassCastException e) {
            return fallback; // config not loaded yet (e.g. a tooltip on the title screen), or a bad entry
        }
    }
}

package IGoByJake.orefishfarm;

import net.minecraftforge.common.ForgeConfigSpec;

/** Global multipliers on top of the per-fish numbers in the datapack files. */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PRODUCT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue BREED_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CROSSBREED_CHANCE;
    public static final ForgeConfigSpec.DoubleValue POWER;
    public static final ForgeConfigSpec.DoubleValue FOOD_TIME;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_BREED_CHANCE;
    public static final ForgeConfigSpec.IntValue DEFAULT_FOOD_TICKS;
    public static final ForgeConfigSpec.IntValue ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue ENERGY_OUTPUT;
    public static final ForgeConfigSpec.BooleanValue SEAGULLS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> SEAGULL_DIMENSIONS;
    public static final ForgeConfigSpec.IntValue FIRST_GIFT_SECONDS;
    public static final ForgeConfigSpec.DoubleValue SECOND_SEAGULL_CHANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Chances are rolled once per piece of food a tank finishes eating.",
                "Each fish's own numbers live in data/<namespace>/orefishfarm_fish/*.json; these multiply them.").push("odds");
        PRODUCT_CHANCE = b.comment("Multiplier on every fish's scale/product chance.").defineInRange("productChanceMultiplier", 1.0, 0.0, 100.0);
        BREED_CHANCE = b.comment("Multiplier on same-species breeding chances.").defineInRange("breedChanceMultiplier", 1.0, 0.0, 100.0);
        CROSSBREED_CHANCE = b.comment("Multiplier on cross-breeding (hybrid) chances from orefishfarm_breeding/*.json.").defineInRange("crossbreedChanceMultiplier", 1.0, 0.0, 100.0);
        DEFAULT_BREED_CHANCE = b.comment("Breeding chance for fish in the #orefishfarm:tank_fish tag that have no fish file (e.g. cod).").defineInRange("defaultBreedChance", 0.1, 0.0, 1.0);
        b.pop().push("food");
        FOOD_TIME = b.comment("Multiplier on how long each piece of food lasts.").defineInRange("foodTimeMultiplier", 1.0, 0.01, 100.0);
        DEFAULT_FOOD_TICKS = b.comment("Ticks one item lasts for foods in the #orefishfarm:fish_food tag that have no food file.").defineInRange("defaultFoodTicks", 400, 1, 72000);
        b.pop().push("power");
        POWER = b.comment("Multiplier on the FE/tick power fish make.").defineInRange("powerMultiplier", 1.0, 0.0, 100.0);
        ENERGY_CAPACITY = b.comment("FE a fish tank can hold.").defineInRange("energyCapacity", 20000, 100, Integer.MAX_VALUE);
        ENERGY_OUTPUT = b.comment("Most FE per tick a tank pushes into neighbouring blocks.").defineInRange("energyOutput", 1000, 1, Integer.MAX_VALUE);
        b.pop().push("seagulls");
        SEAGULLS = b.comment("A seagull keeps each player company (and brings the odd gift).").define("enabled", true);
        SEAGULL_DIMENSIONS = b.comment("Dimensions seagulls appear in.").defineListAllowEmpty("dimensions",
                java.util.List.of("minecraft:overworld"), o -> o instanceof String);
        FIRST_GIFT_SECONDS = b.comment("A player's first gift (loot table orefishfarm:gameplay/seagull/first_gift) arrives within about this many seconds of play.")
                .defineInRange("firstGiftWithinSeconds", 300, 180, 36000);
        SECOND_SEAGULL_CHANCE = b.comment("Chance each minute that a second seagull joins.").defineInRange("secondSeagullChance", 0.03, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}

package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.block.LegacyTankBlock;
import IGoByJake.orefishfarm.block.LegacyTankBlockEntity;
import IGoByJake.orefishfarm.block.TankBlock;
import IGoByJake.orefishfarm.block.TankBlockEntity;
import IGoByJake.orefishfarm.entity.OreFish;
import IGoByJake.orefishfarm.loot.AddFishModifier;
import IGoByJake.orefishfarm.menu.AquariumMenu;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class Registry {
    private static final String M = OreFishFarm.MODID;
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, M);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, M);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, M);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, M);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, M);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, M);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, M);

    /** Creative tab order. */
    private static final List<RegistryObject<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static RegistryObject<Item> fish(String name) {
        RegistryObject<Item> r = ITEMS.register(name, () -> new FishItem(new Item.Properties()));
        TAB_ORDER.add(r);
        return r;
    }

    private static RegistryObject<Item> tabbed(String name, java.util.function.Supplier<Item> sup) {
        RegistryObject<Item> r = ITEMS.register(name, sup);
        TAB_ORDER.add(r);
        return r;
    }

    private static RegistryObject<Item> item(String name) {
        RegistryObject<Item> r = ITEMS.register(name, () -> new Item(new Item.Properties()));
        TAB_ORDER.add(r);
        return r;
    }

    // Fish
    public static final RegistryObject<Item> OBSIDIAN_FISH = fish("obsidian_fish");
    public static final RegistryObject<Item> FERROFIN = fish("ferrofin");
    public static final RegistryObject<Item> GILDED_CARP = fish("gilded_carp");
    public static final RegistryObject<Item> ZINCRAY = fish("zincray");
    public static final RegistryObject<Item> CUPRUM_EEL = fish("cuprum_eel");
    public static final RegistryObject<Item> BRASS_BREAM = fish("brass_bream");
    // Fish without a mob of their own (drawn by the shared data fish), moved here from Quicreate in 2.20.0
    public static final RegistryObject<Item> GLASS_EEL = fish("glass_eel");
    public static final RegistryObject<Item> FURRY_TROUT = fish("furry_trout");
    public static final RegistryObject<Item> STONE_FISH = fish("stone_fish");
    public static final RegistryObject<Item> MUDSKIPPER = fish("mudskipper");
    public static final RegistryObject<Item> SANDSHARK = fish("sandshark");
    public static final RegistryObject<Item> RED_LAMPFISH = fish("red_lampfish");
    public static final RegistryObject<Item> BARKACUDA = fish("barkacuda");

    // Living fish: same ids as the items.
    private static RegistryObject<EntityType<OreFish>> fishMob(String name) {
        return ENTITIES.register(name, () -> EntityType.Builder.<OreFish>of(OreFish::new, MobCategory.WATER_AMBIENT)
                .sized(0.5F, 0.35F).clientTrackingRange(4).build(name));
    }

    public static final RegistryObject<EntityType<OreFish>> OBSIDIAN_FISH_MOB = fishMob("obsidian_fish");
    public static final RegistryObject<EntityType<OreFish>> FERROFIN_MOB = fishMob("ferrofin");
    public static final RegistryObject<EntityType<OreFish>> GILDED_CARP_MOB = fishMob("gilded_carp");
    public static final RegistryObject<EntityType<OreFish>> ZINCRAY_MOB = fishMob("zincray");
    public static final RegistryObject<EntityType<OreFish>> CUPRUM_EEL_MOB = fishMob("cuprum_eel");
    public static final RegistryObject<EntityType<OreFish>> BRASS_BREAM_MOB = fishMob("brass_bream");
    /** One mob for every fish species without its own (data-pack and other mods' fish). */
    public static final RegistryObject<EntityType<IGoByJake.orefishfarm.entity.DataFish>> DATA_FISH_MOB = ENTITIES.register("data_fish",
            () -> EntityType.Builder.<IGoByJake.orefishfarm.entity.DataFish>of(IGoByJake.orefishfarm.entity.DataFish::new, MobCategory.WATER_AMBIENT)
                    .sized(0.5F, 0.35F).clientTrackingRange(4).build("data_fish"));
    public static final List<RegistryObject<EntityType<OreFish>>> FISH_MOBS =
            List.of(OBSIDIAN_FISH_MOB, FERROFIN_MOB, GILDED_CARP_MOB, ZINCRAY_MOB, CUPRUM_EEL_MOB, BRASS_BREAM_MOB);

    // What they make
    public static final RegistryObject<Item> OBSIDIAN_SCALE = item("obsidian_scale");
    public static final RegistryObject<Item> IRON_SCALE = item("iron_scale");
    public static final RegistryObject<Item> GOLD_SCALE = item("gold_scale");
    public static final RegistryObject<Item> ZINC_SCALE = item("zinc_scale");
    public static final RegistryObject<Item> COPPER_TOOTH = item("copper_tooth");
    public static final RegistryObject<Item> BRASS_SCALE = item("brass_scale");
    public static final RegistryObject<Item> GLASS_TOOTH = item("glass_tooth");
    public static final RegistryObject<Item> PEBBLED_SCALE = item("pebbled_scale");
    public static final RegistryObject<Item> MUD_BLOB = item("mud_blob");
    public static final RegistryObject<Item> SANDY_TEETH = item("sandy_teeth");
    // The Barkacuda sheds one of these at random; 9 make that tree's log.
    public static final List<RegistryObject<Item>> BARK_SCALES = java.util.stream.Stream.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry").map(w -> item(w + "_bark_scale")).toList();
    public static final RegistryObject<Item> FISH_FOOD = item("fish_food");
    public static final RegistryObject<Item> BRINE_FLAKES = item("brine_flakes");
    public static final RegistryObject<Item> EMBER_PELLETS = item("ember_pellets");
    public static final RegistryObject<Item> VOID_FLAKES = item("void_flakes");
    public static final RegistryObject<Item> FISH_BONE = item("fish_bone");

    // Rods: plain fishing rods; the item tags #orefishfarm:lava_rods / #orefishfarm:void_rods give them their fluid.
    public static final RegistryObject<Item> LAVA_ROD = tabbed("lava_rod",
            () -> new IGoByJake.orefishfarm.fishing.RodItem(new Item.Properties().durability(128).fireResistant(), "item.orefishfarm.lava_rod.desc"));
    public static final RegistryObject<Item> VOID_ROD = tabbed("void_rod",
            () -> new IGoByJake.orefishfarm.fishing.RodItem(new Item.Properties().durability(128), "item.orefishfarm.void_rod.desc"));
    public static final RegistryObject<EntityType<IGoByJake.orefishfarm.fishing.OreFishingHook>> ORE_FISHING_HOOK = ENTITIES.register("fishing_hook",
            () -> EntityType.Builder.<IGoByJake.orefishfarm.fishing.OreFishingHook>of(IGoByJake.orefishfarm.fishing.OreFishingHook::new, MobCategory.MISC)
                    .noSave().noSummon().fireImmune().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(5)
                    .build(OreFishFarm.MODID + ":fishing_hook"));

    // Squid don't have buckets in vanilla: these are their own mob buckets (caught by SquidBuckets).
    public static final RegistryObject<Item> SQUID_BUCKET = tabbed("squid_bucket", () -> new net.minecraft.world.item.MobBucketItem(
            () -> EntityType.SQUID, () -> net.minecraft.world.level.material.Fluids.WATER, () -> net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_FISH,
            new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GLOW_SQUID_BUCKET = tabbed("glow_squid_bucket", () -> new net.minecraft.world.item.MobBucketItem(
            () -> EntityType.GLOW_SQUID, () -> net.minecraft.world.level.material.Fluids.WATER, () -> net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_FISH,
            new Item.Properties().stacksTo(1)));

    // A live fish of any species, in its habitat's bucket.
    public static final RegistryObject<Item> FISH_BUCKET = ITEMS.register("fish_bucket", () -> new FishBucketItem(new Item.Properties().stacksTo(1)));

    // Tackle: one piece per rod, one per tech age (see tackle/Tackle)
    private static RegistryObject<Item> tackle(String name, int durability) {
        return tabbed(name, () -> new IGoByJake.orefishfarm.tackle.TackleItem(durability));
    }

    public static final RegistryObject<Item> SQUID_SPINNER = tackle("squid_spinner", 32);
    public static final RegistryObject<Item> SHELL_SINKER = tackle("shell_sinker", 48);
    public static final RegistryObject<Item> PUFFLOATER = tackle("puffloater", 64);
    public static final RegistryObject<Item> SKIM_PICKLE = tackle("skim_pickle", 96);

    // Kits and upgrades for the Fish Tank
    public static final RegistryObject<Item> DYNAMO_COIL = item("dynamo_coil");

    // Spawn eggs (fish colours: body, accent)
    private static void egg(String name, java.util.function.Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type, int bg, int fg) {
        TAB_ORDER.add(ITEMS.register(name + "_spawn_egg", () -> new ForgeSpawnEggItem(type, bg, fg, new Item.Properties())));
    }

    static {
        egg("obsidian_fish", OBSIDIAN_FISH_MOB, 0x2E1F4F, 0xB67CFF);
        egg("ferrofin", FERROFIN_MOB, 0xB4B4B4, 0x6F6F6F);
        egg("gilded_carp", GILDED_CARP_MOB, 0xF2C230, 0xE5532A);
        egg("zincray", ZINCRAY_MOB, 0x9AA9B5, 0xEEF5FA);
        egg("cuprum_eel", CUPRUM_EEL_MOB, 0xD4683F, 0x4FB59A);
        egg("brass_bream", BRASS_BREAM_MOB, 0xD9A944, 0x8A6A1F);
    }

    // Tanks
    private static BlockBehaviour.Properties tankProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.GLASS).noOcclusion()
                .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    public static final RegistryObject<Block> FISH_TANK = BLOCKS.register("fish_tank",
            () -> new TankBlock(tankProps().lightLevel(s -> s.getValue(TankBlock.LOOK).light)));
    /** 1.x blocks: each turns into a Fish Tank the first time it ticks. Their items still place (and convert). */
    public static final RegistryObject<Block> BREEDING_TANK = BLOCKS.register("breeding_tank", () -> new LegacyTankBlock(tankProps(), true));
    public static final RegistryObject<Block> POWER_TANK = BLOCKS.register("power_tank", () -> new LegacyTankBlock(tankProps(), false));

    static {
        TAB_ORDER.add(0, ITEMS.register("fish_tank", () -> new BlockItem(FISH_TANK.get(), new Item.Properties())));
        ITEMS.register("breeding_tank", () -> new BlockItem(BREEDING_TANK.get(), new Item.Properties()));
        ITEMS.register("power_tank", () -> new BlockItem(POWER_TANK.get(), new Item.Properties()));
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TankBlockEntity>> FISH_TANK_BE = BLOCK_ENTITIES.register("fish_tank",
            () -> BlockEntityType.Builder.of(TankBlockEntity::new, FISH_TANK.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<LegacyTankBlockEntity>> LEGACY_BREEDING_BE = BLOCK_ENTITIES.register("breeding_tank",
            () -> BlockEntityType.Builder.of((p, st) -> new LegacyTankBlockEntity(Registry.LEGACY_BREEDING_BE.get(), p, st), BREEDING_TANK.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<LegacyTankBlockEntity>> LEGACY_POWER_BE = BLOCK_ENTITIES.register("power_tank",
            () -> BlockEntityType.Builder.of((p, st) -> new LegacyTankBlockEntity(Registry.LEGACY_POWER_BE.get(), p, st), POWER_TANK.get()).build(null));

    public static final RegistryObject<MenuType<AquariumMenu>> AQUARIUM_MENU = MENUS.register("fish_tank",
            () -> IForgeMenuType.create(AquariumMenu::fromNetwork));

    public static final RegistryObject<Codec<AddFishModifier>> ADD_FISH = LOOT_MODIFIERS.register("add_fish", AddFishModifier.CODEC);
    public static final RegistryObject<Codec<IGoByJake.orefishfarm.loot.TackleModifier>> TACKLE = LOOT_MODIFIERS.register("tackle", IGoByJake.orefishfarm.loot.TackleModifier.CODEC);
    public static final RegistryObject<Codec<IGoByJake.orefishfarm.loot.LiveCatchModifier>> LIVE_CATCH = LOOT_MODIFIERS.register("live_catch", IGoByJake.orefishfarm.loot.LiveCatchModifier.CODEC);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.orefishfarm"))
            .icon(() -> new ItemStack(FERROFIN.get()))
            .displayItems((params, out) -> {
                // Every stack once: the tab throws on a repeat (same item and tag).
                java.util.Set<String> seen = new java.util.HashSet<>();
                java.util.function.Consumer<ItemStack> add = st -> {
                    if (!st.isEmpty() && seen.add(ForgeRegistries.ITEMS.getKey(st.getItem()) + String.valueOf(st.getTag()))) out.accept(st);
                };
                TAB_ORDER.forEach(r -> add.accept(new ItemStack(r.get())));
                add.accept(new ItemStack(IGoByJake.orefishfarm.voidliquid.VoidLiquid.BUCKET.get()));
                // Each of our fish alive in a bucket, ready for a tank.
                for (var m : FISH_MOBS) {
                    Item fish = ForgeRegistries.ITEMS.getValue(m.getId());
                    if (fish != null && fish != net.minecraft.world.item.Items.AIR) add.accept(FishBucketItem.of(fish));
                }
                // Every other fish the fish files know (other mods' and data-pack fish, e.g. Quicreate's), once they're
                // loaded: the fish, alive in its bucket, and what it sheds (vanilla items left out), so one tab has them all.
                var others = new java.util.ArrayList<>(IGoByJake.orefishfarm.data.FishData.allFish());
                others.sort(java.util.Comparator.comparing(f -> String.valueOf(ForgeRegistries.ITEMS.getKey(f.item()))));
                for (var f : others) {
                    var id = ForgeRegistries.ITEMS.getKey(f.item());
                    if (id == null || id.getNamespace().equals(OreFishFarm.MODID)) continue;
                    add.accept(new ItemStack(f.item()));
                    add.accept(IGoByJake.orefishfarm.data.FishData.bucketOf(f));
                    var product = ForgeRegistries.ITEMS.getKey(f.product().getItem());
                    if (f.makesProduct() && product != null && !product.getNamespace().equals("minecraft")) add.accept(new ItemStack(f.product().getItem()));
                }
                IGoByJake.orefishfarm.data.FishData.extraBuckets().forEach(add);
            })
            .build());

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        LOOT_MODIFIERS.register(bus);
    }

    private Registry() {}
}

package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.block.BreedingTankBlockEntity;
import IGoByJake.orefishfarm.block.FishTankBlockEntity;
import IGoByJake.orefishfarm.block.PowerTankBlockEntity;
import IGoByJake.orefishfarm.block.TankBlock;
import IGoByJake.orefishfarm.entity.OreFish;
import IGoByJake.orefishfarm.loot.AddFishModifier;
import IGoByJake.orefishfarm.menu.BreedingTankMenu;
import IGoByJake.orefishfarm.menu.FishTankMenu;
import IGoByJake.orefishfarm.menu.PowerTankMenu;
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
        RegistryObject<Item> r = ITEMS.register(name, () -> new FishItem(new Item.Properties().stacksTo(16)));
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
    public static final List<RegistryObject<EntityType<OreFish>>> FISH_MOBS =
            List.of(OBSIDIAN_FISH_MOB, FERROFIN_MOB, GILDED_CARP_MOB, ZINCRAY_MOB, CUPRUM_EEL_MOB, BRASS_BREAM_MOB);

    // What they make
    public static final RegistryObject<Item> OBSIDIAN_SCALE = item("obsidian_scale");
    public static final RegistryObject<Item> IRON_SCALE = item("iron_scale");
    public static final RegistryObject<Item> GOLD_SCALE = item("gold_scale");
    public static final RegistryObject<Item> ZINC_SCALE = item("zinc_scale");
    public static final RegistryObject<Item> COPPER_TOOTH = item("copper_tooth");
    public static final RegistryObject<Item> BRASS_SCALE = item("brass_scale");
    public static final RegistryObject<Item> FISH_FOOD = item("fish_food");

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
            () -> new TankBlock(tankProps(), Registry.FISH_TANK_BE, FishTankBlockEntity::new));
    public static final RegistryObject<Block> BREEDING_TANK = BLOCKS.register("breeding_tank",
            () -> new TankBlock(tankProps(), Registry.BREEDING_TANK_BE, BreedingTankBlockEntity::new));
    public static final RegistryObject<Block> POWER_TANK = BLOCKS.register("power_tank",
            () -> new TankBlock(tankProps(), Registry.POWER_TANK_BE, PowerTankBlockEntity::new));

    static {
        for (RegistryObject<Block> b : List.of(POWER_TANK, BREEDING_TANK, FISH_TANK)) {
            TAB_ORDER.add(0, ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties())));
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<FishTankBlockEntity>> FISH_TANK_BE = BLOCK_ENTITIES.register("fish_tank",
            () -> BlockEntityType.Builder.of(FishTankBlockEntity::new, FISH_TANK.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BreedingTankBlockEntity>> BREEDING_TANK_BE = BLOCK_ENTITIES.register("breeding_tank",
            () -> BlockEntityType.Builder.of(BreedingTankBlockEntity::new, BREEDING_TANK.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<PowerTankBlockEntity>> POWER_TANK_BE = BLOCK_ENTITIES.register("power_tank",
            () -> BlockEntityType.Builder.of(PowerTankBlockEntity::new, POWER_TANK.get()).build(null));

    public static final RegistryObject<MenuType<FishTankMenu>> FISH_TANK_MENU = MENUS.register("fish_tank",
            () -> IForgeMenuType.create(FishTankMenu::fromNetwork));
    public static final RegistryObject<MenuType<BreedingTankMenu>> BREEDING_TANK_MENU = MENUS.register("breeding_tank",
            () -> IForgeMenuType.create(BreedingTankMenu::fromNetwork));
    public static final RegistryObject<MenuType<PowerTankMenu>> POWER_TANK_MENU = MENUS.register("power_tank",
            () -> IForgeMenuType.create(PowerTankMenu::fromNetwork));

    public static final RegistryObject<Codec<AddFishModifier>> ADD_FISH = LOOT_MODIFIERS.register("add_fish", AddFishModifier.CODEC);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.orefishfarm"))
            .icon(() -> new ItemStack(FERROFIN.get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(r -> out.accept(r.get())))
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

package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Jake's Ore Fish Farm: ore fish you keep in tanks. Fish in a Fish Tank eat fish food and, every bite, may drop
 * their product (scales, teeth). With breeding on, pairs sometimes make a new fish. Power fish (the Cuprum Eel)
 * can run a tank in power mode and make Forge Energy. Each live fish has stats and genes (see FishGenes). Fish,
 * foods, crossbreeds and gene colours are datapack files (see FishData).
 */
@Mod(OreFishFarm.MODID)
public class OreFishFarm {
    public static final String MODID = "orefishfarm";

    public OreFishFarm() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        IGoByJake.orefishfarm.voidliquid.VoidLiquid.register(modBus);
        Registry.register(modBus);
        // The orefishfarm:fish recipe ingredient (a live fish, by species and genes).
        modBus.addListener((net.minecraftforge.registries.RegisterEvent e) -> {
            if (e.getRegistryKey().equals(net.minecraftforge.registries.ForgeRegistries.Keys.RECIPE_SERIALIZERS))
                net.minecraftforge.common.crafting.CraftingHelper.register(IGoByJake.orefishfarm.data.FishIngredient.ID, IGoByJake.orefishfarm.data.FishIngredient.SERIALIZER);
        });
        // With Create: aquariums warmed by heat fish count as boiler heaters.
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) ->
                e.enqueueWork(() -> IGoByJake.orefishfarm.compat.CreateHeat.registerBoilerHeater(Registry.FISH_TANK.get())));
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.addListener(FishData::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(Network::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.fishing.FishingEvents::onJoin);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.tackle.Tackle::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.tackle.TackleEvents::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.tackle.TackleEvents::onFished);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.tackle.TackleEvents::onHookLeave);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.voidliquid.VoidLiquid::onLivingTick);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.voidliquid.VoidLiquid::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(IGoByJake.orefishfarm.SquidBuckets::onInteract);
        Network.register();
    }
}

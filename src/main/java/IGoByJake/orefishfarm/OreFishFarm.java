package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Jake's Ore Fish Farm: ore fish you keep in tanks. A fish in a Fish Tank eats fish food (burned like
 * furnace fuel) and, each time a piece of food is finished, may drop its product (scales, teeth). Two fish in
 * a Breeding Tank may make a new fish instead. Power fish (the Cuprum Eel) can run a tank in power mode and
 * make Forge Energy. Fish, foods and cross-breeds are all datapack files (see FishData).
 */
@Mod(OreFishFarm.MODID)
public class OreFishFarm {
    public static final String MODID = "orefishfarm";

    public OreFishFarm() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Registry.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.addListener(FishData::onAddReloadListeners);
    }
}

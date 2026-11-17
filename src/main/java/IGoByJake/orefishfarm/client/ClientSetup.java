package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = OreFishFarm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** Tank water: vanilla's animated water texture, tinted a clear aquarium blue. */
    private static final int WATER = 0x3F9FE4;

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Registry.FISH_TANK_MENU.get(), FishTankScreen::new);
            MenuScreens.register(Registry.BREEDING_TANK_MENU.get(), BreedingTankScreen::new);
            MenuScreens.register(Registry.POWER_TANK_MENU.get(), PowerTankScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Registry.FISH_TANK_BE.get(), TankRenderer::new);
        event.registerBlockEntityRenderer(Registry.BREEDING_TANK_BE.get(), TankRenderer::new);
        event.registerBlockEntityRenderer(Registry.POWER_TANK_BE.get(), TankRenderer::new);
        for (var fish : Registry.FISH_MOBS) event.registerEntityRenderer(fish.get(), OreFishRenderer::new);
        event.registerEntityRenderer(Registry.SEAGULL.get(), SeagullRenderer::new);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SeagullModel.LAYER, SeagullModel::createLayer);
    }

    @SubscribeEvent
    public static void onModels(ModelEvent.RegisterAdditional event) {
        FishRender.registerModels(event);
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.BakingCompleted event) {
        FishRender.clearCache();
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 0 ? WATER : -1,
                Registry.FISH_TANK.get(), Registry.BREEDING_TANK.get(), Registry.POWER_TANK.get());
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? WATER : -1,
                Registry.FISH_TANK.get(), Registry.BREEDING_TANK.get(), Registry.POWER_TANK.get());
    }
}

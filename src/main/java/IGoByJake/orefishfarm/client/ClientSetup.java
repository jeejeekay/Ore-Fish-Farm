package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.block.TankBlockEntity;
import IGoByJake.orefishfarm.data.FishData;
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
    /** Tank water in the item: vanilla's animated water texture, tinted a clear aquarium blue. */
    private static final int WATER = 0x3F9FE4;

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Registry.AQUARIUM_MENU.get(), AquariumScreen::new);
            MenuScreens.register(Registry.PIPE_MENU.get(), PipeScreen::new);
            // Vanilla's "cast" switch for the rods, so the line leaves the rod while fishing.
            for (var rod : java.util.List.of(Registry.LAVA_ROD, Registry.VOID_ROD)) {
                net.minecraft.client.renderer.item.ItemProperties.register(rod.get(), new net.minecraft.resources.ResourceLocation("cast"), (stack, level, entity, seed) -> {
                    if (!(entity instanceof net.minecraft.world.entity.player.Player player) || player.fishing == null) return 0.0F;
                    boolean main = entity.getMainHandItem() == stack;
                    boolean off = entity.getOffhandItem() == stack && !(entity.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem);
                    return main || off ? 1.0F : 0.0F;
                });
            }
        });
    }

    @SubscribeEvent
    public static void onTooltipFactories(net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(IGoByJake.orefishfarm.data.StatBars.class, StatBarsTooltip::new);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Registry.FISH_TANK_BE.get(), AquariumRenderer::new);
        event.registerEntityRenderer(Registry.ORE_FISHING_HOOK.get(), TackleBobberRenderer::new);
        event.registerEntityRenderer(net.minecraft.world.entity.EntityType.FISHING_BOBBER, TackleBobberRenderer::new);
        for (var fish : Registry.FISH_MOBS) event.registerEntityRenderer(fish.get(), OreFishRenderer::new);
        event.registerEntityRenderer(Registry.DATA_FISH_MOB.get(), OreFishRenderer::new);
    }

    @SubscribeEvent
    public static void onModels(ModelEvent.RegisterAdditional event) {
        FishRender.registerModels(event);
        TankExtrasModel.register(event);
    }

    @SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        TankExtrasModel.wrap(event);
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.BakingCompleted event) {
        FishRender.clearCache();
        TankExtrasModel.clearCache();
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        // The water-look fill takes its tint from the tank's habitat file.
        event.register((state, level, pos, tint) -> {
            if (tint != 0) return -1;
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof TankBlockEntity t) {
                return FishData.habitat(t.habitat()).color();
            }
            return WATER;
        }, Registry.FISH_TANK.get(), Registry.BREEDING_TANK.get(), Registry.POWER_TANK.get());
        event.register((state, level, pos, tint) -> tint == 0 ? WATER : -1, Registry.PIPE_TANK.get());
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? WATER : -1,
                Registry.FISH_TANK.get(), Registry.BREEDING_TANK.get(), Registry.POWER_TANK.get(), Registry.PIPE_TANK.get());
    }
}

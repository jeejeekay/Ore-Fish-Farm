package IGoByJake.orefishfarm.entity;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OreFishFarm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EntitySetup {
    private EntitySetup() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (var fish : Registry.FISH_MOBS) event.put(fish.get(), AbstractFish.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        // Natural spawns come from biome modifiers (data/orefishfarm/forge/biome_modifier); this is where they may appear.
        for (var fish : Registry.FISH_MOBS) {
            event.register(fish.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    WaterAnimal::checkSurfaceWaterAnimalSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

}

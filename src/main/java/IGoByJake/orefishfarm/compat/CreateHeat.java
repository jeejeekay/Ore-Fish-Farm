package IGoByJake.orefishfarm.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Heat fish warm their aquarium, and Create reads that heat exactly like a Blaze Burner's: no Create code is needed
 * to build Ore Fish Farm, it's all looked up when Create is installed.
 * <ul>
 *   <li>The tank block gets Create's own {@code heat_level} block-state property
 *       ({@code BlazeBurnerBlock.HEAT_LEVEL}), so Basins read it as they read a Blaze Burner (heated / superheated
 *       mixing).</li>
 *   <li>The tank is registered as a boiler heater with Create's Blaze Burner heater, so boilers on top of an
 *       aquarium count its heat the same way.</li>
 * </ul>
 * Levels: 0 none, 1 smouldering (passive), 2 kindled (heated), 3 seething (superheated).
 */
public final class CreateHeat {
    private static final Logger LOG = LogUtils.getLogger();
    private static Property<?> property;
    private static Object[] levels; // by our level 0..3
    private static boolean tried;

    private CreateHeat() {
    }

    /** Create's heat_level property, or null without Create. Safe to call while blocks are registered. */
    @Nullable
    public static Property<?> property() {
        if (!tried) {
            tried = true;
            if (ModList.get() != null && ModList.get().isLoaded("create")) {
                try {
                    Class<?> burner = Class.forName("com.simibubi.create.content.processing.burner.BlazeBurnerBlock");
                    property = (Property<?>) burner.getField("HEAT_LEVEL").get(null);
                    Class<?> heat = Class.forName("com.simibubi.create.content.processing.burner.BlazeBurnerBlock$HeatLevel");
                    levels = new Object[]{enumValue(heat, "NONE"), enumValue(heat, "SMOULDERING"), enumValue(heat, "KINDLED"), enumValue(heat, "SEETHING")};
                } catch (ReflectiveOperationException | RuntimeException e) {
                    LOG.warn("Ore Fish Farm: couldn't hook into Create's heat ({}); aquariums won't heat Create machines", e.toString());
                    property = null;
                }
            }
        }
        return property;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<Enum>) type, name);
    }

    /** The state with its Create heat set to our level 0..3 (unchanged without Create). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState withHeat(BlockState state, int level) {
        Property p = property();
        if (p == null || !state.hasProperty(p)) return state;
        return state.setValue(p, (Comparable) levels[Math.max(0, Math.min(3, level))]);
    }

    /** Registers the tank as a Create boiler heater (Create's Blaze Burner heater reads the same property). */
    public static void registerBoilerHeater(Block tank) {
        if (property() == null) return;
        try {
            Class<?> heater = Class.forName("com.simibubi.create.api.boiler.BoilerHeater");
            Object registry = heater.getField("REGISTRY").get(null);
            Object blazeBurner = heater.getField("BLAZE_BURNER").get(null);
            registry.getClass().getMethod("register", Object.class, Object.class).invoke(registry, tank, blazeBurner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Ore Fish Farm: couldn't register the fish tank as a Create boiler heater ({})", e.toString());
        }
    }
}

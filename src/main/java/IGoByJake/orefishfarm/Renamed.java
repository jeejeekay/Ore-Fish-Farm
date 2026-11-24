package IGoByJake.orefishfarm;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

import java.util.Set;

/**
 * Fish and their products that moved here from Quicreate (2.20.0). Old worlds keep working: items saved as
 * {@code quicreate:<id>} load as {@code orefishfarm:<id>} (missing mappings), and species names saved inside fish
 * buckets and data fish are read through {@link #fix}.
 */
@Mod.EventBusSubscriber(modid = OreFishFarm.MODID)
public final class Renamed {
    static final Set<String> FROM_QUICREATE = Set.of("glass_eel", "glass_tooth", "furry_trout", "stone_fish", "pebbled_scale",
            "mudskipper", "mud_blob", "sandshark", "sandy_teeth", "red_lampfish");

    private Renamed() {}

    /** The id a saved name stands for now. */
    public static ResourceLocation fix(ResourceLocation id) {
        if (id != null && id.getNamespace().equals("quicreate") && FROM_QUICREATE.contains(id.getPath()))
            return new ResourceLocation(OreFishFarm.MODID, id.getPath());
        return id;
    }

    public static String fix(String id) {
        ResourceLocation r = ResourceLocation.tryParse(id);
        return r == null ? id : fix(r).toString();
    }

    @SubscribeEvent
    public static void onMissing(MissingMappingsEvent event) {
        for (var m : event.getMappings(ForgeRegistries.Keys.ITEMS, "quicreate")) {
            ResourceLocation to = fix(m.getKey());
            if (!to.equals(m.getKey()) && ForgeRegistries.ITEMS.containsKey(to)) m.remap(ForgeRegistries.ITEMS.getValue(to));
        }
    }
}

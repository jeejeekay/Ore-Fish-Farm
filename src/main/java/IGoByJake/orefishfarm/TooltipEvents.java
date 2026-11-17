package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tag-driven tooltip lines, so they work for any item a pack makes a fish. */
@Mod.EventBusSubscriber(modid = OreFishFarm.MODID, value = Dist.CLIENT)
public final class TooltipEvents {
    private TooltipEvents() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        FishData.Fish fish = FishData.loaded() ? FishData.fish(event.getItemStack()) : null;
        if (fish != null && fish.makesPower()) {
            event.getToolTip().add(Component.literal("⚡ ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("tooltip.orefishfarm.power_fish_amount", FishData.powerPerTick(fish)).withStyle(ChatFormatting.GOLD)));
        } else if (event.getItemStack().is(FishItem.POWER_FISH)) {
            event.getToolTip().add(Component.literal("⚡ ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("tooltip.orefishfarm.power_fish").withStyle(ChatFormatting.GOLD)));
        }
        if (event.getItemStack().is(FishItem.HYBRID_FISH)) {
            event.getToolTip().add(Component.literal("❤ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.translatable("tooltip.orefishfarm.hybrid_fish").withStyle(ChatFormatting.DARK_PURPLE)));
        }
    }
}

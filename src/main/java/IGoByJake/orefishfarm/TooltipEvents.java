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

    /** Swaps a fish's plain stat line (FishGenes.tooltip) for the drawn stat bars. */
    @SubscribeEvent
    public static void onGather(net.minecraftforge.client.event.RenderTooltipEvent.GatherComponents event) {
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        if (!FishData.loaded() || !IGoByJake.orefishfarm.data.FishGenes.has(stack)) return;
        FishData.Fish fish = FishData.fish(stack);
        if (fish == null) return;
        var stats = IGoByJake.orefishfarm.data.FishGenes.shownStats(fish.makesPower(), fish.makesHeat());
        Component marker = IGoByJake.orefishfarm.data.StatBars.marker(stack, stats);
        var list = event.getTooltipElements();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).left().map(marker::equals).orElse(false)) {
                list.set(i, com.mojang.datafixers.util.Either.right(IGoByJake.orefishfarm.data.StatBars.of(stack, stats)));
                return;
            }
        }
    }

    /** "Habitat: Water", the line a live fish's tooltip shows (the tank screen finds it by its key). */
    public static net.minecraft.network.chat.MutableComponent habitatLine(net.minecraft.resources.ResourceLocation habitat) {
        return Component.translatable(HABITAT_KEY, FishData.habitatName(habitat)).withStyle(ChatFormatting.AQUA);
    }

    public static final String HABITAT_KEY = "tooltip.orefishfarm.habitat";

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
        // Habitat: only on a fish in a bucket (a live fish); the tank screen adds its wrong-habitat warning to this line.
        if (fish != null && event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem) {
            event.getToolTip().add(habitatLine(fish.habitat()));
        }
        net.minecraft.resources.ResourceLocation foodHab = FishData.loaded() ? FishData.foodHabitat(event.getItemStack()) : null;
        if (foodHab != null) {
            event.getToolTip().add(Component.translatable("tooltip.orefishfarm.habitat_food", FishData.habitatName(foodHab)).withStyle(ChatFormatting.AQUA));
        }
        FishData.Habitat kit = FishData.loaded() ? FishData.habitatForKit(event.getItemStack()) : null;
        if (kit != null) {
            event.getToolTip().add(Component.translatable("tooltip.orefishfarm.habitat_kit", FishData.habitatName(kit.id())).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (event.getItemStack().is(Registry.DYNAMO_COIL.get())) {
            event.getToolTip().add(Component.translatable("tooltip.orefishfarm.dynamo_coil").withStyle(ChatFormatting.GOLD));
        }
        if (fish != null) IGoByJake.orefishfarm.data.FishGenes.tooltip(event.getItemStack(), event.getToolTip(), fish.makesPower(), fish.makesHeat());
        if (event.getItemStack().is(FishItem.HYBRID_FISH)) {
            event.getToolTip().add(Component.literal("❤ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.translatable("tooltip.orefishfarm.hybrid_fish").withStyle(ChatFormatting.DARK_PURPLE)));
        }
    }
}

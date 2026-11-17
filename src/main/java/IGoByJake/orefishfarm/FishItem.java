package IGoByJake.orefishfarm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The mod's own fish items: a short description line under the name. */
public class FishItem extends Item {
    public FishItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** Fish that run a Power Tank. Tags reach the client, so any mod's fish added here gets the ⚡ tooltip too. */
    public static final TagKey<Item> POWER_FISH = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "power_fish"));
    public static final TagKey<Item> HYBRID_FISH = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "hybrid_fish"));
}

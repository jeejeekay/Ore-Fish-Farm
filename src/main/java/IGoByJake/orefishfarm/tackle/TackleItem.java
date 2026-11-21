package IGoByJake.orefishfarm.tackle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A tackle piece: wears out by one for every fish caught while it's on a rod. */
public class TackleItem extends Item {
    public TackleItem(int durability) {
        super(new Item.Properties().durability(durability));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.orefishfarm.tackle.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}

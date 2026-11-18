package IGoByJake.orefishfarm.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Lava Rod and Void Rod: plain fishing rods whose item tags let them fish lava or the void. */
public class RodItem extends FishingRodItem {
    private final String desc;

    public RodItem(Properties props, String desc) {
        super(props);
        this.desc = desc;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable(this.desc).withStyle(ChatFormatting.GOLD));
    }
}

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

    /** A piece in the main hand and a rod with no tackle in the off hand: right-click clips it on. */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack piece = player.getItemInHand(hand);
        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            ItemStack rod = player.getOffhandItem();
            if (Tackle.isRod(rod) && rod.getCount() == 1 && Tackle.get(rod).isEmpty()) {
                TackleEvents.clipInHand(player, rod, piece);
                return net.minecraft.world.InteractionResultHolder.sidedSuccess(piece, level.isClientSide);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.orefishfarm.tackle.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}

package IGoByJake.orefishfarm.tackle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * Putting tackle on and off, and wearing it out.
 * <ul>
 *   <li>Click a tackle piece onto a rod in an inventory: it clips on (any piece already there comes off onto the
 *       cursor).</li>
 *   <li>Right-click a rod with an empty cursor: the piece comes off onto the cursor.</li>
 *   <li>Every fish caught wears the piece by one. When it breaks, the next piece of the same kind in the
 *       player's inventory clips on by itself.</li>
 * </ul>
 */
public final class TackleEvents {
    private TackleEvents() {
    }

    public static void onStackedOn(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedItem();
        ItemStack target = event.getStackedOnItem();
        if (!Tackle.isRod(target) || target.getCount() != 1) return;
        if (Tackle.isTackle(carried)) {
            ItemStack old = Tackle.get(target);
            if (!old.isEmpty() && carried.getCount() > 1) return; // nowhere to put the old piece
            Tackle.set(target, carried);
            ItemStack rest = carried.copy();
            rest.shrink(1);
            event.getCarriedSlotAccess().set(old.isEmpty() ? rest : old);
            event.getPlayer().playSound(SoundEvents.ARMOR_EQUIP_CHAIN, 0.6F, 1.4F);
            event.setCanceled(true);
        } else if (carried.isEmpty() && event.getClickAction() == ClickAction.SECONDARY) {
            ItemStack old = Tackle.get(target);
            if (old.isEmpty()) return;
            Tackle.set(target, ItemStack.EMPTY);
            event.getCarriedSlotAccess().set(old);
            event.getPlayer().playSound(SoundEvents.ARMOR_EQUIP_CHAIN, 0.6F, 0.9F);
            event.setCanceled(true);
        }
    }

    public static void onFished(ItemFishedEvent event) {
        FishingHook hook = event.getHookEntity();
        Player player = hook.getPlayerOwner();
        if (player == null || player.level().isClientSide) return;
        ItemStack rod = Tackle.rodOf(player);
        ItemStack tackle = Tackle.get(rod);
        if (tackle.isEmpty()) return;
        if (player.getAbilities().instabuild) return;
        tackle.setDamageValue(tackle.getDamageValue() + 1);
        if (tackle.getDamageValue() < tackle.getMaxDamage()) {
            Tackle.set(rod, tackle);
            return;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack spare = inv.getItem(i);
            if (spare.is(tackle.getItem())) {
                Tackle.set(rod, spare);
                spare.shrink(1);
                player.displayClientMessage(Component.translatable("message.orefishfarm.tackle_replaced", tackle.getHoverName()).withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        Tackle.set(rod, ItemStack.EMPTY);
        player.displayClientMessage(Component.translatable("message.orefishfarm.tackle_broke", tackle.getHoverName()).withStyle(ChatFormatting.RED), true);
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack tackle = Tackle.get(event.getItemStack());
        if (tackle.isEmpty()) return;
        int left = tackle.getMaxDamage() - tackle.getDamageValue();
        event.getToolTip().add(1, Component.translatable("tooltip.orefishfarm.tackle_on", tackle.getHoverName(), left, tackle.getMaxDamage())
                .withStyle(ChatFormatting.AQUA));
    }
}

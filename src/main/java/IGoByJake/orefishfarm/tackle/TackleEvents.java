package IGoByJake.orefishfarm.tackle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemFishedEvent;

/**
 * Putting tackle on and off, and wearing it out.
 * <ul>
 *   <li>In an inventory, the tackle popup (client/TackleBubble) clips the carried piece on or takes a piece off;
 *       the server does the swap here ({@link #clipOn}, {@link #takeOff}).</li>
 *   <li>In the world, a rod in one hand and a piece in the other: right-click clips it on if the rod has none.</li>
 *   <li>Every fish caught wears the piece by one. When it breaks, the next piece of the same kind in the
 *       player's inventory clips on by itself.</li>
 * </ul>
 */
public final class TackleEvents {
    private TackleEvents() {
    }

    /**
     * Clips the carried piece onto the rod in a menu slot (the tackle popup, server side). Any piece already on the
     * rod comes off onto the cursor, or into the inventory if the cursor still holds more pieces. Checks everything
     * itself: the packet only names the slot.
     */
    public static void clipOn(net.minecraft.server.level.ServerPlayer player, int slotIndex) {
        var menu = player.containerMenu;
        if (slotIndex < 0 || slotIndex >= menu.slots.size() || !menu.stillValid(player)) return;
        var slot = menu.getSlot(slotIndex);
        ItemStack rod = slot.getItem(), carried = menu.getCarried();
        if (!Tackle.isRod(rod) || rod.getCount() != 1 || !Tackle.isTackle(carried) || !slot.mayPickup(player)) return;
        ItemStack newRod = rod.copy();
        ItemStack old = Tackle.get(newRod);
        Tackle.set(newRod, carried);
        ItemStack rest = carried.copy();
        rest.shrink(1);
        if (rest.isEmpty()) {
            menu.setCarried(old);
        } else {
            menu.setCarried(rest);
            if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
        }
        slot.set(newRod);
        slot.setChanged();
        menu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** Takes the piece off the rod in a menu slot onto the empty cursor (the tackle popup, server side). */
    public static void takeOff(net.minecraft.server.level.ServerPlayer player, int slotIndex) {
        var menu = player.containerMenu;
        if (slotIndex < 0 || slotIndex >= menu.slots.size() || !menu.stillValid(player)) return;
        var slot = menu.getSlot(slotIndex);
        ItemStack rod = slot.getItem();
        if (!Tackle.isRod(rod) || rod.getCount() != 1 || !menu.getCarried().isEmpty() || !slot.mayPickup(player)) return;
        ItemStack old = Tackle.get(rod);
        if (old.isEmpty()) return;
        ItemStack newRod = rod.copy();
        Tackle.set(newRod, ItemStack.EMPTY);
        menu.setCarried(old);
        slot.set(newRod);
        slot.setChanged();
        menu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.6F, 0.9F);
    }

    /** The tackle popup's click, sent to the server: which slot, and whether it clips on or takes off. */
    public record Action(int slot, boolean clip) {
        public void write(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeVarInt(this.slot);
            buf.writeBoolean(this.clip);
        }

        public static Action read(net.minecraft.network.FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readBoolean());
        }

        public void handle(@javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
            if (player == null) return;
            if (this.clip) clipOn(player, this.slot);
            else takeOff(player, this.slot);
        }
    }

    /**
     * In the world: a rod in the main hand and a piece in the off hand. Right-clicking clips the piece on if the rod
     * has none (else the rod casts as usual). Taking a piece off is only done in the inventory.
     */
    public static void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || player.fishing != null) return;
        ItemStack rod = player.getMainHandItem(), piece = player.getOffhandItem();
        if (!Tackle.isRod(rod) || rod.getCount() != 1 || !Tackle.isTackle(piece) || !Tackle.get(rod).isEmpty()) return;
        clipInHand(player, rod, piece);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide));
    }

    /** Clips one of {@code piece} onto {@code rod} (both in the player's hands). */
    public static void clipInHand(Player player, ItemStack rod, ItemStack piece) {
        if (player.level().isClientSide) return;
        Tackle.set(rod, piece);
        ItemStack name = piece.copyWithCount(1);
        if (!player.getAbilities().instabuild) piece.shrink(1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable("message.orefishfarm.tackle_clipped", name.getHoverName()).withStyle(ChatFormatting.GRAY), true);
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
}

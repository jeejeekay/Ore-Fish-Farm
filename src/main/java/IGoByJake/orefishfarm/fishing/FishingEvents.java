package IGoByJake.orefishfarm.fishing;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/**
 * Makes the rod tags work for any rod: when a vanilla bobber is cast from a rod in {@code #orefishfarm:lava_rods}
 * or {@code #orefishfarm:void_rods}, it is swapped for an {@link OreFishingHook}. Any mod's rod can be added to
 * the tags; the Lava Rod and Void Rod are just plain rods that are in them.
 */
public final class FishingEvents {
    private FishingEvents() {}

    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.getEntity().getClass() != FishingHook.class) return;
        FishingHook hook = (FishingHook) event.getEntity();
        if (hook.getPersistentData().getBoolean(OreFishingHook.VANILLA_TAG)) return;
        Player player = hook.getPlayerOwner();
        if (player == null) return;
        ItemStack rod = rodOf(player);
        boolean lava = rod.is(OreFishingHook.LAVA_RODS), voidOk = rod.is(OreFishingHook.VOID_RODS);
        if (!lava && !voidOk) return;
        event.setCanceled(true);
        int lure = EnchantmentHelper.getFishingSpeedBonus(rod);
        int luck = EnchantmentHelper.getFishingLuckBonus(rod);
        event.getLevel().addFreshEntity(new OreFishingHook(player, hook, luck, lure, lava, voidOk));
    }

    /** The rod being cast: main hand first, like vanilla. */
    private static ItemStack rodOf(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.canPerformAction(ToolActions.FISHING_ROD_CAST)) return main;
        ItemStack off = player.getOffhandItem();
        return off.canPerformAction(ToolActions.FISHING_ROD_CAST) ? off : ItemStack.EMPTY;
    }
}

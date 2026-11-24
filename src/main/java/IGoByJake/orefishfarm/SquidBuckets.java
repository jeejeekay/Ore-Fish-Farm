package IGoByJake.orefishfarm;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Squid and glow squid go in a bucket like fish: use a water bucket on one. The buckets are plain mob buckets,
 * so they work in Fish Tanks like any other (drawn as the real squid) and pour the squid back out.
 */
public final class SquidBuckets {
    private SquidBuckets() {
    }

    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Squid squid) || !squid.isAlive()) return;
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!held.is(Items.WATER_BUCKET)) return;
        ItemStack bucket = new ItemStack(squid.getType() == EntityType.GLOW_SQUID ? Registry.GLOW_SQUID_BUCKET.get() : Registry.SQUID_BUCKET.get());
        if (squid.hasCustomName()) bucket.setHoverName(squid.getCustomName());
        squid.playSound(SoundEvents.BUCKET_FILL_FISH, 1F, 1F);
        if (!player.level().isClientSide) {
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(held, player, bucket, false));
            squid.discard();
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
    }
}

package IGoByJake.orefishfarm.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A living ore fish. Each fish item has a matching mob with the same id (orefishfarm:ferrofin, ...). It swims
 * like a cod; scooping it with a water bucket gives you the fish item and keeps the bucket.
 */
public class OreFish extends AbstractFish {
    public OreFish(EntityType<? extends AbstractFish> type, Level level) {
        super(type, level);
    }

    /** The fish item with this mob's id. */
    public ItemStack fishItem() {
        Item item = ForgeRegistries.ITEMS.getValue(ForgeRegistries.ENTITY_TYPES.getKey(getType()));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public ItemStack getBucketItemStack() {
        return fishItem();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.WATER_BUCKET) && this.isAlive()) {
            this.playSound(SoundEvents.BUCKET_FILL_FISH, 1.0F, 1.0F);
            if (!this.level().isClientSide) {
                ItemStack fish = fishItem();
                if (!player.getInventory().add(fish)) player.drop(fish, false);
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }
}

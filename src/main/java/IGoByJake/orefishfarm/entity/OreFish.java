package IGoByJake.orefishfarm.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A living ore fish. Each fish item has a matching mob with the same id (orefishfarm:ferrofin, ...). It swims
 * like a cod; scooping it with a water bucket gives you a Fish Bucket, ready for a tank.
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

    /** Scooped with a water bucket (vanilla's fish bucketing): a Fish Bucket of this species, with the mob's data. */
    @Override
    public ItemStack getBucketItemStack() {
        ItemStack fish = fishItem();
        return fish.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET) : IGoByJake.orefishfarm.FishBucketItem.of(fish.getItem());
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

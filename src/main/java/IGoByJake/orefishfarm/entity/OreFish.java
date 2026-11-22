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
        refreshDimensions(); // the type gives the unscaled size; use the fish file's scale from the start
    }

    /** Fish file "scale": the mob (hitbox and model) is that many times bigger. Tanks fit the mob by its hitbox, so they stay the same. */
    public float sizeScale() {
        ItemStack fish = fishItem();
        return fish.isEmpty() ? 1F : IGoByJake.orefishfarm.data.FishData.scale(fish.getItem());
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        float s = sizeScale();
        return s == 1F ? super.getDimensions(pose) : super.getDimensions(pose).scale(s);
    }

    @Override
    public void tick() {
        // Fish data (and so the scale) can arrive after the mob was made, or change on /reload.
        if (this.tickCount % 40 == 1 && Math.abs(getBbWidth() - getType().getWidth() * sizeScale()) > 1.0E-3) refreshDimensions();
        super.tick();
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

    /** Stats and genes carried by a fish let out of a bucket, so scooping it again keeps them. */
    @org.jetbrains.annotations.Nullable private net.minecraft.nbt.CompoundTag genes;

    /** Scooped: the bucket gets the fish's stats and genes (rolled now if it never had any). */
    @Override
    public void saveToBucketTag(ItemStack bucket) {
        super.saveToBucketTag(bucket);
        if (this.genes != null) bucket.getOrCreateTag().put(IGoByJake.orefishfarm.data.FishGenes.KEY, this.genes.copy());
        IGoByJake.orefishfarm.data.FishGenes.ensure(bucket, getRandom());
    }

    @Override
    public void loadFromBucketTag(net.minecraft.nbt.CompoundTag tag) {
        super.loadFromBucketTag(tag);
        if (tag.contains(IGoByJake.orefishfarm.data.FishGenes.KEY)) this.genes = tag.getCompound(IGoByJake.orefishfarm.data.FishGenes.KEY).copy();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.genes != null) tag.put(IGoByJake.orefishfarm.data.FishGenes.KEY, this.genes);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(IGoByJake.orefishfarm.data.FishGenes.KEY)) this.genes = tag.getCompound(IGoByJake.orefishfarm.data.FishGenes.KEY);
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

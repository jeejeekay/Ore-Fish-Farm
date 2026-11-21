package IGoByJake.orefishfarm.entity;

import IGoByJake.orefishfarm.FishBucketItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A living fish for any species that has no mob of its own (fish added by data packs or other mods, like
 * Quicreate's glass eel). One mob type for all of them: it remembers which species it is, looks like that
 * species' tank model ({@code models/fish/<fish>.json}) and goes back into a bucket as that species. Made when
 * such a fish is let out of its bucket, and used to draw it in tanks.
 */
public class DataFish extends OreFish {
    private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(DataFish.class, EntityDataSerializers.STRING);

    public DataFish(EntityType<? extends AbstractFish> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SPECIES, "");
    }

    public void setSpecies(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        this.entityData.set(SPECIES, id == null ? "" : id.toString());
    }

    @Override
    public ItemStack fishItem() {
        ResourceLocation id = ResourceLocation.tryParse(this.entityData.get(SPECIES));
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    protected Component getTypeName() {
        ItemStack fish = fishItem();
        return fish.isEmpty() ? super.getTypeName() : fish.getHoverName();
    }

    @Override
    public void saveToBucketTag(ItemStack bucket) {
        super.saveToBucketTag(bucket);
        bucket.getOrCreateTag().putString(FishBucketItem.KEY, this.entityData.get(SPECIES));
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        if (tag.contains(FishBucketItem.KEY)) this.entityData.set(SPECIES, tag.getString(FishBucketItem.KEY));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Species", this.entityData.get(SPECIES));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Species")) this.entityData.set(SPECIES, tag.getString("Species"));
    }
}

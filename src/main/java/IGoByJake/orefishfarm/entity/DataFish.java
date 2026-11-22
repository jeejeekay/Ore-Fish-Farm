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
 *
 * <p>A species whose fish file has {@code "shear"} can be sheared like a sheep: it drops the coat's item, looks like
 * {@code models/fish/<fish>_sheared.json} and grows the coat back after the file's regrow time. A sheared fish stays
 * sheared when bucketed and let out again.
 */
public class DataFish extends OreFish implements net.minecraftforge.common.IForgeShearable {
    private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(DataFish.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(DataFish.class, EntityDataSerializers.BOOLEAN);
    private int regrow;

    public DataFish(EntityType<? extends AbstractFish> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SPECIES, "");
        this.entityData.define(SHEARED, false);
    }

    public boolean isSheared() {
        return this.entityData.get(SHEARED);
    }

    @Override
    public boolean isShearable(ItemStack shears, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return isAlive() && !isSheared() && IGoByJake.orefishfarm.data.FishData.shear(fishItem()) != null;
    }

    @Override
    public java.util.List<ItemStack> onSheared(@javax.annotation.Nullable net.minecraft.world.entity.player.Player player, ItemStack shears,
                                                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, int fortune) {
        var sh = IGoByJake.orefishfarm.data.FishData.shear(fishItem());
        if (sh == null || isSheared()) return java.util.List.of();
        level.playSound(null, this, net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                player == null ? net.minecraft.sounds.SoundSource.BLOCKS : net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1F);
        if (level.isClientSide) return java.util.List.of();
        this.entityData.set(SHEARED, true);
        this.regrow = sh.regrow();
        int n = sh.min() + this.random.nextInt(sh.max() - sh.min() + 1);
        return n <= 0 ? java.util.List.of() : java.util.List.of(new ItemStack(sh.drop(), n));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && isSheared() && --this.regrow <= 0) this.entityData.set(SHEARED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SPECIES.equals(key)) refreshDimensions();
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
        if (isSheared()) {
            bucket.getOrCreateTag().putBoolean("Sheared", true);
            bucket.getOrCreateTag().putInt("Regrow", this.regrow);
        }
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        if (tag.contains(FishBucketItem.KEY)) this.entityData.set(SPECIES, tag.getString(FishBucketItem.KEY));
        this.entityData.set(SHEARED, tag.getBoolean("Sheared"));
        this.regrow = tag.getInt("Regrow");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Species", this.entityData.get(SPECIES));
        tag.putBoolean("Sheared", isSheared());
        tag.putInt("Regrow", this.regrow);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Species")) this.entityData.set(SPECIES, tag.getString("Species"));
        this.entityData.set(SHEARED, tag.getBoolean("Sheared"));
        this.regrow = tag.getInt("Regrow");
    }
}

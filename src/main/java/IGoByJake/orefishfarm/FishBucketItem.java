package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A live fish in a bucket: the one bucket item for every species that has no bucket of its own (another mod's
 * fish can name its own bucket in its fish file instead). The species is stored as the fish item's id under
 * "Fish"; the rest of the tag is the mob's bucket data, so whatever the fish carries travels with it.
 *
 * <p>Emptied into the world it places water and lets the fish out (if the species has a mob with the same id).
 * Fish Tanks hold these buckets as they are. Only a fish in a bucket is alive, so it is what recipes, tanks and
 * (later) the Fish Checker use.
 */
public class FishBucketItem extends MobBucketItem {
    public static final String KEY = "Fish";

    public FishBucketItem(Properties props) {
        super(() -> Registry.FISH_MOBS.get(0).get(), () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH, props);
    }

    /** A bucket holding a fresh fish of this species. */
    public static ItemStack of(Item species) {
        ItemStack s = new ItemStack(Registry.FISH_BUCKET.get());
        s.getOrCreateTag().putString(KEY, String.valueOf(ForgeRegistries.ITEMS.getKey(species)));
        return s;
    }

    /** The species in a Fish Bucket, or null for any other stack. */
    @Nullable
    public static Item species(ItemStack stack) {
        if (!(stack.getItem() instanceof FishBucketItem)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY)) return null;
        ResourceLocation id = Renamed.fix(ResourceLocation.tryParse(tag.getString(KEY)));
        if (id == null) return null;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    @Override
    public Component getName(ItemStack stack) {
        Item species = species(stack);
        return species == null ? super.getName(stack) : Component.translatable("item.orefishfarm.fish_bucket.of", species.getDescription());
    }

    /** Let the fish out where the water went, if the species has a mob. */
    @Override
    public void checkExtraContent(@Nullable Player player, Level level, ItemStack stack, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        Item species = species(stack);
        if (species == null) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(species);
        if (id == null) return;
        // The species' own mob, or the shared data fish for species without one.
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : Registry.DATA_FISH_MOB.get();
        if (type == null) return;
        Entity e = type.spawn(server, stack, null, pos, MobSpawnType.BUCKET, true, false);
        if (e instanceof Bucketable b) {
            b.loadFromBucketTag(stack.getOrCreateTag());
            b.setFromBucket(true);
        }
        if (e != null) level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return IGoByJake.orefishfarm.client.FishBucketRenderer.get();
            }
        });
    }

    /** Crafting with a fish bucket (into its raw ore, say) leaves the empty bucket. */
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return new ItemStack(FishData.habitat(FishData.habitatOf(stack)).empty());
    }

    /** The habitat bucket this fish's bucket looks like (water, lava or void). */
    public static ItemStack look(ItemStack stack) {
        return new ItemStack(FishData.habitat(FishData.habitatOf(stack)).bucket());
    }
}

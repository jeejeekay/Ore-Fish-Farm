package IGoByJake.orefishfarm.loot;

import IGoByJake.orefishfarm.data.FishData;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

/**
 * Live catches: when any fishing (water, lava, void, any mod's) pulls up a tank fish and the angler carries that
 * fish's habitat bucket (a water bucket for water fish, a lava bucket for heated fish, a Void Bucket for void
 * fish), the fish comes up alive in it and that bucket is used up. Without one it is a plain fish.
 * Runs on every loot table; it only does anything when a fishing bobber is the one looting.
 */
public class LiveCatchModifier extends LootModifier {
    public static final Supplier<Codec<LiveCatchModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(i ->
            codecStart(i).apply(i, LiveCatchModifier::new)));

    public LiveCatchModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        Entity e = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(e instanceof FishingHook hook)) return loot;
        Player player = hook.getPlayerOwner();
        if (player == null) return loot;
        for (int i = 0; i < loot.size(); i++) {
            ItemStack s = loot.get(i);
            if (FishData.isBucketed(s)) continue;
            FishData.Fish f = FishData.fish(s);
            if (f == null) continue;
            if (takeOne(player.getInventory(), FishData.habitat(f.habitat()))) loot.set(i, FishData.bucketOf(f));
        }
        return loot;
    }

    private static boolean takeOne(Inventory inv, FishData.Habitat h) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(h.bucket())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}

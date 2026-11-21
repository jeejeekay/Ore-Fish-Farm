package IGoByJake.orefishfarm.loot;

import IGoByJake.orefishfarm.tackle.Tackle;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

/**
 * The tackle on the rod (the loot's tool) can swap the catch for a fish: water fishing
 * ({@code gameplay/fishing}), lava fishing ({@code .../lava_fishing...}) and void fishing ({@code .../void_fishing...})
 * tables only, filtered by the bonus's liquid and dimension. See {@link Tackle}.
 */
public class TackleModifier extends LootModifier {
    public static final Supplier<Codec<TackleModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(i ->
            codecStart(i).apply(i, TackleModifier::new)));

    public TackleModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    /** The liquid a fishing table is for, or null for any other table. */
    static String liquidOf(ResourceLocation table) {
        if (table == null) return null;
        String p = table.getPath();
        if (p.startsWith("gameplay/fishing")) return "water";
        if (p.contains("lava_fishing")) return "lava";
        if (p.contains("void_fishing")) return "void";
        return null;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        String liquid = liquidOf(ctx.getQueriedLootTableId());
        if (liquid == null) return loot;
        ItemStack rod = ctx.getParamOrNull(LootContextParams.TOOL);
        if (rod == null) return loot;
        ResourceLocation dim = ctx.getLevel().dimension().location();
        for (Tackle.Bonus b : Tackle.bonusesOf(Tackle.get(rod))) {
            if (b.liquid() != null && !b.liquid().equals(liquid)) continue;
            if (b.dimension() != null && !b.dimension().equals(dim)) continue;
            if (ctx.getRandom().nextFloat() < b.chance()) {
                ObjectArrayList<ItemStack> out = new ObjectArrayList<>();
                out.add(new ItemStack(b.fish()));
                return out;
            }
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}

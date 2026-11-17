package IGoByJake.orefishfarm.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.List;
import java.util.function.Supplier;

/**
 * Lets ordinary fishing catch ore fish: each entry has a chance to replace the catch. The list lives in
 * data/orefishfarm/loot_modifiers/fish_in_fishing.json, so a pack can change or empty it (Quicreate keeps
 * only the obsidian fish there and puts the ore fish in nether lava instead).
 */
public class AddFishModifier extends LootModifier {
    public record Entry(Item item, float chance) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Entry::item),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Entry::chance)
        ).apply(i, Entry::new));
    }

    public static final Supplier<Codec<AddFishModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(i ->
            codecStart(i).and(Entry.CODEC.listOf().fieldOf("fish").forGetter(m -> m.fish)).apply(i, AddFishModifier::new)));

    private final List<Entry> fish;

    public AddFishModifier(LootItemCondition[] conditions, List<Entry> fish) {
        super(conditions);
        this.fish = fish;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        for (Entry e : this.fish) {
            if (ctx.getRandom().nextFloat() < e.chance()) {
                ObjectArrayList<ItemStack> out = new ObjectArrayList<>();
                out.add(new ItemStack(e.item()));
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

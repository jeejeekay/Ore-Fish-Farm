package IGoByJake.orefishfarm.compat.jei;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The JEI entries, read from FishData (chances already include the config multipliers). */
public final class TankRecipes {
    private TankRecipes() {}

    public record Product(ItemStack fish, List<ItemStack> product, float chance, List<ItemStack> foods) {}

    /**
     * One breeding pair and what it can make. results: the baby species (one for a same-species pair, either parent's
     * for a mixed pair), or the recipe fish for a hybrid. genes: the traits a hybrid baby must inherit.
     */
    public record Breeding(ItemStack a, ItemStack b, List<ItemStack> results, float chance, boolean hybrid, List<ItemStack> foods,
                           List<net.minecraft.resources.ResourceLocation> genes) {}

    public record Power(ItemStack fish, int fePerTick, List<ItemStack> foods) {}

    private static List<FishData.Fish> sorted() {
        List<FishData.Fish> list = new ArrayList<>(FishData.allFish());
        list.sort(Comparator.comparing(f -> String.valueOf(ForgeRegistries.ITEMS.getKey(f.item()))));
        return list;
    }

    public static List<Product> products() {
        List<ItemStack> foods = FishData.allFoods();
        List<Product> out = new ArrayList<>();
        for (FishData.Fish f : sorted()) {
            if (f.makesProduct()) out.add(new Product(FishData.bucketOf(f), FishData.productChoices(f), FishData.productChance(f), foods));
        }
        return out;
    }

    /**
     * Every pair of fish that can breed (FishData.canPair), each pair once (A + B, never B + A again), with the
     * species its babies can be; then every recipe (hybrid). Looking up a fish's recipes finds every pair that makes it.
     */
    public static List<Breeding> breeding() {
        List<ItemStack> foods = FishData.allFoods();
        List<Breeding> out = new ArrayList<>();
        List<FishData.Fish> all = sorted();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i; j < all.size(); j++) {
                FishData.Fish fa = all.get(i), fb = all.get(j);
                if (!FishData.canPair(fa, fb)) continue;
                if (fa.item() == fb.item()) {
                    out.add(new Breeding(FishData.bucketOf(fa), FishData.bucketOf(fb), List.of(FishData.bucketOf(fa)), FishData.sameSpeciesChance(), false, foods, List.of()));
                } else { // either parent's species (a recipe pair's babies that miss its traits are plain ones too)
                    out.add(new Breeding(FishData.bucketOf(fa), FishData.bucketOf(fb), List.of(FishData.bucketOf(fa), FishData.bucketOf(fb)), FishData.crossChance(), false, foods, List.of()));
                }
            }
        }
        for (FishData.Cross x : FishData.allCrosses()) {
            out.add(new Breeding(bucket(x.a()), bucket(x.b()), List.of(bucket(x.result().getItem())), FishData.hybridChance(x), true, foods, x.traits()));
        }
        return out;
    }

    public static List<Power> power() {
        List<ItemStack> foods = FishData.allFoods();
        List<Power> out = new ArrayList<>();
        for (FishData.Fish f : sorted()) {
            if (f.makesPower()) out.add(new Power(FishData.bucketOf(f), FishData.powerPerTick(f), foods));
        }
        return out;
    }

    /** A species shown as its live, bucketed form (what tanks hold). */
    private static ItemStack bucket(net.minecraft.world.item.Item item) {
        FishData.Fish f = FishData.fish(new ItemStack(item));
        return f != null ? FishData.bucketOf(f) : new ItemStack(item);
    }
}

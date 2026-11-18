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

    public record Product(ItemStack fish, ItemStack product, float chance, List<ItemStack> foods) {}

    public record Breeding(ItemStack a, ItemStack b, ItemStack result, float chance, boolean hybrid, List<ItemStack> foods) {}

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
            if (f.makesProduct()) out.add(new Product(FishData.bucketOf(f), f.product().copy(), FishData.productChance(f), foods));
        }
        return out;
    }

    public static List<Breeding> breeding() {
        List<ItemStack> foods = FishData.allFoods();
        List<Breeding> out = new ArrayList<>();
        for (FishData.Fish f : sorted()) {
            float c = FishData.breedChance(f);
            if (c > 0) out.add(new Breeding(FishData.bucketOf(f), FishData.bucketOf(f), FishData.bucketOf(f), c, false, foods));
        }
        for (FishData.Cross x : FishData.allCrosses()) {
            out.add(new Breeding(bucket(x.a()), bucket(x.b()), bucket(x.result().getItem()), FishData.crossChance(x), true, foods));
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

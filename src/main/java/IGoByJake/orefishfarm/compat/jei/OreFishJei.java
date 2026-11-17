package IGoByJake.orefishfarm.compat.jei;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI pages for the three tanks, built from the fish files (FishData): what each fish makes in a Fish Tank and
 * how often, what two fish breed into, and how much power a power fish makes. On a server the fish list arrives
 * after JEI has started, so the pages are added then ({@link #lateRecipes}).
 */
@JeiPlugin
public class OreFishJei implements IModPlugin {
    public static final RecipeType<TankRecipes.Product> PRODUCT = RecipeType.create(OreFishFarm.MODID, "fish_tank", TankRecipes.Product.class);
    public static final RecipeType<TankRecipes.Breeding> BREEDING = RecipeType.create(OreFishFarm.MODID, "breeding_tank", TankRecipes.Breeding.class);
    public static final RecipeType<TankRecipes.Power> POWER = RecipeType.create(OreFishFarm.MODID, "power_tank", TankRecipes.Power.class);

    private static IJeiRuntime runtime;
    private static boolean added;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(OreFishFarm.MODID, "tanks");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        var gui = reg.getJeiHelpers().getGuiHelper();
        reg.addRecipeCategories(new TankCategories.ProductCategory(gui), new TankCategories.BreedingCategory(gui), new TankCategories.PowerCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        added = FishData.loaded();
        if (!added) return;
        reg.addRecipes(PRODUCT, TankRecipes.products());
        reg.addRecipes(BREEDING, TankRecipes.breeding());
        reg.addRecipes(POWER, TankRecipes.power());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(Registry.FISH_TANK.get()), PRODUCT);
        reg.addRecipeCatalyst(new ItemStack(Registry.BREEDING_TANK.get()), BREEDING);
        reg.addRecipeCatalyst(new ItemStack(Registry.POWER_TANK.get()), POWER);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime rt) {
        runtime = rt;
        lateRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        added = false;
    }

    /** Adds the pages if JEI is running and they weren't there when it started. */
    public static void lateRecipes() {
        if (runtime == null || added || !FishData.loaded()) return;
        added = true;
        var rm = runtime.getRecipeManager();
        rm.addRecipes(PRODUCT, new ArrayList<>(TankRecipes.products()));
        rm.addRecipes(BREEDING, new ArrayList<>(TankRecipes.breeding()));
        rm.addRecipes(POWER, new ArrayList<>(TankRecipes.power()));
    }

}

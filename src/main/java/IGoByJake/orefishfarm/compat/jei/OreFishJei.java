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
    public static final RecipeType<TackleCategory.Page> TACKLE = RecipeType.create(OreFishFarm.MODID, "tackle", TackleCategory.Page.class);
    private static boolean tackleAdded;

    private static IJeiRuntime runtime;
    private static boolean added;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(OreFishFarm.MODID, "tanks");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        var gui = reg.getJeiHelpers().getGuiHelper();
        reg.addRecipeCategories(new TankCategories.ProductCategory(gui), new TankCategories.BreedingCategory(gui), new TankCategories.PowerCategory(gui), new TackleCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        info(reg, Registry.FISH_TANK.get(), "fish_tank");
        info(reg, Registry.DYNAMO_COIL.get(), "dynamo_coil");
        info(reg, Registry.BRINE_FLAKES.get(), "habitat_food");
        info(reg, Registry.EMBER_PELLETS.get(), "habitat_food");
        info(reg, Registry.VOID_FLAKES.get(), "habitat_food");
        info(reg, Registry.FISH_BONE.get(), "fish_bone");
        info(reg, Registry.LAVA_ROD.get(), "lava_rod");
        info(reg, Registry.FISH_BUCKET.get(), "fish_bucket");
        info(reg, Registry.VOID_ROD.get(), "void_rod");
        info(reg, IGoByJake.orefishfarm.voidliquid.VoidLiquid.BUCKET.get(), "void_bucket");
        added = FishData.loaded();
        if (!added) return;
        reg.addRecipes(PRODUCT, TankRecipes.products());
        reg.addRecipes(BREEDING, TankRecipes.breeding());
        reg.addRecipes(POWER, TankRecipes.power());
        if (IGoByJake.orefishfarm.tackle.Tackle.loaded()) {
            tackleAdded = true;
            reg.addRecipes(TACKLE, TackleCategory.pages());
        }
    }

    /** Each species' Fish Bucket is its own entry in JEI. */
    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration reg) {
        reg.registerSubtypeInterpreter(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Registry.FISH_BUCKET.get(), (stack, ctx) -> {
            var t = stack.getTag();
            return t != null && t.contains(IGoByJake.orefishfarm.FishBucketItem.KEY) ? t.getString(IGoByJake.orefishfarm.FishBucketItem.KEY) : "";
        });
    }

    private static void info(IRecipeRegistration reg, net.minecraft.world.level.ItemLike item, String key) {
        reg.addIngredientInfo(new ItemStack(item), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                net.minecraft.network.chat.Component.translatable("jei.orefishfarm.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(Registry.FISH_TANK.get()), PRODUCT);
        reg.addRecipeCatalyst(new ItemStack(Registry.FISH_TANK.get()), BREEDING);
        reg.addRecipeCatalyst(new ItemStack(Registry.FISH_TANK.get()), BREEDING);
        reg.addRecipeCatalyst(new ItemStack(Registry.DYNAMO_COIL.get()), POWER);
        reg.addRecipeCatalyst(new ItemStack(Registry.FISH_TANK.get()), POWER);
        reg.addRecipeCatalyst(new ItemStack(net.minecraft.world.item.Items.FISHING_ROD), TACKLE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime rt) {
        runtime = rt;
        lateRecipes();
        lateTackle();
    }

    /** Adds the tackle pages once the server's tackle data has arrived (if JEI started before it did). */
    public static void lateTackle() {
        if (runtime == null || tackleAdded || !IGoByJake.orefishfarm.tackle.Tackle.loaded()) return;
        tackleAdded = true;
        var pages = TackleCategory.pages();
        runtime.getRecipeManager().addRecipes(TACKLE, pages);
        // An info page on each fish that needs tackle: "Needs the Squid Spinner on the rod: Water · 6%".
        java.util.Map<net.minecraft.world.item.Item, List<net.minecraft.network.chat.FormattedText>> lines = new java.util.LinkedHashMap<>();
        for (TackleCategory.Page p : pages)
            lines.computeIfAbsent(p.bonus().fish(), k -> new ArrayList<>(List.of(net.minecraft.network.chat.Component.translatable("jei.orefishfarm.info.fishing"))))
                    .add(net.minecraft.network.chat.Component.translatable("jei.orefishfarm.info.needs_tackle", p.tackle().getHoverName(), TackleCategory.where(p.bonus())));
        List<mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe> infos = new ArrayList<>();
        var im = runtime.getIngredientManager();
        lines.forEach((fish, text) -> im.createTypedIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(fish))
                .ifPresent(t -> infos.add(new TackleInfo(List.of(t), List.copyOf(text)))));
        if (!infos.isEmpty()) runtime.getRecipeManager().addRecipes(mezz.jei.api.constants.RecipeTypes.INFORMATION, infos);
    }

    private record TackleInfo(List<mezz.jei.api.ingredients.ITypedIngredient<?>> getIngredients,
                              List<net.minecraft.network.chat.FormattedText> getDescription)
            implements mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe {}

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        added = false;
        tackleAdded = false;
    }

    /** Adds the pages if JEI is running and they weren't there when it started. */
    public static void lateRecipes() {
        if (runtime == null || added || !FishData.loaded()) return;
        added = true;
        IJeiRuntime rt = runtime;
        var rm = rt.getRecipeManager();
        rm.addRecipes(PRODUCT, new ArrayList<>(TankRecipes.products()));
        rm.addRecipes(BREEDING, new ArrayList<>(TankRecipes.breeding()));
        rm.addRecipes(POWER, new ArrayList<>(TankRecipes.power()));
        // Fish Buckets for data-pack fish (e.g. Quicreate's glass eel) that JEI didn't get from the creative tab.
        var im = rt.getIngredientManager();
        java.util.Set<String> have = new java.util.HashSet<>();
        for (ItemStack s : im.getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK))
            if (s.is(Registry.FISH_BUCKET.get()) && s.getTag() != null) have.add(s.getTag().getString(IGoByJake.orefishfarm.FishBucketItem.KEY));
        List<ItemStack> missing = new ArrayList<>();
        for (ItemStack s : FishData.extraBuckets())
            if (!have.contains(s.getOrCreateTag().getString(IGoByJake.orefishfarm.FishBucketItem.KEY))) missing.add(s);
        if (!missing.isEmpty()) im.addIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, missing);
    }

}

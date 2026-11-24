package IGoByJake.orefishfarm.compat.jei;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Layout shared by the three pages: fish slot(s) on the left, the food that runs the tank under them, an arrow,
 * then the result, with the chance (or FE/t) written under the arrow.
 */
public final class TankCategories {
    private TankCategories() {}

    static final ResourceLocation TEX = new ResourceLocation(OreFishFarm.MODID, "textures/gui/tank.png");
    static final int W = 130, H = 52;

    abstract static class Base<T> implements IRecipeCategory<T> {
        protected final IDrawable background;
        protected final IDrawable icon;
        protected final IDrawable slot;
        protected final IDrawable arrow;

        Base(IGuiHelper gui, ItemLike iconItem) {
            this.background = gui.createBlankDrawable(W, H);
            this.icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(iconItem));
            this.slot = gui.getSlotDrawable();
            this.arrow = gui.createDrawable(TEX, 176, 62, 22, 15);
        }

        public int getWidth() { return W; }

        public int getHeight() { return H; }

        @SuppressWarnings("removal")
        public IDrawable getBackground() { return this.background; }

        @Override
        public IDrawable getIcon() { return this.icon; }

        protected void text(GuiGraphics g, Component c, int x, int y) {
            g.drawString(Minecraft.getInstance().font, c, x, y, 0xFF404040, false);
        }

        protected void centred(GuiGraphics g, Component c, int cx, int y) {
            var font = Minecraft.getInstance().font;
            g.drawString(font, c, cx - font.width(c) / 2, y, 0xFF404040, false);
        }

        protected static String pct(float chance) {
            float p = chance * 100;
            return p >= 10 || p == Math.round(p) ? String.valueOf(Math.round(p)) : String.format("%.1f", p);
        }
    }

    // ---- Fish Tank: fish (+ food) -> product, X% per bite
    static class ProductCategory extends Base<TankRecipes.Product> {
        ProductCategory(IGuiHelper gui) { super(gui, Registry.FISH_TANK.get()); }

        @Override public RecipeType<TankRecipes.Product> getRecipeType() { return OreFishJei.PRODUCT; }

        @Override public Component getTitle() { return Component.translatable("jei.orefishfarm.fish_tank"); }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, TankRecipes.Product r, IFocusGroup focuses) {
            // fish -> product, centred across the page
            b.addSlot(RecipeIngredientRole.INPUT, 28, 8).setBackground(this.slot, -1, -1).addItemStack(r.fish());
            b.addSlot(RecipeIngredientRole.OUTPUT, 86, 8).setBackground(this.slot, -1, -1).addItemStacks(r.product());
        }

        @Override
        public void draw(TankRecipes.Product r, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            this.arrow.draw(g, 54, 8);
            String secs = new java.text.DecimalFormat("0.#").format(IGoByJake.orefishfarm.Config.BITE_TICKS.get() / 20.0);
            centred(g, Component.translatable("jei.orefishfarm.per_food", pct(r.chance()), secs), W / 2, 30);
        }
    }

    // ---- Breeding Tank: fish + fish (+ food) -> baby or hybrid, X% per bite
    static class BreedingCategory extends Base<TankRecipes.Breeding> {
        BreedingCategory(IGuiHelper gui) { super(gui, Registry.FISH_TANK.get().asItem()); }

        @Override public RecipeType<TankRecipes.Breeding> getRecipeType() { return OreFishJei.BREEDING; }

        @Override public Component getTitle() { return Component.translatable("jei.orefishfarm.breeding_tank"); }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, TankRecipes.Breeding r, IFocusGroup focuses) {
            // parent + parent -> baby (or either baby), centred across the page
            b.addSlot(RecipeIngredientRole.INPUT, 8, 8).setBackground(this.slot, -1, -1).addItemStack(r.a());
            b.addSlot(RecipeIngredientRole.INPUT, 34, 8).setBackground(this.slot, -1, -1).addItemStack(r.b());
            for (int i = 0; i < r.results().size(); i++)
                b.addSlot(RecipeIngredientRole.OUTPUT, (r.results().size() == 1 ? 98 : 88) + i * 20, 8).setBackground(this.slot, -1, -1).addItemStack(r.results().get(i));
        }

        @Override
        public void draw(TankRecipes.Breeding r, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            g.drawString(net.minecraft.client.Minecraft.getInstance().font, "+", 27, 12, 0xFF404040, false);
            this.arrow.draw(g, 58, 8);
            centred(g, Component.translatable(r.hybrid() ? "jei.orefishfarm.hybrid" : r.results().size() > 1 ? "jei.orefishfarm.mixed" : "jei.orefishfarm.baby", pct(r.chance())), W / 2, 30);
            if (!r.genes().isEmpty()) {
                // the genes the baby must inherit, each in its gene colour
                net.minecraft.network.chat.MutableComponent needs = Component.translatable("jei.orefishfarm.needs_genes");
                for (int i = 0; i < r.genes().size(); i++) {
                    var gene = r.genes().get(i);
                    needs.append(i == 0 ? " " : ", ").append(IGoByJake.orefishfarm.data.FishGenes.geneName(gene).copy()
                            .withStyle(s -> s.withColor(IGoByJake.orefishfarm.data.FishGenes.color(gene))));
                }
                centred(g, needs, W / 2, 42);
            }
        }
    }

    // ---- Power Tank: power fish (+ food) -> FE/t
    static class PowerCategory extends Base<TankRecipes.Power> {
        PowerCategory(IGuiHelper gui) { super(gui, Registry.DYNAMO_COIL.get()); }

        @Override public RecipeType<TankRecipes.Power> getRecipeType() { return OreFishJei.POWER; }

        @Override public Component getTitle() { return Component.translatable("jei.orefishfarm.power_tank"); }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, TankRecipes.Power r, IFocusGroup focuses) {
            b.addSlot(RecipeIngredientRole.INPUT, 28, 8).setBackground(this.slot, -1, -1).addItemStack(r.fish());
        }

        @Override
        public void draw(TankRecipes.Power r, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            this.arrow.draw(g, 54, 8);
            text(g, Component.literal("⚡ " + r.fePerTick() + " FE/t"), 82, 12);
            centred(g, Component.translatable("jei.orefishfarm.while_eating"), W / 2, 30);
        }
    }
}

package IGoByJake.orefishfarm.compat.jei;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.fishing.OreFishingHook;
import IGoByJake.orefishfarm.tackle.Tackle;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Tackle" pages: one per tackle bonus. The rod for the liquid and the tackle piece go in, the fish comes out, and
 * the line under the arrow says where and how often ("Water · 6%", "Overworld · Lava · 6%"). U on a piece shows
 * everything it can catch; R on a fish shows which tackle it needs.
 */
public final class TackleCategory implements IRecipeCategory<TackleCategory.Page> {
    public record Page(ItemStack tackle, Tackle.Bonus bonus) {}

    static final int W = 130, H = 46;
    private final IDrawable background, icon, slot, arrow;

    TackleCategory(IGuiHelper gui) {
        this.background = gui.createBlankDrawable(W, H);
        this.icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("orefishfarm", "squid_spinner"))));
        this.slot = gui.getSlotDrawable();
        this.arrow = gui.createDrawable(TankCategories.TEX, 176, 62, 22, 15);
    }

    /** Every bonus of every tackle piece, in data order. */
    static List<Page> pages() {
        List<Page> out = new ArrayList<>();
        for (Map.Entry<Item, List<Tackle.Bonus>> e : Tackle.all().entrySet())
            for (Tackle.Bonus b : e.getValue()) out.add(new Page(new ItemStack(e.getKey()), b));
        out.sort(java.util.Comparator.comparing((Page p) -> String.valueOf(BuiltInRegistries.ITEM.getKey(p.tackle().getItem()))));
        return out;
    }

    @Override public RecipeType<Page> getRecipeType() { return OreFishJei.TACKLE; }

    @Override public Component getTitle() { return Component.translatable("jei.orefishfarm.tackle"); }

    public int getWidth() { return W; }

    public int getHeight() { return H; }

    @SuppressWarnings("removal")
    public IDrawable getBackground() { return this.background; }

    @Override public IDrawable getIcon() { return this.icon; }

    /** The rods that fish in this bonus's liquid (any rod for no liquid). */
    private static List<ItemStack> rods(String liquid) {
        List<ItemStack> out = new ArrayList<>();
        if ("lava".equals(liquid) || "void".equals(liquid)) {
            var tag = "lava".equals(liquid) ? OreFishingHook.LAVA_RODS : OreFishingHook.VOID_RODS;
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> out.add(new ItemStack(h.value())));
        }
        if (out.isEmpty()) out.add(new ItemStack(Items.FISHING_ROD));
        return out;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Page p, IFocusGroup focuses) {
        b.addSlot(RecipeIngredientRole.CATALYST, 6, 6).setBackground(this.slot, -1, -1).addItemStacks(rods(p.bonus().liquid()));
        b.addSlot(RecipeIngredientRole.INPUT, 26, 6).setBackground(this.slot, -1, -1).addItemStack(p.tackle());
        b.addSlot(RecipeIngredientRole.OUTPUT, 98, 6).setBackground(this.slot, -1, -1).addItemStack(new ItemStack(p.bonus().fish()));
    }

    /** "Water · 6%", "Overworld · Lava · 6%". */
    static Component where(Tackle.Bonus b) {
        var c = Component.empty();
        if (b.dimension() != null) c.append(dimName(b.dimension())).append(" · ");
        c.append(Component.translatable("jei.orefishfarm.liquid." + (b.liquid() == null ? "any" : b.liquid())));
        return c.append(" · " + TankCategories.Base.pct(b.chance()) + "%");
    }

    private static Component dimName(ResourceLocation dim) {
        String key = "dimension." + dim.getNamespace() + "." + dim.getPath();
        if (net.minecraft.locale.Language.getInstance().has(key)) return Component.translatable(key);
        String p = dim.getPath().replace('_', ' ');
        return Component.literal(Character.toUpperCase(p.charAt(0)) + p.substring(1));
    }

    @Override
    public void draw(Page p, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
        this.arrow.draw(g, 56, 6);
        var font = Minecraft.getInstance().font;
        Component line = where(p.bonus());
        g.drawString(font, line, W / 2 - font.width(line) / 2, 30, 0xFF404040, false);
    }
}

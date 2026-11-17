package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.menu.TankMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The tank screens: a little aquarium window with the fish swimming in it, bubbles, a food meter and a status
 * line. Everything is drawn from one 256x256 sheet (gui/tank.png).
 */
public abstract class TankScreen<M extends TankMenu> extends AbstractContainerScreen<M> {
    protected static final ResourceLocation TEX = new ResourceLocation(OreFishFarm.MODID, "textures/gui/tank.png");
    // Aquarium window, in screen-relative pixels.
    protected static final int WX = 30, WY = 16, WW = 116, WH = 54;
    // Meter icon (top of the right column).
    protected static final int IX = 153, IY = 16;

    protected TankScreen(M menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelY = 4;
    }

    /** v of the meter icon row: 18 food flake, 32 heart, 46 bolt. */
    protected abstract int meterRow();

    /** Status text, or null when all is well. */
    protected abstract Component statusText();

    protected abstract void drawFish(GuiGraphics g, float t);

    protected abstract void addTooltips(List<Component> out, int mx, int my);

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        List<Component> tips = new ArrayList<>();
        int x = mx - this.leftPos, y = my - this.topPos;
        if (in(x, y, IX, IY, 14, 14)) {
            tips.add(Component.translatable("gui.orefishfarm.food_left", this.menu.get(0) / 10));
        }
        addTooltips(tips, x, y);
        if (!tips.isEmpty() && this.menu.getCarried().isEmpty() && this.hoveredSlot == null) {
            g.renderComponentTooltip(this.font, tips, mx, my);
        }
    }

    protected static boolean in(int x, int y, int rx, int ry, int w, int h) {
        return x >= rx && y >= ry && x < rx + w && y < ry + h;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int l = this.leftPos, t = this.topPos;
        g.blit(TEX, l, t, 0, 0, this.imageWidth, this.imageHeight);
        float time = (Util.getMillis() % 1_000_000L) / 1000F;

        // A thin stream of bubbles from an air stone by the left-hand kelp, behind the fish (fish don't blow bubbles).
        g.enableScissor(l + WX, t + WY, l + WX + WW, t + WY + WH);
        for (int k = 0; k < 4; k++) {
            int bx = l + WX + 13 + (int) (Mth.sin(time * 1.7F + k * 1.9F) * 1.5F);
            int by = t + WY + WH - 9 - (int) ((time * 11 + k * 12) % (WH - 10));
            if (k % 2 == 0) g.blit(TEX, bx, by, 204, 18, 4, 4);
            else g.blit(TEX, bx - 1, by, 210, 18, 6, 6);
        }
        g.disableScissor();

        // Fish, drawn as their item models, scaled up.
        g.enableScissor(l + WX, t + WY, l + WX + WW, t + WY + WH);
        drawFish(g, time);
        g.disableScissor();

        // Food meter: empty icon, then the full one cropped from the bottom.
        int row = meterRow();
        g.blit(TEX, l + IX, t + IY, 176, row, 14, 14);
        int left = Mth.ceil(this.menu.get(0) / 1000F * 14);
        if (left > 0) g.blit(TEX, l + IX, t + IY + 14 - left, 190, row + 14 - left, 14, left);

        drawExtraBg(g, l, t, time);
    }

    /** Extra slot frames, energy bars and so on. */
    protected void drawExtraBg(GuiGraphics g, int l, int t, float time) {}

    /** Fish length on screen, in pixels. */
    protected static final float FISH_PX = 34F;

    /** One fish swimming slowly from side to side across the window, turning round at each end. */
    protected void drawLoneSwimmer(GuiGraphics g, ItemStack fish, float t, float speed) {
        float off = FishRender.swimOffset(t, speed);
        float x = this.leftPos + WX + WW / 2F + 6 + off * 18;
        float y = this.topPos + WY + WH / 2F + 1 + Mth.sin(t * 0.8F) * 0.8F;
        FishRender.renderGui(g, fish, x, y, FISH_PX, FishRender.swimYaw(t, speed), FishRender.tailWag(t, 0));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
        Component s = statusText();
        if (s != null) {
            int w = this.font.width(s);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(this.font, s, WX + (WW - w) / 2, WY + 3, 0xFFFFFF, true);
            g.pose().popPose();
        }
    }
}

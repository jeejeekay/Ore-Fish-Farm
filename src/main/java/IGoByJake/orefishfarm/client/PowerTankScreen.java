package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.block.PowerTankBlockEntity;
import IGoByJake.orefishfarm.menu.PowerTankMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class PowerTankScreen extends TankScreen<PowerTankMenu> {
    // Energy bar, along the bottom of the window (over the sand).
    private static final int EX = WX + 2, EY = WY + WH - 8, EW = 112, EH = 6;

    public PowerTankScreen(PowerTankMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected int meterRow() { return 18; }

    @Override
    protected Component statusText() {
        return switch (this.menu.get(1)) {
            case PowerTankBlockEntity.NO_FISH -> Component.translatable("gui.orefishfarm.status.no_power_fish");
            case PowerTankBlockEntity.NO_FOOD -> Component.translatable("gui.orefishfarm.status.no_food");
            case PowerTankBlockEntity.ENERGY_FULL -> Component.translatable("gui.orefishfarm.status.energy_full");
            default -> null;
        };
    }

    @Override
    protected void drawFish(GuiGraphics g, float t) {
        drawLoneSwimmer(g, this.menu.slots.get(0).getItem(), t, 0.2F);
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int l, int t, float time) {
        // Bolt lamp where the output slot would be: lit while generating, flickering a little.
        boolean on = this.menu.get(4) == 1;
        g.blit(TEX, l + 152, t + 35, on && (time * 8) % 1 > 0.15F ? 190 : 176, 46, 14, 14);
        // Energy bar.
        g.blit(TEX, l + EX, t + EY, 0, 166, EW, EH);
        int w = Math.round(this.menu.get(2) / 1000F * EW);
        if (w > 0) g.blit(TEX, l + EX, t + EY, 0, 172, w, EH);
    }

    @Override
    protected void addTooltips(List<Component> out, int x, int y) {
        if (in(x, y, EX, EY, EW, EH) || in(x, y, 152, 35, 14, 14)) {
            out.add(Component.translatable("gui.orefishfarm.energy", this.menu.get(2) / 10F));
            out.add(Component.translatable("gui.orefishfarm.power_rate", this.menu.get(3)));
        }
    }
}

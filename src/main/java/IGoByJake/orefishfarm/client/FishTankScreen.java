package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.block.FishTankBlockEntity;
import IGoByJake.orefishfarm.menu.FishTankMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class FishTankScreen extends TankScreen<FishTankMenu> {
    public FishTankScreen(FishTankMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected int meterRow() { return 18; }

    @Override
    protected Component statusText() {
        return switch (this.menu.get(1)) {
            case FishTankBlockEntity.NO_FISH -> Component.translatable("gui.orefishfarm.status.no_fish");
            case FishTankBlockEntity.NO_FOOD -> Component.translatable("gui.orefishfarm.status.no_food");
            case FishTankBlockEntity.OUTPUT_FULL -> Component.translatable("gui.orefishfarm.status.output_full");
            case FishTankBlockEntity.MAKES_NOTHING -> Component.translatable("gui.orefishfarm.status.makes_nothing");
            default -> null;
        };
    }

    @Override
    protected void drawFish(GuiGraphics g, float t) {
        drawLoneSwimmer(g, this.menu.slots.get(0).getItem(), t, 0.25F);
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int l, int t, float time) {
        g.blit(TEX, l + 151, t + 34, 176, 0, 18, 18); // output slot
    }

    @Override
    protected void addTooltips(List<Component> out, int x, int y) {
        if (in(x, y, WX, WY, WW, WH) && this.menu.get(1) == FishTankBlockEntity.OK) {
            out.add(Component.translatable("gui.orefishfarm.product_chance", this.menu.get(2) / 10F));
        }
    }
}

package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.block.BreedingTankBlockEntity;
import IGoByJake.orefishfarm.menu.BreedingTankMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class BreedingTankScreen extends TankScreen<BreedingTankMenu> {
    public BreedingTankScreen(BreedingTankMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected int meterRow() { return 32; }

    @Override
    protected Component statusText() {
        return switch (this.menu.get(1)) {
            case BreedingTankBlockEntity.NEED_TWO -> Component.translatable("gui.orefishfarm.status.need_two");
            case BreedingTankBlockEntity.NO_FOOD -> Component.translatable("gui.orefishfarm.status.no_food");
            case BreedingTankBlockEntity.OUTPUT_FULL -> Component.translatable("gui.orefishfarm.status.output_full");
            case BreedingTankBlockEntity.CANT_BREED -> Component.translatable("gui.orefishfarm.status.cant_breed");
            default -> null;
        };
    }

    @Override
    protected void drawFish(GuiGraphics g, float t) {
        // Two fish facing each other, drifting together from side to side; the gap breathes a little.
        float drift = Mth.sin(t * 0.22F) * 10;
        float gap = 20 + Mth.sin(t * 0.5F) * 2;
        float cx = this.leftPos + WX + WW / 2F + 6 + drift;
        float cy = this.topPos + WY + WH / 2F + 3;
        FishRender.renderGui(g, this.menu.slots.get(0).getItem(), cx - gap, cy + Mth.sin(t * 0.8F) * 0.8F, 28F, 0, FishRender.tailWag(t, 0));
        FishRender.renderGui(g, this.menu.slots.get(1).getItem(), cx + gap, cy + Mth.sin(t * 0.8F + 1.3F) * 0.8F, 28F, 180, FishRender.tailWag(t, 1.7F));
        if (this.menu.get(1) == BreedingTankBlockEntity.OK) {
            // Little hearts drifting up between them.
            for (int k = 0; k < 3; k++) {
                float p = (t * 0.6F + k / 3F) % 1F;
                int hx = (int) (this.leftPos + WX + WW / 2F + 3 + Mth.sin(t * 0.22F) * 10 + Mth.sin(t * 2 + k * 2) * 3);
                int hy = this.topPos + WY + WH - 10 - (int) (p * (WH - 14));
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.blit(TEX, hx, hy, 204, 32, 7, 6);
                g.pose().popPose();
            }
        }
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int l, int t, float time) {
        g.blit(TEX, l + 7, t + 34, 176, 0, 18, 18);   // second fish slot
        g.blit(TEX, l + 151, t + 34, 176, 0, 18, 18); // output slot
    }

    @Override
    protected void addTooltips(List<Component> out, int x, int y) {
        if (in(x, y, WX, WY, WW, WH) && this.menu.get(1) == BreedingTankBlockEntity.OK) {
            out.add(Component.translatable(this.menu.get(3) == 1 ? "gui.orefishfarm.hybrid_chance" : "gui.orefishfarm.breed_chance",
                    this.menu.get(2) / 10F));
        }
    }
}

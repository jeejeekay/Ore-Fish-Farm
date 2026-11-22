package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.data.FishGenes;
import IGoByJake.orefishfarm.data.StatBars;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/** Draws {@link StatBars}: label, 10 boxes (level filled, potential outlined, the rest dark), "level/potential". */
public class StatBarsTooltip implements ClientTooltipComponent {
    private static final int BOX_W = 6, BOX_H = 8, STEP = 7, ROW = 10;
    private static final int EMPTY = 0xFF262626, OUTLINE = 0xFFB0B0B0, LABEL = 0xFFAAAAAA, NUMBER = 0xFF8A8A8A;
    private final StatBars bars;

    public StatBarsTooltip(StatBars bars) {
        this.bars = bars;
    }

    private static Component label(String stat) {
        return Component.translatable("stat.orefishfarm." + stat);
    }

    private int labelWidth(Font font) {
        int w = 0;
        for (StatBars.Row r : this.bars.rows()) w = Math.max(w, font.width(label(r.stat())));
        return w + 4;
    }

    @Override
    public int getHeight() {
        return this.bars.rows().size() * ROW + 2;
    }

    @Override
    public int getWidth(Font font) {
        return labelWidth(font) + FishGenes.MAX_LEVEL * STEP + 3 + font.width("10/10");
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        int lw = labelWidth(font);
        for (int i = 0; i < this.bars.rows().size(); i++) {
            StatBars.Row r = this.bars.rows().get(i);
            int ry = y + i * ROW;
            g.drawString(font, label(r.stat()), x, ry, LABEL, false);
            int color = 0xFF000000 | FishGenes.statColor(r.stat());
            for (int c = 0; c < FishGenes.MAX_LEVEL; c++) {
                int bx = x + lw + c * STEP, by = ry;
                int fill = c < r.level() ? color : EMPTY;
                if (c < r.potential()) {
                    g.fill(bx, by, bx + BOX_W, by + BOX_H, OUTLINE);
                    g.fill(bx + 1, by + 1, bx + BOX_W - 1, by + BOX_H - 1, fill);
                } else {
                    g.fill(bx, by, bx + BOX_W, by + BOX_H, fill);
                }
            }
            g.drawString(font, r.level() + "/" + r.potential(), x + lw + FishGenes.MAX_LEVEL * STEP + 3, ry, NUMBER, false);
        }
    }
}

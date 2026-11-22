package IGoByJake.orefishfarm.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A fish's stat bars in its tooltip: one row per shown stat, 10 boxes each. Boxes up to the level are filled in the
 * stat's colour, boxes up to the potential get a grey outline, the rest are dark grey, and the row ends with
 * "level/potential". The tooltip first gets a plain {@link #marker} line; the client swaps it for this
 * (client.StatBarsTooltip draws it).
 */
public record StatBars(List<Row> rows) implements TooltipComponent {
    public record Row(String stat, int level, int potential) {}

    public static StatBars of(ItemStack stack, List<String> stats) {
        List<Row> rows = new ArrayList<>();
        for (String s : stats) rows.add(new Row(s, FishGenes.level(stack, s), FishGenes.potential(stack, s)));
        return new StatBars(rows);
    }

    /** The plain-text stand-in, "Yield 3/7 · Satiety 4/6 ...", for anything that can't draw the bars. */
    public static Component marker(ItemStack stack, List<String> stats) {
        MutableComponent c = Component.empty().withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < stats.size(); i++) {
            String s = stats.get(i);
            if (i > 0) c.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.translatable("stat.orefishfarm." + s)).append(" " + FishGenes.level(stack, s) + "/" + FishGenes.potential(stack, s));
        }
        return c;
    }
}

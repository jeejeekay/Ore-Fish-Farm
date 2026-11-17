package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.block.TankBlockEntity;
import IGoByJake.orefishfarm.block.TankRole;
import IGoByJake.orefishfarm.data.FishData;
import IGoByJake.orefishfarm.menu.AquariumMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The aquarium screen: a window with up to six of its fish swimming in the habitat's colour, a status line,
 * two rows of fish slots with a scroll bar, the shared food and Love Lattice slots, and the output.
 */
public class AquariumScreen extends AbstractContainerScreen<AquariumMenu> {
    private static final ResourceLocation TEX = new ResourceLocation(OreFishFarm.MODID, "textures/gui/aquarium.png");
    private static final ResourceLocation ICONS = new ResourceLocation(OreFishFarm.MODID, "textures/gui/tank.png");
    private static final int WX = 8, WY = 16, WW = 162, WH = 34;
    private static final int SX = 174, SY = 53, SH = 36;
    private static final int MX = 174, MY = 16; // food meter
    private static final int EX = 177, EY = 33, EH = 17; // energy bar
    private boolean dragging;

    public AquariumScreen(AquariumMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 194;
        this.imageHeight = 238;
        this.inventoryLabelY = AquariumMenu.INV_Y + 2;
        this.titleLabelY = 5;
    }

    private boolean power() {
        return this.menu.controller.role() == TankRole.POWER;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        int x = mx - this.leftPos, y = my - this.topPos;
        List<Component> tips = new ArrayList<>();
        if (in(x, y, MX, MY, 14, 14)) tips.add(Component.translatable("gui.orefishfarm.food_left", this.menu.get(TankBlockEntity.D_FOOD) / 10));
        if (power() && in(x, y, EX, EY, 8, EH)) {
            tips.add(Component.translatable("gui.orefishfarm.energy", this.menu.get(TankBlockEntity.D_ENERGY) / 10));
            tips.add(Component.translatable("gui.orefishfarm.power_rate", this.menu.get(TankBlockEntity.D_RATE)).withStyle(ChatFormatting.GOLD));
        }
        if (in(x, y, WX, WY, WW, WH)) {
            tips.add(Component.translatable("gui.orefishfarm.aquarium_info", this.menu.get(TankBlockEntity.D_FISH), this.menu.get(TankBlockEntity.D_BLOCKS)));
            tips.add(Component.translatable("gui.orefishfarm.habitat", FishData.habitatName(this.menu.controller.habitat())).withStyle(ChatFormatting.AQUA));
            int l = this.menu.get(TankBlockEntity.D_LATTICES);
            if (l > 0) tips.add(Component.translatable("gui.orefishfarm.lattices", l).withStyle(ChatFormatting.LIGHT_PURPLE));
            if (this.menu.get(TankBlockEntity.D_PROTECTED) == 1) tips.add(Component.translatable("gui.orefishfarm.protected").withStyle(ChatFormatting.GREEN));
        }
        if (this.hoveredSlot instanceof AquariumMenu.FishSlot fs && this.menu.getCarried().isEmpty()) {
            if (fs.locked() && !fs.hasItem()) tips.add(Component.translatable("gui.orefishfarm.needs_lattice"));
            else if (fs.hasItem() && this.menu.controller.isForeign(fs.getItem())) {
                if (this.menu.get(TankBlockEntity.D_PROTECTED) == 1) tips.add(Component.translatable("gui.orefishfarm.kept_alive").withStyle(ChatFormatting.GREEN));
                else tips.add(Component.translatable("gui.orefishfarm.wrong_habitat", FishData.habitatName(FishData.habitatOf(fs.getItem()))).withStyle(ChatFormatting.RED));
            }
            if (!tips.isEmpty()) {
                List<Component> all = new ArrayList<>(getTooltipFromContainerItem(fs.getItem()));
                if (!fs.hasItem()) all.clear();
                all.addAll(tips);
                g.renderComponentTooltip(this.font, all, mx, my);
                return;
            }
        }
        if (!tips.isEmpty() && this.menu.getCarried().isEmpty() && this.hoveredSlot == null) g.renderComponentTooltip(this.font, tips, mx, my);
    }

    private static boolean in(int x, int y, int rx, int ry, int w, int h) {
        return x >= rx && y >= ry && x < rx + w && y < ry + h;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int l = this.leftPos, t = this.topPos;
        g.blit(TEX, l, t, 0, 0, this.imageWidth, this.imageHeight);
        float time = (Util.getMillis() % 1_000_000L) / 1000F;

        // Window: the habitat's colour, darker toward the bottom, a sand strip and a few bubbles.
        FishData.Habitat h = FishData.habitat(this.menu.controller.habitat());
        int c = h.color();
        int top = 0xFF000000 | shade(c, 1.15F), bottom = 0xFF000000 | shade(c, 0.55F);
        g.fillGradient(l + WX, t + WY, l + WX + WW, t + WY + WH, top, bottom);
        int sand = h.look().equals("lava") ? 0xFF5A2A1A : h.look().equals("void") ? 0xFFD8D6A0 : 0xFFD9C78F;
        g.fill(l + WX, t + WY + WH - 3, l + WX + WW, t + WY + WH, sand);
        g.enableScissor(l + WX, t + WY, l + WX + WW, t + WY + WH);
        for (int k = 0; k < 4; k++) {
            int bx = l + WX + 10 + (int) (Mth.sin(time * 1.7F + k * 1.9F) * 1.5F);
            int by = t + WY + WH - 6 - (int) ((time * 11 + k * 9) % (WH - 6));
            g.blit(ICONS, bx, by, k % 2 == 0 ? 204 : 210, 18, k % 2 == 0 ? 4 : 6, k % 2 == 0 ? 4 : 6);
        }
        // Up to six fish, each in its own lane.
        int shown = 0;
        for (int i = 0; i < this.menu.fishSlotCount() && shown < 6; i++) {
            AquariumMenu.FishRef r = this.menu.refByIndex(i);
            if (r == null) continue;
            ItemStack fish = r.be().fishStack(r.slot());
            if (fish.isEmpty()) continue;
            float speed = 0.22F + shown * 0.05F;
            float phase = shown * 2.3F;
            float off = FishRender.swimOffset(time + phase, speed);
            float fx = l + WX + WW / 2F + off * (WW / 2F - 14);
            float fy = t + WY + 9 + (shown % 3) * 8 + Mth.sin(time * 0.8F + phase) * 0.8F;
            FishRender.renderGui(g, fish, fx, fy, 20F, FishRender.swimYaw(time + phase, speed), FishRender.tailWag(time, phase));
            shown++;
        }
        g.disableScissor();

        // Food meter.
        g.blit(ICONS, l + MX, t + MY, 176, 18, 14, 14);
        int left = Mth.ceil(this.menu.get(TankBlockEntity.D_FOOD) / 1000F * 14);
        if (left > 0) g.blit(ICONS, l + MX, t + MY + 14 - left, 190, 18 + 14 - left, 14, left);

        // Energy bar (power tanks).
        if (power()) {
            g.blit(TEX, l + EX, t + EY, 224, 0, 8, EH);
            int e = Mth.ceil(this.menu.get(TankBlockEntity.D_ENERGY) / 1000F * EH);
            if (e > 0) g.blit(TEX, l + EX, t + EY + EH - e, 232, EH - e, 8, e);
        }

        // Scroll bar.
        int max = this.menu.maxRow();
        int knobY = max == 0 ? 0 : (int) ((SH - 15) * (this.menu.row() / (float) max));
        g.blit(TEX, l + SX + 1, t + SY + 1 + knobY, max == 0 ? 212 : 200, 0, 12, 15);

        // Slot extras: closed second slots, fish out of their habitat, empty food and lattice hints.
        boolean safe = this.menu.get(TankBlockEntity.D_PROTECTED) == 1;
        for (Slot s : this.menu.slots) {
            int sx = l + s.x, sy = t + s.y;
            if (s instanceof AquariumMenu.FishSlot fs) {
                if (!fs.isActive()) {
                    g.fill(sx, sy, sx + 16, sy + 16, 0x80373737);
                } else if (fs.locked() && !fs.hasItem()) {
                    g.blit(TEX, sx, sy, 200, 16, 16, 16);
                } else if (fs.hasItem() && this.menu.controller.isForeign(fs.getItem())) {
                    boolean flash = !safe && (Util.getMillis() / 300) % 2 == 0;
                    g.fill(sx, sy, sx + 16, sy + 16, safe ? 0x5040A0FF : flash ? 0x90FF2020 : 0x60FF2020);
                }
            } else if (s.index >= this.menu.sharedStart && s.index < this.menu.sharedStart + TankBlockEntity.FOOD + TankBlockEntity.UPGRADES && !s.hasItem()) {
                boolean upgrade = s.index >= this.menu.sharedStart + TankBlockEntity.FOOD;
                g.blit(TEX, sx, sy, upgrade ? 216 : 200, 32, 16, 16);
            }
        }
    }

    private static int shade(int rgb, float f) {
        int r = Mth.clamp((int) (((rgb >> 16) & 255) * f), 0, 255);
        int gr = Mth.clamp((int) (((rgb >> 8) & 255) * f), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * f), 0, 255);
        return (r << 16) | (gr << 8) | b;
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
            g.drawString(this.font, s, WX + (WW - w) / 2, WY + WH - 12, 0xFFFFFF, true);
            g.pose().popPose();
        }
    }

    private Component statusText() {
        return switch (this.menu.get(TankBlockEntity.D_STATUS)) {
            case TankBlockEntity.NO_FISH -> Component.translatable(power() ? "gui.orefishfarm.status.no_power_fish" : "gui.orefishfarm.status.no_fish");
            case TankBlockEntity.NO_POWER_FISH -> Component.translatable("gui.orefishfarm.status.no_power_fish");
            case TankBlockEntity.NO_FOOD -> Component.translatable("gui.orefishfarm.status.no_food");
            case TankBlockEntity.OUTPUT_FULL -> Component.translatable("gui.orefishfarm.status.output_full");
            case TankBlockEntity.ENERGY_FULL -> Component.translatable("gui.orefishfarm.status.energy_full");
            case TankBlockEntity.NEEDS_HABITAT_FOOD -> Component.translatable("gui.orefishfarm.status.needs_habitat_food",
                    this.menu.get(TankBlockEntity.D_DIES_IN)).withStyle(ChatFormatting.YELLOW);
            case TankBlockEntity.DYING -> Component.translatable("gui.orefishfarm.status.dying", this.menu.get(TankBlockEntity.D_DIES_IN)).withStyle(ChatFormatting.RED);
            default -> null;
        };
    }

    // ---- scrolling

    private void scrollTo(int row) {
        int r = Mth.clamp(row, 0, this.menu.maxRow());
        if (r == this.menu.row()) return;
        this.menu.setRow(r);
        if (this.minecraft != null && this.minecraft.gameMode != null) this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, r);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (this.menu.maxRow() > 0) {
            scrollTo(this.menu.row() - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && in((int) mx - this.leftPos, (int) my - this.topPos, SX, SY, 14, SH) && this.menu.maxRow() > 0) {
            this.dragging = true;
            dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private void dragTo(double my) {
        float f = (float) ((my - this.topPos - SY - 7.5) / (SH - 15));
        scrollTo(Math.round(Mth.clamp(f, 0F, 1F) * this.menu.maxRow()));
    }
}

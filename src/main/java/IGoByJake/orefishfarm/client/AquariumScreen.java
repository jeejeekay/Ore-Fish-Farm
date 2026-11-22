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
 * The aquarium screen: a window with up to six of its fish swimming in the habitat's colour, a status line, the
 * fish slots (only as many as the aquarium has, at most three rows, a scroll bar only when there are more), then one
 * line with the food slots, the breeding heart and the output slots. The middle of the screen is drawn here, so it
 * fits any row count; the top (title, window, food meter, energy) and the inventory come from the texture.
 * Empty fish slots show a faded bucket of cod, empty food slots a faded fish food. A newborn's slot shows a yellow
 * asterisk until it is hovered.
 */
public class AquariumScreen extends AbstractContainerScreen<AquariumMenu> {
    private static final ResourceLocation TEX = new ResourceLocation(OreFishFarm.MODID, "textures/gui/aquarium.png");
    private static final ResourceLocation ICONS = new ResourceLocation(OreFishFarm.MODID, "textures/gui/tank.png");
    private static final int WX = 8, WY = 16, WW = 162, WH = 34;
    private static final int SX = 174;
    /** Texture rows: a plain panel row (for the drawn middle), and where the inventory part starts and ends. */
    private static final int PLAIN_ROW = 141, TEX_INV_Y = 144, TEX_H = 238;
    private static final int MX = 174, MY = 16; // food meter
    private static final int EX = 177, EY = 33, EH = 17; // energy bar
    private boolean dragging;
    private BreedHeart breedButton;
    /** Newborns already reported as seen (so the server is told once). */
    private final java.util.Set<Integer> seenSent = new java.util.HashSet<>();

    public AquariumScreen(AquariumMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 194;
        this.imageHeight = menu.invY + (TEX_H - TEX_INV_Y);
        this.inventoryLabelY = menu.invY + 2;
        this.titleLabelY = 5;
    }

    @Override
    protected void init() {
        super.init();
        this.breedButton = addRenderableWidget(new BreedHeart(this.leftPos + 8 + AquariumMenu.BREED_COL * 18 - 1, this.topPos + this.menu.lineY - 1, b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null)
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, AquariumMenu.BREEDING_BUTTON);
        }));
        updateBreedButton();
    }

    /** No free fish slot: a baby would have nowhere to go. */
    private boolean breedingFull() {
        return this.menu.get(TankBlockEntity.D_FISH) >= this.menu.blocks * TankBlockEntity.FISH;
    }

    private boolean breedingOn() {
        return this.menu.get(TankBlockEntity.D_BREEDING) == 1;
    }

    private Component breedLabel() {
        if (breedingFull()) return Component.translatable("gui.orefishfarm.breeding_full");
        return Component.translatable(breedingOn() ? "gui.orefishfarm.breeding_on" : "gui.orefishfarm.breeding_off");
    }

    /** Shown only on aquariums big enough to breed in; greyed out when there's no room for a baby. */
    private void updateBreedButton() {
        if (this.breedButton == null) return;
        this.breedButton.visible = this.menu.blocks >= TankBlockEntity.BREED_MIN_BLOCKS;
        this.breedButton.active = !breedingFull();
        this.breedButton.pink = breedingOn() && !breedingFull();
        Component label = breedLabel();
        if (!label.equals(this.breedButton.getMessage())) {
            this.breedButton.setMessage(label);
            this.breedButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }
    }

    /** The breeding switch: a slot-sized button with a heart, pink when on, faded grey when off or full. */
    private static final class BreedHeart extends net.minecraft.client.gui.components.Button {
        private static final String[] HEART = {
                ".XX...XX.",
                "XXXX.XXXX",
                "XXXXXXXXX",
                "XXXXXXXXX",
                ".XXXXXXX.",
                "..XXXXX..",
                "...XXX...",
                "....X...."};
        boolean pink;

        BreedHeart(int x, int y, OnPress press) {
            super(x, y, 18, 18, Component.empty(), press, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int x = getX(), y = getY();
            boolean hot = this.active && isHoveredOrFocused();
            // A raised slot-sized button.
            g.fill(x, y, x + 18, y + 18, 0xFF373737);
            g.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
            g.fill(x + 1, y + 1, x + 17, y + 17, hot ? 0xFFD8D8D8 : 0xFFC6C6C6);
            int fill = this.pink ? (hot ? 0xFFFF8CC0 : 0xFFF06292) : 0xFFA8A8A8;
            int edge = this.pink ? 0xFF8E1F4E : 0xFF7A7A7A;
            int hx = x + 5, hy = y + 5;
            for (int r = 0; r < HEART.length; r++) {
                for (int c = 0; c < HEART[r].length(); c++) {
                    if (HEART[r].charAt(c) != 'X') continue;
                    boolean rim = !on(r - 1, c) || !on(r + 1, c) || !on(r, c - 1) || !on(r, c + 1);
                    g.fill(hx + c, hy + r, hx + c + 1, hy + r + 1, rim ? edge : fill);
                }
            }
            if (this.pink) g.fill(hx + 2, hy + 1, hx + 3, hy + 2, 0xFFFFD0E4); // shine
        }

        private static boolean on(int r, int c) {
            return r >= 0 && r < HEART.length && c >= 0 && c < HEART[r].length() && HEART[r].charAt(c) == 'X';
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateBreedButton();
    }

    /** Window height and scroll bar top for this aquarium's size. */
    private int wh() { return WH + this.menu.extra; }

    /** Scroll bar: top (the fish rows' frame) and height. */
    private int sy() { return this.menu.fishY - 1; }

    private int sh() { return this.menu.visRows * 18; }

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
        if (in(x, y, WX, WY, WW, wh())) {
            tips.add(Component.translatable("gui.orefishfarm.aquarium_info", this.menu.get(TankBlockEntity.D_FISH), this.menu.get(TankBlockEntity.D_BLOCKS)));
            tips.add(Component.translatable("gui.orefishfarm.habitat", FishData.habitatName(this.menu.controller.habitat())).withStyle(ChatFormatting.AQUA));
            if (this.menu.get(TankBlockEntity.D_PROTECTED) == 1) tips.add(Component.translatable("gui.orefishfarm.protected").withStyle(ChatFormatting.GREEN));
        }
        ItemStack carried = this.menu.getCarried();
        // Hovering a newborn marks it seen (tells the server once).
        if (this.hoveredSlot instanceof AquariumMenu.FishSlot seen && seen.hasItem() && IGoByJake.orefishfarm.data.FishGenes.isUnseen(seen.getItem())
                && this.seenSent.add(seen.index()) && this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, AquariumMenu.SEEN_BUTTON + seen.index());
        }
        if (!tips.isEmpty() && this.menu.getCarried().isEmpty() && this.hoveredSlot == null) g.renderComponentTooltip(this.font, tips, mx, my);
    }

    /**
     * A fish out of its habitat: the warning goes on the end of its tooltip's habitat line ("Habitat: Water ⚠ wrong
     * tank" in red, or "kept alive by habitat food" in green), so the normal tooltip (with its stat bars) stays the
     * only one.
     */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (!(this.hoveredSlot instanceof AquariumMenu.FishSlot fs) || !fs.hasItem() || fs.getItem() != stack
                || !this.menu.controller.isForeign(stack)) return lines;
        boolean kept = this.menu.get(TankBlockEntity.D_PROTECTED) == 1;
        Component note = kept ? Component.translatable("gui.orefishfarm.kept_alive_short").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.orefishfarm.wrong_habitat_short").withStyle(ChatFormatting.RED);
        List<Component> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals(IGoByJake.orefishfarm.TooltipEvents.HABITAT_KEY)) {
                out.set(i, out.get(i).copy().append(" ").append(note));
                return out;
            }
        }
        out.add(IGoByJake.orefishfarm.TooltipEvents.habitatLine(FishData.habitatOf(stack)).append(" ").append(note));
        return out;
    }

    private static boolean in(int x, int y, int rx, int ry, int w, int h) {
        return x >= rx && y >= ry && x < rx + w && y < ry + h;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int l = this.leftPos, t = this.topPos;
        // Background: the top down to the window's last row, that row repeated for a taller window, the window's
        // bottom edge, then a plain panel down to the inventory, which comes from the texture's bottom part.
        int e = this.menu.extra, cut = WY + WH - 1, edgeEnd = WY + WH + 3;
        g.blit(TEX, l, t, 0, 0, this.imageWidth, cut);
        for (int k = 0; k < e; k++) g.blit(TEX, l, t + cut + k, 0, cut - 1, this.imageWidth, 1);
        g.blit(TEX, l, t + cut + e, 0, cut, this.imageWidth, edgeEnd - cut);
        for (int y = edgeEnd + e; y < this.menu.invY; y++) g.blit(TEX, l, t + y, 0, PLAIN_ROW, this.imageWidth, 1);
        g.blit(TEX, l, t + this.menu.invY, 0, TEX_INV_Y, this.imageWidth, TEX_H - TEX_INV_Y);
        // Slot frames for the slots on show (fish, food, output).
        for (Slot sl : this.menu.slots) {
            if (sl.index >= this.menu.playerStart || !sl.isActive()) continue;
            slotFrame(g, l + sl.x - 1, t + sl.y - 1, 18, 18);
        }
        // Scroll bar, only when there are more fish rows than fit.
        if (this.menu.maxRow() > 0) slotFrame(g, l + SX, t + sy(), 14, sh());
        float time = (Util.getMillis() % 1_000_000L) / 1000F;
        int wh = wh();

        // Window: the habitat's colour, darker toward the bottom, a sand strip and a few bubbles.
        FishData.Habitat h = FishData.habitat(this.menu.controller.habitat());
        int c = h.color();
        int top = 0xFF000000 | shade(c, 1.15F), bottom = 0xFF000000 | shade(c, 0.55F);
        g.fillGradient(l + WX, t + WY, l + WX + WW, t + WY + wh, top, bottom);
        int sand = h.look().equals("lava") ? 0xFF5A2A1A : h.look().equals("void") ? 0xFFD8D6A0 : 0xFFD9C78F;
        g.fill(l + WX, t + WY + wh - 3, l + WX + WW, t + WY + wh, sand);
        g.enableScissor(l + WX, t + WY, l + WX + WW, t + WY + wh);
        for (int k = 0; k < 4; k++) {
            int bx = l + WX + 10 + (int) (Mth.sin(time * 1.7F + k * 1.9F) * 1.5F);
            int by = t + WY + wh - 6 - (int) ((time * 11 + k * 9) % (wh - 6));
            g.blit(ICONS, bx, by, k % 2 == 0 ? 204 : 210, 18, k % 2 == 0 ? 4 : 6, k % 2 == 0 ? 4 : 6);
        }
        g.disableScissor();
        // Fish bob in place, six to a row; a bigger aquarium shows more rows.
        int rows = 1 + e / 20, shown = 0;
        for (int i = 0; i < this.menu.fishSlotCount() && shown < rows * 6; i++) {
            AquariumMenu.FishRef r = this.menu.refByIndex(i);
            if (r == null) continue;
            ItemStack bucket = r.be().fishStack(r.slot());
            if (bucket.isEmpty()) continue;
            int row = shown / 6, col = shown % 6;
            float phase = i * 1.3F;
            int fx = l + WX + 16 + col * 26 + (row % 2) * 10;
            int fy = t + WY + 13 + row * 20 + Math.round(Mth.sin(time * 1.6F + phase) * 1.5F);
            drawFish(g, bucket, fx, fy);
            shown++;
        }

        // Food meter.
        g.blit(ICONS, l + MX, t + MY, 176, 18, 14, 14);
        int left = Mth.ceil(this.menu.get(TankBlockEntity.D_FOOD) / 1000F * 14);
        if (left > 0) g.blit(ICONS, l + MX, t + MY + 14 - left, 190, 18 + 14 - left, 14, left);

        // Energy bar (power tanks).
        if (power()) {
            g.blit(TEX, l + EX, t + EY, 224, 0, 8, EH);
            int en = Mth.ceil(this.menu.get(TankBlockEntity.D_ENERGY) / 1000F * EH);
            if (en > 0) g.blit(TEX, l + EX, t + EY + EH - en, 232, EH - en, 8, en);
        }

        // Scroll knob.
        int max = this.menu.maxRow();
        if (max > 0) {
            int knobY = (int) ((sh() - 17) * (this.menu.row() / (float) max));
            g.blit(TEX, l + SX + 1, t + sy() + 1 + knobY, 200, 0, 12, 15);
        }

        // Carrying a live fish: the free slots it could go in light up.
        if (FishData.isFish(this.menu.getCarried())) {
            for (Slot s : this.menu.slots) {
                if (s instanceof AquariumMenu.FishSlot fs && fs.isActive() && !fs.hasItem()) {
                    g.fill(l + s.x, t + s.y, l + s.x + 16, t + s.y + 16, 0x4060E060);
                }
            }
        }
        // Slot extras: fish out of their habitat, empty food hints.
        boolean safe = this.menu.get(TankBlockEntity.D_PROTECTED) == 1;
        for (Slot s : this.menu.slots) {
            int sx = l + s.x, sy = t + s.y;
            if (s instanceof AquariumMenu.FishSlot fs) {
                if (!fs.isActive()) continue;
                if (!fs.hasItem()) {
                    ghost(g, COD_BUCKET, sx, sy); // an empty fish slot: a faded bucket of cod
                } else if (this.menu.controller.isForeign(fs.getItem())) {
                    boolean flash = !safe && (Util.getMillis() / 300) % 2 == 0;
                    g.fill(sx, sy, sx + 16, sy + 16, safe ? 0x5040A0FF : flash ? 0x90FF2020 : 0x60FF2020);
                }
            } else if (s.index >= this.menu.sharedStart && s.index < this.menu.foodEnd() && !s.hasItem()) {
                g.blit(TEX, sx, sy, 200, 32, 16, 16);
            }
        }
    }

    private static final ItemStack COD_BUCKET = new ItemStack(net.minecraft.world.item.Items.COD_BUCKET);

    /** An item drawn faded into a slot, as a hint of what goes there. */
    private static void ghost(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderFakeItem(stack, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + 16, y + 16, 0xB08B8B8B);
        g.pose().popPose();
    }

    /** A vanilla-style slot frame: dark top-left, white bottom-right, grey inside. */
    private static void slotFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    /** The mob from the bucket, side-on and about 20 pixels long, centred on (x, y); the bucket if it has no mob. */
    private static void drawFish(GuiGraphics g, ItemStack bucket, int x, int y) {
        net.minecraft.world.entity.Entity mob = FishLook.mob(bucket);
        if (mob instanceof net.minecraft.world.entity.LivingEntity living) {
            float big = Math.max(0.2F, Math.max(mob.getBbWidth(), mob.getBbHeight()));
            int scale = Math.max(4, Math.round(20 / big));
            org.joml.Quaternionf q = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(new org.joml.Quaternionf().rotateY((float) Math.PI / 2));
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(g, x, y + Math.round(mob.getBbHeight() * scale / 2), scale, q, null, living);
        } else {
            g.renderItem(bucket, x - 8, y - 8);
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
        // Newborns not yet looked at: a yellow asterisk in the slot's corner.
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (Slot s : this.menu.slots) {
            if (s instanceof AquariumMenu.FishSlot fs && fs.hasItem() && IGoByJake.orefishfarm.data.FishGenes.isUnseen(fs.getItem()) && !this.seenSent.contains(fs.index())) {
                g.drawString(this.font, "*", s.x + 11, s.y - 1, 0xFFFF55, true);
            }
        }
        g.pose().popPose();
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
        Component s = statusText();
        if (s != null) {
            int w = this.font.width(s);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(this.font, s, WX + (WW - w) / 2, WY + wh() - 12, 0xFFFFFF, true);
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
        if (button == 0 && in((int) mx - this.leftPos, (int) my - this.topPos, SX, sy(), 14, sh()) && this.menu.maxRow() > 0) {
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
        float f = (float) ((my - this.topPos - sy() - 8.5) / (sh() - 17));
        scrollTo(Math.round(Mth.clamp(f, 0F, 1F) * this.menu.maxRow()));
    }
}

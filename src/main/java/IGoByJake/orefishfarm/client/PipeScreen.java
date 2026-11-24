package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.pipe.PipeMenu;
import IGoByJake.orefishfarm.pipe.PipeNet;
import IGoByJake.orefishfarm.pipe.PipePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The pipe network screen. Left: the inventories the network touches, lettered (click one to rename it). Right: the
 * commands, top one first: item (click with an item to set it, with an empty hand to clear it = any item), From and
 * To (click for the next inventory, right-click for the previous one), Keep (click to type an amount; empty = move
 * everything), On/Off, move up/down, delete. Below: the fish slots and the player's inventory.
 */
public class PipeScreen extends AbstractContainerScreen<PipeMenu> {
    private static final int LIST_X = 6, LIST_Y = 18, LIST_W = 114, ROW_H = 18, LIST_ROWS = 5;
    private static final int CMD_X = 126, CMD_Y = 18, CMD_W = 198, CMD_ROW = 20, CMD_ROWS = 5;
    private static final int HELP_X = PipeMenu.FISH_X + PipeMenu.FISH_COLS * 18 + 8;
    private static final int EDIT_NONE = 0, EDIT_NAME = 1, EDIT_KEEP = 2;

    private int listScroll, cmdScroll;
    private EditBox edit;
    private int editKind = EDIT_NONE, editTarget;

    public PipeScreen(PipeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PipeMenu.W;
        this.imageHeight = PipeMenu.H;
        this.titleLabelX = LIST_X;
        this.titleLabelY = 6;
        this.inventoryLabelX = PipeMenu.INV_X;
        this.inventoryLabelY = PipeMenu.INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        this.edit = new EditBox(this.font, 0, 0, 80, 12, Component.empty());
        this.edit.setBordered(true);
        this.edit.setVisible(false);
        this.edit.setMaxLength(24);
    }

    // ================================================================== drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        if (this.edit.isVisible()) this.edit.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = this.leftPos, y = this.topPos;
        panel(g, x, y, this.imageWidth, this.imageHeight);
        sunken(g, x + LIST_X - 1, y + LIST_Y - 1, LIST_W + 2, LIST_ROWS * ROW_H + 2);
        sunken(g, x + CMD_X - 1, y + CMD_Y - 1, CMD_W + 2, CMD_ROWS * CMD_ROW + 2);
        // Fish slots: as many as the network has; the rest shown locked.
        for (int i = 0; i < PipeNet.MAX_FISH; i++) {
            int sx = x + PipeMenu.FISH_X - 1 + (i % PipeMenu.FISH_COLS) * 18, sy = y + PipeMenu.FISH_Y - 1 + (i / PipeMenu.FISH_COLS) * 18;
            if (i < this.menu.capacity) slot(g, sx, sy);
            else g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF9A9A9A);
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(g, x + PipeMenu.INV_X - 1 + c * 18, y + PipeMenu.INV_Y - 1 + r * 18);
        for (int c = 0; c < 9; c++) slot(g, x + PipeMenu.INV_X - 1 + c * 18, y + PipeMenu.INV_Y + 57);
        drawList(g, mx, my);
        drawCommands(g, mx, my);
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        List<PipeMenu.EndpointView> eps = this.menu.endpoints;
        this.listScroll = Mth.clamp(this.listScroll, 0, Math.max(0, eps.size() - LIST_ROWS));
        int x = this.leftPos + LIST_X, y0 = this.topPos + LIST_Y;
        if (eps.isEmpty()) {
            g.drawWordWrap(this.font, Component.translatable("screen.orefishfarm.pipe.no_inventories"), x + 4, y0 + 4, LIST_W - 8, 0xFF404040);
            return;
        }
        for (int r = 0; r < LIST_ROWS; r++) {
            int i = r + this.listScroll;
            if (i >= eps.size()) break;
            PipeMenu.EndpointView e = eps.get(i);
            int y = y0 + r * ROW_H;
            boolean hover = in(mx, my, x, y, LIST_W, ROW_H);
            g.fill(x, y, x + LIST_W, y + ROW_H, hover ? 0xFFA8A8A8 : (r % 2 == 0 ? 0xFF8F8F8F : 0xFF969696));
            badge(g, x + 2, y + 2, e.letter());
            Block block = e.block() == null ? null : ForgeRegistries.BLOCKS.getValue(e.block());
            if (block != null) g.renderItem(new ItemStack(block), x + 18, y + 1);
            String name = e.name().isEmpty() ? (block == null ? "?" : block.getName().getString()) : e.name();
            int color = e.missing() ? 0xFFFF6060 : e.name().isEmpty() ? 0xFFE0E0E0 : 0xFFFFFFFF;
            if (!(this.editKind == EDIT_NAME && this.editTarget == e.id()))
                g.drawString(this.font, this.font.plainSubstrByWidth(name, LIST_W - 40), x + 36, y + 5, color, true);
        }
        scrollbar(g, x + LIST_W - 3, y0, LIST_ROWS * ROW_H, this.listScroll, eps.size(), LIST_ROWS);
    }

    private void drawCommands(GuiGraphics g, int mx, int my) {
        int x = this.leftPos + CMD_X, y0 = this.topPos + CMD_Y;
        g.drawString(this.font, Component.translatable("screen.orefishfarm.pipe.commands"), x, this.topPos + 6, 0xFF404040, false);
        button(g, x + CMD_W - 14, this.topPos + 4, 14, 11, "+", in(mx, my, x + CMD_W - 14, this.topPos + 4, 14, 11), 0xFF60C060);
        List<PipeMenu.CommandView> cmds = this.menu.commands;
        this.cmdScroll = Mth.clamp(this.cmdScroll, 0, Math.max(0, cmds.size() - CMD_ROWS));
        if (cmds.isEmpty()) {
            g.drawWordWrap(this.font, Component.translatable("screen.orefishfarm.pipe.no_commands"), x + 4, y0 + 4, CMD_W - 8, 0xFF404040);
            return;
        }
        for (int r = 0; r < CMD_ROWS; r++) {
            int i = r + this.cmdScroll;
            if (i >= cmds.size()) break;
            PipeMenu.CommandView c = cmds.get(i);
            int y = y0 + r * CMD_ROW;
            g.fill(x, y, x + CMD_W, y + CMD_ROW, r % 2 == 0 ? 0xFF8F8F8F : 0xFF969696);
            // item
            slot(g, x + 1, y + 1);
            if (c.filter().isEmpty()) g.drawCenteredString(this.font, "*", x + 10, y + 6, 0xFFD0D0D0);
            else g.renderItem(c.filter(), x + 2, y + 2);
            // from -> to
            button(g, x + 24, y + 3, 20, 14, letter(c.from()), in(mx, my, x + 24, y + 3, 20, 14), 0);
            g.drawString(this.font, "→", x + 47, y + 6, 0xFFFFFFFF, true);
            button(g, x + 56, y + 3, 20, 14, letter(c.to()), in(mx, my, x + 56, y + 3, 20, 14), 0);
            // keep
            if (!(this.editKind == EDIT_KEEP && this.editTarget == i)) {
                String keep = c.keep() < 0 ? Component.translatable("screen.orefishfarm.pipe.all").getString()
                        : Component.translatable("screen.orefishfarm.pipe.keep", c.keep()).getString();
                button(g, x + 80, y + 3, 48, 14, this.font.plainSubstrByWidth(keep, 44), in(mx, my, x + 80, y + 3, 48, 14), 0);
            }
            // on / off
            button(g, x + 132, y + 3, 22, 14, c.on() ? "On" : "Off", in(mx, my, x + 132, y + 3, 22, 14), c.on() ? 0xFF4FA04F : 0xFFA04F4F);
            button(g, x + 158, y + 3, 10, 14, "▲", in(mx, my, x + 158, y + 3, 10, 14), 0);
            button(g, x + 169, y + 3, 10, 14, "▼", in(mx, my, x + 169, y + 3, 10, 14), 0);
            button(g, x + 183, y + 3, 12, 14, "x", in(mx, my, x + 183, y + 3, 12, 14), 0xFF904040);
        }
        scrollbar(g, x + CMD_W - 2, y0, CMD_ROWS * CMD_ROW, this.cmdScroll, cmds.size(), CMD_ROWS);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);
        g.drawString(this.font, Component.translatable("screen.orefishfarm.pipe.fish", this.menu.capacity, this.menu.pipeCount),
                PipeMenu.FISH_X, PipeMenu.FISH_Y - 10, 0xFF404040, false);
        int y = PipeMenu.FISH_Y - 10;
        for (var line : this.font.split(Component.translatable("screen.orefishfarm.pipe.help"), PipeMenu.W - HELP_X - 6)) {
            g.drawString(this.font, line, HELP_X, y, 0xFF505050, false);
            y += 9;
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        // Fish slots: what the fish is doing.
        if (this.hoveredSlot != null && this.hoveredSlot.index < PipeNet.MAX_FISH && this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()) {
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(this.hoveredSlot.getItem()));
            PipeMenu.FishView f = this.menu.fish[this.hoveredSlot.index];
            if (f != null) lines.add(1, fishStatus(f));
            g.renderComponentTooltip(this.font, lines, mx, my);
            return;
        }
        int lx = this.leftPos + LIST_X, ly = this.topPos + LIST_Y;
        if (in(mx, my, lx, ly, LIST_W, LIST_ROWS * ROW_H)) {
            int i = (my - ly) / ROW_H + this.listScroll;
            if (i >= 0 && i < this.menu.endpoints.size()) {
                g.renderComponentTooltip(this.font, endpointTooltip(this.menu.endpoints.get(i)), mx, my);
                return;
            }
        }
        int cx = this.leftPos + CMD_X, cy = this.topPos + CMD_Y;
        if (in(mx, my, cx, cy, CMD_W, CMD_ROWS * CMD_ROW)) {
            int i = (my - cy) / CMD_ROW + this.cmdScroll;
            int rx = mx - cx;
            if (i >= 0 && i < this.menu.commands.size()) {
                PipeMenu.CommandView c = this.menu.commands.get(i);
                Component tip = null;
                if (rx < 20) {
                    if (!c.filter().isEmpty()) {
                        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(c.filter()));
                        lines.add(Component.translatable("screen.orefishfarm.pipe.filter_hint").withStyle(ChatFormatting.GRAY));
                        g.renderComponentTooltip(this.font, lines, mx, my);
                        return;
                    }
                    tip = Component.translatable("screen.orefishfarm.pipe.any_item");
                } else if (rx >= 24 && rx < 44) tip = endpointName(c.from());
                else if (rx >= 56 && rx < 76) tip = endpointName(c.to());
                else if (rx >= 80 && rx < 128) tip = Component.translatable("screen.orefishfarm.pipe.keep_hint");
                if (tip != null) {
                    g.renderTooltip(this.font, tip, mx, my);
                    return;
                }
            }
        }
        super.renderTooltip(g, mx, my);
    }

    private List<Component> endpointTooltip(PipeMenu.EndpointView e) {
        List<Component> lines = new ArrayList<>();
        Block block = e.block() == null ? null : ForgeRegistries.BLOCKS.getValue(e.block());
        lines.add(Component.literal(e.letter() + (e.name().isEmpty() ? "" : "  " + e.name())).withStyle(ChatFormatting.WHITE));
        if (block != null) lines.add(block.getName().copy().withStyle(ChatFormatting.GRAY));
        var p = e.pos();
        MutableComponent where = Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ());
        if (e.face() >= 0) where.append(Component.literal(" (")).append(Component.translatable("screen.orefishfarm.pipe.side." + Direction.from3DDataValue(e.face()).getName())).append(")");
        lines.add(where.withStyle(ChatFormatting.DARK_GRAY));
        if (e.missing()) lines.add(Component.translatable("screen.orefishfarm.pipe.missing").withStyle(ChatFormatting.RED));
        else if (!e.loaded()) lines.add(Component.translatable("screen.orefishfarm.pipe.unloaded").withStyle(ChatFormatting.GOLD));
        else lines.add(Component.translatable("screen.orefishfarm.pipe.slots", e.used(), e.total()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("screen.orefishfarm.pipe.rename_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private Component fishStatus(PipeMenu.FishView f) {
        Component what = f.cargo().isEmpty() ? Component.empty() : Component.literal(f.amount() + " ").append(f.cargo().getHoverName());
        Component where = endpointName(f.target());
        return (switch (f.state()) {
            case PipeNet.TO_SOURCE -> Component.translatable("screen.orefishfarm.pipe.fish.to_source", what, where);
            case PipeNet.TO_TARGET -> Component.translatable("screen.orefishfarm.pipe.fish.to_target", what, where);
            case PipeNet.RETURNING -> Component.translatable("screen.orefishfarm.pipe.fish.returning", what, where);
            case PipeNet.HOLDING -> Component.translatable("screen.orefishfarm.pipe.fish.holding", what);
            default -> Component.translatable("screen.orefishfarm.pipe.fish.idle");
        }).copy().withStyle(f.state() == PipeNet.HOLDING ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE);
    }

    private String letter(int id) {
        PipeMenu.EndpointView e = this.menu.endpoint(id);
        return e == null ? "-" : e.letter();
    }

    private Component endpointName(int id) {
        PipeMenu.EndpointView e = this.menu.endpoint(id);
        if (e == null) return Component.literal("-");
        if (!e.name().isEmpty()) return Component.literal(e.letter() + " " + e.name());
        Block block = e.block() == null ? null : ForgeRegistries.BLOCKS.getValue(e.block());
        return Component.literal(e.letter() + " ").append(block == null ? Component.literal("?") : block.getName());
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        if (this.edit.isVisible()) {
            if (this.edit.mouseClicked(mxd, myd, button)) return true;
            commitEdit();
        }
        // New command
        int cx = this.leftPos + CMD_X;
        if (in(mx, my, cx + CMD_W - 14, this.topPos + 4, 14, 11)) {
            send(PipePackets.ADD, 0, 0);
            this.cmdScroll = Math.max(0, this.menu.commands.size() + 1 - CMD_ROWS);
            return true;
        }
        // Inventory list: rename
        int lx = this.leftPos + LIST_X, ly = this.topPos + LIST_Y;
        if (in(mx, my, lx, ly, LIST_W, LIST_ROWS * ROW_H)) {
            int i = (my - ly) / ROW_H + this.listScroll;
            if (i >= 0 && i < this.menu.endpoints.size()) {
                PipeMenu.EndpointView e = this.menu.endpoints.get(i);
                startEdit(EDIT_NAME, e.id(), lx + 35, ly + (i - this.listScroll) * ROW_H + 3, LIST_W - 38, e.name());
                return true;
            }
        }
        // Commands
        int cy = this.topPos + CMD_Y;
        if (in(mx, my, cx, cy, CMD_W, CMD_ROWS * CMD_ROW)) {
            int i = (my - cy) / CMD_ROW + this.cmdScroll;
            if (i >= 0 && i < this.menu.commands.size()) {
                int rx = mx - cx, ry = cy + (i - this.cmdScroll) * CMD_ROW;
                int step = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
                if (rx < 20) PipePackets.edit(this.menu.containerId, PipePackets.FILTER, i, 0, "", this.menu.getCarried().copyWithCount(Math.min(1, this.menu.getCarried().getCount())));
                else if (rx >= 24 && rx < 44) send(PipePackets.FROM, i, step);
                else if (rx >= 56 && rx < 76) send(PipePackets.TO, i, step);
                else if (rx >= 80 && rx < 128) {
                    int keep = this.menu.commands.get(i).keep();
                    startEdit(EDIT_KEEP, i, cx + 81, ry + 4, 46, keep < 0 ? "" : String.valueOf(keep));
                } else if (rx >= 132 && rx < 154) send(PipePackets.TOGGLE, i, 0);
                else if (rx >= 158 && rx < 168) send(PipePackets.MOVE, i, -1);
                else if (rx >= 169 && rx < 179) send(PipePackets.MOVE, i, 1);
                else if (rx >= 183 && rx < 195) send(PipePackets.DELETE, i, 0);
                else return true;
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int lx = this.leftPos + LIST_X, ly = this.topPos + LIST_Y;
        if (in((int) mx, (int) my, lx, ly, LIST_W, LIST_ROWS * ROW_H)) {
            this.listScroll -= (int) Math.signum(delta);
            return true;
        }
        int cx = this.leftPos + CMD_X, cy = this.topPos + CMD_Y;
        if (in((int) mx, (int) my, cx, cy, CMD_W, CMD_ROWS * CMD_ROW)) {
            this.cmdScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (this.edit.isVisible() && this.edit.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                stopEdit();
                return true;
            }
            this.edit.keyPressed(key, scan, mods);
            return true; // the inventory key etc. type into the box instead of closing the screen
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (this.edit.isVisible() && this.edit.isFocused()) return this.edit.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.edit.tick();
    }

    private void startEdit(int kind, int target, int x, int y, int w, String text) {
        this.editKind = kind;
        this.editTarget = target;
        this.edit.setX(x);
        this.edit.setY(y);
        this.edit.setWidth(w);
        this.edit.setFilter(kind == EDIT_KEEP ? s -> s.matches("\\d{0,7}") : s -> true);
        this.edit.setMaxLength(kind == EDIT_KEEP ? 7 : 24);
        this.edit.setValue(text);
        this.edit.setVisible(true);
        this.edit.setFocused(true);
        setFocused(this.edit);
    }

    private void commitEdit() {
        if (this.editKind == EDIT_NAME) PipePackets.edit(this.menu.containerId, PipePackets.RENAME, this.editTarget, 0, this.edit.getValue(), ItemStack.EMPTY);
        else if (this.editKind == EDIT_KEEP) {
            String v = this.edit.getValue().trim();
            int n = v.isEmpty() ? -1 : Integer.parseInt(v);
            send(PipePackets.KEEP, this.editTarget, n);
        }
        stopEdit();
    }

    private void stopEdit() {
        this.editKind = EDIT_NONE;
        this.edit.setVisible(false);
        this.edit.setFocused(false);
        setFocused(null);
    }

    private void send(int op, int a, int b) {
        PipePackets.edit(this.menu.containerId, op, a, b, "", ItemStack.EMPTY);
    }

    private void playClick() {
        if (this.minecraft != null) this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1F, 0.6F));
    }

    // ================================================================== bits

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** A vanilla-style raised window. */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
        g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6);
    }

    /** A sunken box, like a slot. */
    private static void sunken(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        sunken(g, x, y, 18, 18);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, boolean hover, int tint) {
        int top = hover ? 0xFFFFFFFF : 0xFFDDDDDD, face = tint != 0 ? tint : (hover ? 0xFFB8B8DC : 0xFFAAAAAA), bottom = 0xFF555555;
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bottom);
        g.fill(x + 1, y + 1, x + w - 2, y + h - 2, top);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, hover && tint != 0 ? brighter(face) : face);
        g.drawCenteredString(this.font, text, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private static int brighter(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 30), gg = Math.min(255, ((c >> 8) & 0xFF) + 30), b = Math.min(255, (c & 0xFF) + 30);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    /** The inventory's letter in a little coloured box. */
    private void badge(GuiGraphics g, int x, int y, String letter) {
        float hue = ((letter.hashCode() * 0x9E3779B9) >>> 8 & 0xFFFF) / 65535F;
        int c = 0xFF000000 | Mth.hsvToRgb(hue, 0.55F, 0.75F);
        g.fill(x, y, x + 14, y + 14, 0xFF202020);
        g.fill(x + 1, y + 1, x + 13, y + 13, c);
        g.drawCenteredString(this.font, letter.length() > 2 ? letter.substring(0, 2) : letter, x + 7, y + 3, 0xFFFFFFFF);
    }

    private static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int total, int shown) {
        if (total <= shown) return;
        int bar = Math.max(8, h * shown / total);
        int top = y + (h - bar) * scroll / Math.max(1, total - shown);
        g.fill(x, y, x + 2, y + h, 0xFF606060);
        g.fill(x, top, x + 2, top + bar, 0xFFE0E0E0);
    }
}

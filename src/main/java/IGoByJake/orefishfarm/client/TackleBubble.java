package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.Network;
import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.tackle.Tackle;
import IGoByJake.orefishfarm.tackle.TackleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The tackle popup. In any inventory screen:
 * <ul>
 *   <li>Carrying a tackle piece: the rod nearest the cursor (within about a slot and a half) opens a bubble above
 *       itself with its tackle slot and the fish the carried piece catches. Click the slot to clip the piece on (a
 *       piece already there comes off onto the cursor).</li>
 *   <li>Empty cursor over a rod: the bubble shows the piece on it, its uses left and what it catches. Click the
 *       piece to take it off.</li>
 * </ul>
 * The bubble pops in (fades in while rising a few pixels), stays while the mouse is over the rod, the bubble or the
 * gap between, and otherwise fades out quickly while sinking back. The server does the swap ({@link TackleEvents#clipOn}); in the creative inventory, which keeps
 * its cursor on the client, it is done here and sent as creative slot changes.
 */
@Mod.EventBusSubscriber(modid = OreFishFarm.MODID, value = Dist.CLIENT)
public final class TackleBubble {
    private static final int REACH = 26, IN_MS = 110, OUT_MS = 140, PAD = 5, RISE = 4;
    @Nullable private static Slot target;
    @Nullable private static AbstractContainerScreen<?> screen;
    private static long fadeStart = -1, showStart;
    // bubble rect and its tackle slot, in screen coordinates
    private static int bx, by, bw, bh, sx, sy;
    private static boolean below;
    private static boolean ownTooltip;

    private TackleBubble() {}

    // ---------------------------------------------------------------- which rod

    private static boolean usable(AbstractContainerScreen<?> s, Slot slot) {
        if (!slot.isActive() || !Tackle.isRod(slot.getItem()) || slot.getItem().getCount() != 1) return false;
        // in the creative screen only the player's own inventory slots (not the item list)
        return !(s instanceof CreativeModeInventoryScreen) || slot.container instanceof Inventory;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean overTarget(AbstractContainerScreen<?> s, double mx, double my) {
        if (target == null) return false;
        int rx = s.getGuiLeft() + target.x - 1, ry = s.getGuiTop() + target.y - 1;
        if (over(mx, my, rx, ry, 18, 18) || over(mx, my, bx, by, bw, bh)) return true;
        // the gap between the rod and the bubble
        int gx = Math.max(bx, rx), gw = Math.min(bx + bw, rx + 18) - gx;
        return below ? over(mx, my, gx, ry + 18, gw, by - ry - 18) : over(mx, my, gx, by + bh, gw, ry - by - bh);
    }

    @Nullable
    private static Slot pick(AbstractContainerScreen<?> s, ItemStack carried, double mx, double my) {
        boolean carrying = Tackle.isTackle(carried);
        if (!carrying && !carried.isEmpty()) return null;
        if (target != null && screen == s && fadeStart < 0 && s.getMenu().slots.contains(target) && usable(s, target) && overTarget(s, mx, my)) return target;
        if (carrying) {
            Slot best = null;
            double bestD = REACH * REACH;
            for (Slot slot : s.getMenu().slots) {
                if (!usable(s, slot)) continue;
                double dx = s.getGuiLeft() + slot.x + 8 - mx, dy = s.getGuiTop() + slot.y + 8 - my, d = dx * dx + dy * dy;
                if (d <= bestD) {
                    bestD = d;
                    best = slot;
                }
            }
            return best;
        }
        Slot hovered = s.getSlotUnderMouse();
        return hovered != null && usable(s, hovered) ? hovered : null;
    }

    // ---------------------------------------------------------------- layout and drawing

    /** The piece on the rod (not the one being carried): the bubble shows what the rod has. */
    private static Component title(ItemStack carried, ItemStack on) {
        return on.isEmpty() ? Component.translatable("gui.orefishfarm.tackle.none") : on.getHoverName();
    }

    /** What the rod's own piece catches; nothing until a piece is on. */
    private static List<Tackle.Bonus> catches(ItemStack carried, ItemStack on) {
        return on.isEmpty() ? List.of() : Tackle.bonusesOf(on);
    }

    // the bubble's size eases toward its target, so it grows when a piece goes on and shrinks when one comes off
    private static float curW, curH;
    private static long lastFrame;
    private static boolean snapSize = true;

    /** Whether the bubble has finished resizing (its fish row is only drawn then). */
    private static boolean settled;

    private static void layout(AbstractContainerScreen<?> s, Slot slot, ItemStack carried) {
        Font font = Minecraft.getInstance().font;
        ItemStack on = Tackle.get(slot.getItem());
        int icons = catches(carried, on).size();
        int tw = Math.max(Math.max(font.width(title(carried, on)) + 2 * PAD, icons * 18 + 2 * PAD), 44);
        int th = PAD + 10 + 18 + (icons > 0 ? 4 + 18 : 0) + PAD;
        long now = net.minecraft.Util.getMillis();
        if (snapSize) {
            curW = tw;
            curH = th;
            snapSize = false;
        } else {
            float k = 1F - (float) Math.exp(-Math.max(0, now - lastFrame) / 45.0);
            curW += (tw - curW) * k;
            curH += (th - curH) * k;
            if (Math.abs(tw - curW) < 0.5F) curW = tw;
            if (Math.abs(th - curH) < 0.5F) curH = th;
        }
        lastFrame = now;
        settled = curW == tw && curH == th;
        bw = Math.round(curW);
        bh = Math.round(curH);
        int rx = s.getGuiLeft() + slot.x + 8, ry = s.getGuiTop() + slot.y;
        bx = Math.max(2, Math.min(s.width - bw - 2, rx - bw / 2));
        below = ry - bh - 6 < 2;
        by = below ? ry + 16 + 6 : ry - bh - 6;
        sx = bx + bw / 2 - 9;
        sy = by + PAD + 10;
    }

    /** {@code rgb} with the bubble's current opacity. */
    private static int fade(int argb, float alpha) {
        return ((int) ((argb >>> 24) * alpha) << 24) | (argb & 0xFFFFFF);
    }

    private static void panel(GuiGraphics g, float alpha) {
        int x1 = bx, y1 = by, x2 = bx + bw, y2 = by + bh;
        int px = Math.max(x1 + 4, Math.min(x2 - 10, sx + 6)); // pointer toward the rod
        int black = fade(0xFF000000, alpha), bg = fade(0xFFC6C6C6, alpha), hi = fade(0xFFFFFFFF, alpha), lo = fade(0xFF555555, alpha);
        // non-overlapping pieces, so a part-faded bubble has no darker seams
        g.fill(x1, y1, x2, y1 + 1, black);
        g.fill(x1, y2 - 1, x2, y2, black);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, black);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, black);
        g.fill(x1 + 1, y1 + 1, x2 - 2, y1 + 2, hi);
        g.fill(x1 + 1, y1 + 2, x1 + 2, y2 - 2, hi);
        g.fill(x1 + 2, y2 - 2, x2 - 1, y2 - 1, lo);
        g.fill(x2 - 2, y1 + 1, x2 - 1, y2 - 2, lo);
        g.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, bg);
        for (int i = 0; i < 4; i++) {
            int yy = below ? y1 - 1 - i : y2 + i;
            g.fill(px + i, yy, px + 7 - i, yy + 1, i == 3 ? black : bg);
            g.fill(px + i - 1, yy, px + i, yy + 1, black);
            g.fill(px + 7 - i, yy, px + 8 - i, yy + 1, black);
        }
    }

    private static void frame(GuiGraphics g, int x, int y, float alpha) {
        g.fill(x, y, x + 17, y + 1, fade(0xFF373737, alpha));
        g.fill(x, y + 1, x + 1, y + 17, fade(0xFF373737, alpha));
        g.fill(x + 1, y + 17, x + 18, y + 18, fade(0xFFFFFFFF, alpha));
        g.fill(x + 17, y, x + 18, y + 17, fade(0xFFFFFFFF, alpha));
        g.fill(x + 1, y + 1, x + 17, y + 17, fade(0xFF8B8B8B, alpha));
    }

    /** The item on the cursor, drawn again over the bubble (the screen drew it before the bubble). */
    private static void drawCarried(GuiGraphics g, ItemStack carried, double mx, double my) {
        if (carried.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.renderItem(carried, (int) mx - 8, (int) my - 8);
        g.renderItemDecorations(Minecraft.getInstance().font, carried, (int) mx - 8, (int) my - 8);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> s)) {
            target = null;
            return;
        }
        double mx = event.getMouseX(), my = event.getMouseY();
        ItemStack carried = s.getMenu().getCarried();
        Slot want = pick(s, carried, mx, my);
        long now = net.minecraft.Util.getMillis();
        if (want != null) {
            if (target == null || screen != s || want != target) {
                if (target == null || screen != s) showStart = now;
                snapSize = true; // a new rod: its bubble opens at its own size
            }
            else if (fadeStart >= 0) {
                // coming back while fading out: carry on from how visible it still is
                float left = Math.max(0F, 1F - (now - fadeStart) / (float) OUT_MS);
                showStart = now - (long) (left * IN_MS);
            }
            target = want;
            screen = s;
            fadeStart = -1;
            layout(s, want, carried);
        } else if (target != null && fadeStart < 0) {
            // start fading from wherever the pop-in had got to
            float shown = Math.min(1F, (now - showStart) / (float) IN_MS);
            fadeStart = now - (long) ((1F - shown) * OUT_MS);
        }
        if (target == null || screen != s) return;
        float alpha = fadeStart >= 0 ? 1F - (now - fadeStart) / (float) OUT_MS : Math.min(1F, (now - showStart) / (float) IN_MS);
        if (alpha <= 0F) {
            target = null;
            return;
        }
        float ease = alpha * alpha * (3 - 2 * alpha);
        GuiGraphics g = event.getGuiGraphics();
        // draw what the screen has batched (item counts) now, so the bubble covers it rather than the other way round
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        Font font = Minecraft.getInstance().font;
        ItemStack on = Tackle.get(target.getItem());
        List<Tackle.Bonus> fish = catches(carried, on);
        g.pose().pushPose();
        // pops in rising RISE px into place, sinks RISE px as it fades out
        g.pose().translate(0, (1F - ease) * RISE * (below ? -1 : 1), 300);
        panel(g, ease);
        if (ease > 0.08F) {
            Component t = title(carried, on);
            g.drawString(font, t, bx + bw / 2 - font.width(t) / 2, by + PAD, fade(0xFF404040, ease), false);
        }
        frame(g, sx, sy, ease);
        if (ease > 0.55F) {
            boolean carrying = Tackle.isTackle(carried);
            boolean hoverSlot = fadeStart < 0 && over(mx, my, sx, sy, 18, 18);
            if (!on.isEmpty()) {
                g.renderItem(on, sx + 1, sy + 1);
                g.renderItemDecorations(font, on, sx + 1, sy + 1);
            } else if (carrying) {
                // an empty slot waiting for the piece: a pulsing outline
                int a = 0x60 + (int) (0x50 * (0.5 + 0.5 * Math.sin(net.minecraft.Util.getMillis() / 180.0)));
                int c = (a << 24) | 0xFFFFFF;
                g.fill(sx + 1, sy + 1, sx + 17, sy + 2, c);
                g.fill(sx + 1, sy + 16, sx + 17, sy + 17, c);
                g.fill(sx + 1, sy + 2, sx + 2, sy + 16, c);
                g.fill(sx + 16, sy + 2, sx + 17, sy + 16, c);
            }
            if (hoverSlot) g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x80FFFFFF);
            int ix = bx + bw / 2 - fish.size() * 9, iy = sy + 18 + 4;
            Tackle.Bonus hovered = null;
            for (int i = 0; settled && i < fish.size(); i++) {
                g.renderItem(new ItemStack(fish.get(i).fish()), ix + i * 18 + 1, iy + 1);
                if (fadeStart < 0 && over(mx, my, ix + i * 18, iy, 18, 18)) hovered = fish.get(i);
            }
            g.pose().popPose();
            drawCarried(g, carried, mx, my);
            // tooltips over the bubble (ours only; vanilla's for slots under the bubble are held back)
            if (fadeStart < 0) {
                List<Component> tip = new ArrayList<>();
                if (hoverSlot) {
                    if (!on.isEmpty()) {
                        tip.add(on.getHoverName());
                        tip.add(Component.translatable("gui.orefishfarm.tackle.uses", on.getMaxDamage() - on.getDamageValue(), on.getMaxDamage())
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                    if (carrying) tip.add(Component.translatable(on.isEmpty() ? "gui.orefishfarm.tackle.click_on" : "gui.orefishfarm.tackle.click_swap").withStyle(net.minecraft.ChatFormatting.YELLOW));
                    else if (!on.isEmpty()) tip.add(Component.translatable("gui.orefishfarm.tackle.click_off").withStyle(net.minecraft.ChatFormatting.YELLOW));
                } else if (hovered != null) {
                    tip.add(new ItemStack(hovered.fish()).getHoverName());
                    tip.add(where(hovered).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                if (!tip.isEmpty()) {
                    ownTooltip = true;
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.renderComponentTooltip(font, tip, (int) mx, (int) my);
                    g.pose().popPose();
                    ownTooltip = false;
                }
            }
            return;
        }
        g.pose().popPose();
        drawCarried(g, carried, mx, my);
    }

    /** "Overworld · Lava · 6%". */
    private static net.minecraft.network.chat.MutableComponent where(Tackle.Bonus b) {
        var c = Component.empty();
        if (b.dimension() != null) {
            String key = "dimension." + b.dimension().getNamespace() + "." + b.dimension().getPath();
            if (net.minecraft.locale.Language.getInstance().has(key)) c.append(Component.translatable(key));
            else {
                String p = b.dimension().getPath().replace('_', ' ');
                c.append(Character.toUpperCase(p.charAt(0)) + p.substring(1));
            }
            c.append(" · ");
        }
        c.append(Component.translatable("jei.orefishfarm.liquid." + (b.liquid() == null ? "any" : b.liquid())));
        float pct = b.chance() * 100F;
        return c.append(" · " + (pct == Math.round(pct) ? String.valueOf(Math.round(pct)) : String.format("%.1f", pct)) + "%");
    }

    /** Vanilla tooltips for whatever is under the bubble are held back while the mouse is on the bubble. */
    @SubscribeEvent
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        if (ownTooltip || target == null || fadeStart >= 0) return;
        if (over(event.getX(), event.getY(), bx, by, bw, bh)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- clicks

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (target == null || fadeStart >= 0 || !(event.getScreen() instanceof AbstractContainerScreen<?> s) || s != screen) return;
        double mx = event.getMouseX(), my = event.getMouseY();
        if (!over(mx, my, bx, by, bw, bh)) return;
        event.setCanceled(true); // the bubble swallows its own clicks, so nothing under it is picked up
        if (!over(mx, my, sx, sy, 18, 18)) return;
        ItemStack carried = s.getMenu().getCarried();
        boolean clip = Tackle.isTackle(carried);
        if (!clip && (!carried.isEmpty() || Tackle.get(target.getItem()).isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (s instanceof CreativeModeInventoryScreen && mc.player != null && mc.gameMode != null && target.container instanceof Inventory inv) {
            creative(s, inv, target.getContainerSlot(), clip);
        } else {
            Network.CHANNEL.sendToServer(new TackleEvents.Action(target.index, clip));
        }
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_CHAIN, 0.6F, clip ? 1.4F : 0.9F);
    }

    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (target != null && fadeStart < 0 && event.getScreen() == screen && over(event.getMouseX(), event.getMouseY(), bx, by, bw, bh)) event.setCanceled(true);
    }

    /** The creative inventory keeps its cursor here: swap locally and send the rod as a creative slot change. */
    private static void creative(AbstractContainerScreen<?> s, Inventory inv, int idx, boolean clip) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack rod = inv.getItem(idx), carried = s.getMenu().getCarried();
        if (!Tackle.isRod(rod) || rod.getCount() != 1) return;
        ItemStack newRod = rod.copy(), old = Tackle.get(newRod);
        if (clip) {
            Tackle.set(newRod, carried);
            ItemStack rest = carried.copy();
            rest.shrink(1);
            if (rest.isEmpty()) s.getMenu().setCarried(old);
            else {
                s.getMenu().setCarried(rest);
                if (!old.isEmpty()) inv.add(old);
            }
        } else {
            if (old.isEmpty()) return;
            Tackle.set(newRod, ItemStack.EMPTY);
            s.getMenu().setCarried(old);
        }
        inv.setItem(idx, newRod);
        // the player inventory menu's numbering: hotbar 36-44, main 9-35, off hand 45
        int menuSlot = idx < 9 ? 36 + idx : idx < 36 ? idx : idx == 40 ? 45 : -1;
        if (menuSlot >= 0) sendCreative(newRod, menuSlot);
        if (clip && !old.isEmpty() && !s.getMenu().getCarried().equals(old)) {
            // the old piece went into the inventory: send whichever slot it landed in
            for (int i = 0; i < 36; i++) {
                if (ItemStack.isSameItemSameTags(inv.getItem(i), old)) sendCreative(inv.getItem(i), i < 9 ? 36 + i : i);
            }
        }
    }

    private static void sendCreative(ItemStack stack, int menuSlot) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(menuSlot, stack.copy()));
    }
}

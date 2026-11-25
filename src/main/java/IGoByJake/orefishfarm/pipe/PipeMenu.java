package IGoByJake.orefishfarm.pipe;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.data.FishData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one screen for a whole pipe network: its inventories, its commands and its fish slots. The menu only knows
 * the pipe it was opened from; the network is looked up from that pipe each time, so it follows merges and splits.
 * The lists are sent as a {@link PipePackets.Snapshot} (on open, after every edit, and every half second while
 * something in it changed); edits come back as {@link PipePackets.Edit}.
 */
public class PipeMenu extends AbstractContainerMenu {
    public static final int W = 330, H = 246;
    public static final int FISH_X = 8, FISH_Y = 132, FISH_COLS = PipeNet.MAX_FISH; // one row
    public static final int INV_X = (W - 162) / 2, INV_Y = 164;

    private final BlockPos pos;
    @Nullable private final ServerLevel level;
    private int lastHash, timer = 9;

    // What the client shows (from the last snapshot).
    public int capacity = 1, pipeCount;
    public final List<EndpointView> endpoints = new ArrayList<>();
    public final List<CommandView> commands = new ArrayList<>();
    public final FishView[] fish = new FishView[PipeNet.MAX_FISH];

    public record EndpointView(int id, String letter, String name, ResourceLocation block, BlockPos pos, int face, boolean missing,
                               boolean loaded, int used, int total) {}

    public record CommandView(ItemStack filter, int from, int to, int keep, boolean on) {}

    /** command: the command it is working on (list index), -1 = none. */
    public record FishView(byte state, ItemStack cargo, int amount, int target, int command) {}

    public static void open(ServerPlayer player, PipeNet net, BlockPos pos) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new PipeMenu(id, inv, pos, (ServerLevel) p.level()),
                Component.translatable("container.orefishfarm.pipe_network")), buf -> buf.writeBlockPos(pos));
    }

    /** Server side. */
    public PipeMenu(int id, Inventory inv, BlockPos pos, ServerLevel level) {
        super(Registry.PIPE_MENU.get(), id);
        this.pos = pos;
        this.level = level;
        addSlots(inv, new NetFish(this));
    }

    /** Client side. */
    public static PipeMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new PipeMenu(id, inv, buf.readBlockPos());
    }

    private PipeMenu(int id, Inventory inv, BlockPos pos) {
        super(Registry.PIPE_MENU.get(), id);
        this.pos = pos;
        this.level = null;
        addSlots(inv, new ItemStackHandler(PipeNet.MAX_FISH));
    }

    private void addSlots(Inventory inv, IItemHandler fishHandler) {
        for (int i = 0; i < PipeNet.MAX_FISH; i++) {
            final int slot = i;
            addSlot(new SlotItemHandler(fishHandler, i, FISH_X + (i % FISH_COLS) * 18, FISH_Y + (i / FISH_COLS) * 18) {
                @Override
                public boolean isActive() {
                    return slot < PipeMenu.this.capacity();
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
    }

    public BlockPos pos() {
        return this.pos;
    }

    @Nullable
    PipeNet net() {
        return this.level == null ? null : PipeNetworks.get(this.level).netAt(this.pos);
    }

    int capacity() {
        if (this.level == null) return this.capacity;
        PipeNet n = net();
        return n == null ? 0 : n.capacity();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null) return true;
        return net() != null && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int fishEnd = PipeNet.MAX_FISH;
        if (index < fishEnd) {
            if (!moveItemStackTo(stack, fishEnd, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!FishData.isHaulingFish(stack) || !moveItemStackTo(stack, 0, fishEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    // ------------------------------------------------------------------ snapshot

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.level == null || ++this.timer < 10) return;
        this.timer = 0;
        sendSnapshot(false);
    }

    void sendSnapshot(boolean force) {
        PipeNet n = net();
        if (n == null || this.level == null) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        n.writeSnapshot(this.level, buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        int h = java.util.Arrays.hashCode(data);
        if (!force && h == this.lastHash) return;
        this.lastHash = h;
        for (var p : this.level.players()) {
            if (p.containerMenu == this) PipePackets.send(p, new PipePackets.Snapshot(this.containerId, data));
        }
    }

    /** Client: reads a snapshot. */
    public void applySnapshot(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        this.capacity = buf.readVarInt();
        this.pipeCount = buf.readVarInt();
        this.endpoints.clear();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            int id = buf.readVarInt();
            String letter = buf.readUtf(), name = buf.readUtf();
            ResourceLocation block = ResourceLocation.tryParse(buf.readUtf());
            BlockPos p = BlockPos.of(buf.readLong());
            int face = buf.readByte();
            boolean missing = buf.readBoolean(), loaded = buf.readBoolean();
            int used = buf.readVarInt(), total = buf.readVarInt();
            this.endpoints.add(new EndpointView(id, letter, name, block, p, face, missing, loaded, used, total));
        }
        this.commands.clear();
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            ItemStack f = buf.readItem();
            this.commands.add(new CommandView(f, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        for (int i = 0; i < PipeNet.MAX_FISH; i++) {
            byte st = buf.readByte();
            this.fish[i] = st < 0 ? null : new FishView(st, buf.readItem(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    @Nullable
    public EndpointView endpoint(int id) {
        for (EndpointView e : this.endpoints) if (e.id() == id) return e;
        return null;
    }

    // ------------------------------------------------------------------ edits (server)

    void edit(PipePackets.Edit e) {
        PipeNet n = net();
        if (n == null || this.level == null) return;
        List<PipeNet.Command> cmds = n.commands;
        PipeNet.Command c = e.a() >= 0 && e.a() < cmds.size() ? cmds.get(e.a()) : null;
        switch (e.op()) {
            case PipePackets.RENAME -> n.rename(e.a(), e.text().trim());
            case PipePackets.ADD -> n.addCommand();
            case PipePackets.DELETE -> { if (c != null) { cmds.remove(c); n.dropUnusedMissing(); } }
            case PipePackets.MOVE -> {
                int to = e.a() + e.b();
                if (c != null && to >= 0 && to < cmds.size()) java.util.Collections.swap(cmds, e.a(), to);
            }
            case PipePackets.TOGGLE -> { if (c != null) c.on = !c.on; }
            case PipePackets.FROM -> { if (c != null) c.from = n.cycle(c.from, e.b()); }
            case PipePackets.TO -> { if (c != null) c.to = n.cycle(c.to, e.b()); }
            case PipePackets.KEEP -> { if (c != null) c.keep = e.b() < 0 ? -1 : Math.min(e.b(), 1_000_000); }
            case PipePackets.FILTER -> { if (c != null) c.filter = e.item().isEmpty() ? ItemStack.EMPTY : e.item().copyWithCount(1); }
            default -> {}
        }
        n.changed(this.level);
        sendSnapshot(true);
    }

    // ------------------------------------------------------------------ the network's fish, as slots (server)

    private record NetFish(PipeMenu menu) implements IItemHandlerModifiable {
        @Nullable private PipeNet n() { return this.menu.net(); }

        @Override public int getSlots() { return PipeNet.MAX_FISH; }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            PipeNet n = n();
            return n == null ? ItemStack.EMPTY : n.fishStack(slot);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            PipeNet n = n();
            if (n == null || this.menu.level == null) return;
            if (!n.fishStack(slot).isEmpty()) n.removeFish(this.menu.level, slot);
            if (!stack.isEmpty() && slot < n.capacity() && FishData.isHaulingFish(stack)) n.setFish(this.menu.level, slot, stack);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            PipeNet n = n();
            if (n == null || stack.isEmpty() || !isItemValid(slot, stack) || !n.fishStack(slot).isEmpty()) return stack;
            if (!simulate && this.menu.level != null) n.setFish(this.menu.level, slot, stack);
            ItemStack rest = stack.copy();
            rest.shrink(1);
            return rest;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            PipeNet n = n();
            if (n == null || amount <= 0 || n.fishStack(slot).isEmpty()) return ItemStack.EMPTY;
            if (simulate || this.menu.level == null) return n.fishStack(slot).copy();
            return n.removeFish(this.menu.level, slot);
        }

        @Override public int getSlotLimit(int slot) { return 1; }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            PipeNet n = n();
            return n != null && slot < n.capacity() && FishData.isHaulingFish(stack);
        }
    }
}

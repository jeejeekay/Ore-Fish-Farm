package IGoByJake.orefishfarm.pipe;

import IGoByJake.orefishfarm.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Pipe Tank packets: the network screen's lists (server to client), its edits (client to server), and fish legs:
 * one small packet when a fish starts swimming a leg (its path and its start and end times), sent only to players
 * near the path, so the client can draw it with no further traffic.
 */
public final class PipePackets {
    private PipePackets() {}

    public static final int RENAME = 0, ADD = 1, DELETE = 2, MOVE = 3, TOGGLE = 4, FROM = 5, TO = 6, KEEP = 7, FILTER = 8;

    /** The screen's lists, as written by {@link PipeNet#writeSnapshot}. */
    public record Snapshot(int containerId, byte[] data) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(this.containerId);
            buf.writeByteArray(this.data);
        }

        static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(buf.readVarInt(), buf.readByteArray());
        }
    }

    /** A change made in the screen. a: endpoint id or command index; b: a step, a delta or a number. */
    public record Edit(int containerId, int op, int a, int b, String text, ItemStack item) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(this.containerId);
            buf.writeVarInt(this.op);
            buf.writeVarInt(this.a);
            buf.writeVarInt(this.b);
            buf.writeUtf(this.text, 64);
            buf.writeItem(this.item);
        }

        static Edit read(FriendlyByteBuf buf) {
            return new Edit(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readItem());
        }

        void handle(@Nullable ServerPlayer player) {
            if (player != null && player.containerMenu instanceof PipeMenu m && m.containerId == this.containerId && m.stillValid(player)) m.edit(this);
        }
    }

    /** A fish starts a leg: swims along {@code path} (pipe positions) from game time start to end. Empty path = it's gone. */
    public record Leg(long uid, long[] path, long start, long end, ItemStack fish, ItemStack cargo) {
        void write(FriendlyByteBuf buf) {
            buf.writeLong(this.uid);
            buf.writeLongArray(this.path);
            buf.writeVarLong(this.start);
            buf.writeVarLong(this.end);
            buf.writeItem(this.fish);
            buf.writeItem(this.cargo);
        }

        static Leg read(FriendlyByteBuf buf) {
            return new Leg(buf.readLong(), buf.readLongArray(), buf.readVarLong(), buf.readVarLong(), buf.readItem(), buf.readItem());
        }
    }

    public static void register(int first) {
        Network.CHANNEL.messageBuilder(Snapshot.class, first, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Snapshot::write).decoder(Snapshot::read)
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IGoByJake.orefishfarm.client.PipeClient.snapshot(msg)))
                .add();
        Network.CHANNEL.messageBuilder(Edit.class, first + 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Edit::write).decoder(Edit::read)
                .consumerMainThread((msg, ctx) -> msg.handle(ctx.get().getSender()))
                .add();
        Network.CHANNEL.messageBuilder(Leg.class, first + 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Leg::write).decoder(Leg::read)
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IGoByJake.orefishfarm.client.PipeClient.leg(msg)))
                .add();
    }

    public static void send(ServerPlayer player, Object msg) {
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** Client: sends an edit for the open network screen. */
    public static void edit(int containerId, int op, int a, int b, String text, ItemStack item) {
        Network.CHANNEL.sendToServer(new Edit(containerId, op, a, b, text, item));
    }

    static void sendLeg(ServerLevel level, PipeNet net, PipeNet.HaulFish f, long[] path) {
        ItemStack cargo = f.state == PipeNet.TO_SOURCE || f.amount <= 0 ? ItemStack.EMPTY : f.cargo.copyWithCount(1);
        near(level, path, new Leg(f.uid, path, f.legStart, f.legEnd, f.bucket.copyWithCount(1), cargo));
    }

    /** The fish is gone from its pipe (taken out, or its pipe broken): clients stop drawing it. */
    static void sendLegEnd(ServerLevel level, @Nullable PipeNet net, PipeNet.HaulFish f) {
        near(level, new long[]{f.at}, new Leg(f.uid, new long[0], 0, 0, ItemStack.EMPTY, ItemStack.EMPTY));
    }

    /** To players within 64 blocks of the path's box. */
    private static void near(ServerLevel level, long[] path, Leg msg) {
        if (path.length == 0 || level.players().isEmpty()) return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long l : path) {
            int x = BlockPos.getX(l), y = BlockPos.getY(l), z = BlockPos.getZ(l);
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
            x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
        }
        double cx = (x0 + x1) / 2.0 + 0.5, cy = (y0 + y1) / 2.0 + 0.5, cz = (z0 + z1) / 2.0 + 0.5;
        double r = 64 + Math.sqrt((double) (x1 - x0) * (x1 - x0) + (double) (y1 - y0) * (y1 - y0) + (double) (z1 - z0) * (z1 - z0)) / 2;
        Network.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(cx, cy, cz, r, level.dimension())), msg);
    }
}

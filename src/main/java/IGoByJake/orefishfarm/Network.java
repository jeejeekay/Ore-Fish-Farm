package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The server's fish and food lists (tooltips, slot checks), its tackle data (JEI, tackle popup), and the tackle popup's clicks. */
public final class Network {
    private static final String VERSION = "6";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(OreFishFarm.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private Network() {}

    public static void register() {
        CHANNEL.messageBuilder(FishData.Synced.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> FishData.write(buf))
                .decoder(FishData::read)
                .consumerMainThread((msg, ctx) -> FishData.applySynced(msg))
                .add();
        CHANNEL.messageBuilder(IGoByJake.orefishfarm.tackle.Tackle.Synced.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> IGoByJake.orefishfarm.tackle.Tackle.write(buf))
                .decoder(IGoByJake.orefishfarm.tackle.Tackle::read)
                .consumerMainThread((msg, ctx) -> IGoByJake.orefishfarm.tackle.Tackle.applySynced(msg))
                .add();
        CHANNEL.messageBuilder(IGoByJake.orefishfarm.tackle.TackleEvents.Action.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(IGoByJake.orefishfarm.tackle.TackleEvents.Action::write)
                .decoder(IGoByJake.orefishfarm.tackle.TackleEvents.Action::read)
                .consumerMainThread((msg, ctx) -> msg.handle(ctx.get().getSender()))
                .add();
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        FishData.Synced msg = new FishData.Synced(java.util.Map.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()); // contents are written from FishData directly
        ServerPlayer player = event.getPlayer();
        var tackle = new IGoByJake.orefishfarm.tackle.Tackle.Synced(java.util.Map.of()); // written from Tackle directly
        if (player != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), tackle);
        } else {
            CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
            CHANNEL.send(PacketDistributor.ALL.noArg(), tackle);
        }
    }
}

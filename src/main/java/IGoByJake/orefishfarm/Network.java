package IGoByJake.orefishfarm;

import IGoByJake.orefishfarm.data.FishData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** One packet: the server's fish and food lists, so clients can show power in tooltips and check slots. */
public final class Network {
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(OreFishFarm.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private Network() {}

    public static void register() {
        CHANNEL.messageBuilder(FishData.Synced.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> FishData.write(buf))
                .decoder(FishData::read)
                .consumerMainThread((msg, ctx) -> FishData.applySynced(msg))
                .add();
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        FishData.Synced msg = new FishData.Synced(java.util.Map.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()); // contents are written from FishData directly
        ServerPlayer player = event.getPlayer();
        if (player != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
        else CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }
}

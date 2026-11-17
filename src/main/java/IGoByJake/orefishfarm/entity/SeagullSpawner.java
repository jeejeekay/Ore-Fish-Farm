package IGoByJake.orefishfarm.entity;

import IGoByJake.orefishfarm.Config;
import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Keeps about one seagull near each player in the allowed dimensions (rarely two), and makes sure each player's
 * first gift (orefishfarm:gameplay/seagull/first_gift: 9 string) is on its way at a random moment in their first
 * few minutes. Runs once a second per player.
 */
@Mod.EventBusSubscriber(modid = OreFishFarm.MODID)
public final class SeagullSpawner {
    private static final String KEY = "orefishfarm_seagull";

    private SeagullSpawner() {}

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY)) persisted.put(KEY, new CompoundTag());
        return persisted.getCompound(KEY);
    }

    public static void markFirstGiftDelivered(Player p) {
        data(p).putBoolean("Delivered", true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!Config.SEAGULLS.get() || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        if (!Config.SEAGULL_DIMENSIONS.get().contains(level.dimension().location().toString())) return;

        CompoundTag d = data(player);
        int seconds = d.getInt("Seconds") + 1;
        d.putInt("Seconds", seconds);
        if (!d.contains("GiftAt")) d.putInt("GiftAt", 45 + player.getRandom().nextInt(Math.max(1, Config.FIRST_GIFT_SECONDS.get() - 150)));

        List<Seagull> near = level.getEntitiesOfClass(Seagull.class, player.getBoundingBox().inflate(96));
        boolean firstDue = !d.getBoolean("Delivered") && seconds >= d.getInt("GiftAt");

        if (near.isEmpty() && (seconds >= 30 || firstDue) && (firstDue || seconds % 10 == 0)) {
            Seagull g = spawn(level, player);
            if (g != null) near = List.of(g);
        } else if (near.size() == 1 && seconds % 60 == 0 && player.getRandom().nextFloat() < Config.SECOND_SEAGULL_CHANCE.get()) {
            spawn(level, player);
        }

        // First gift: hand it to a gull if none is already bringing it; retry every two minutes until it arrives.
        if (firstDue && !near.isEmpty() && seconds >= d.getInt("NextTry")) {
            Seagull g = near.get(0);
            if (!g.busyDelivering()) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, player.position())
                        .withParameter(LootContextParams.THIS_ENTITY, g)
                        .create(LootContextParamSets.GIFT);
                g.queueGift(player, level.getServer().getLootData().getLootTable(Seagull.FIRST_GIFT).getRandomItems(params), true);
                d.putInt("NextTry", seconds + 120);
            }
        }
    }

    private static Seagull spawn(ServerLevel level, ServerPlayer player) {
        double a = player.getRandom().nextDouble() * Math.PI * 2;
        double x = player.getX() + Math.cos(a) * 28;
        double z = player.getZ() + Math.sin(a) * 28;
        double y = Math.min(level.getMaxBuildHeight() - 4, player.getY() + 14);
        Seagull g = Registry.SEAGULL.get().create(level);
        if (g == null) return null;
        g.moveTo(x, y, z, (float) Math.toDegrees(-a), 0);
        g.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(g);
        return g;
    }
}

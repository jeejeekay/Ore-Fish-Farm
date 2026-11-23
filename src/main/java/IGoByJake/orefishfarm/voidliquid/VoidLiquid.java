package IGoByJake.orefishfarm.voidliquid;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Void Liquid: the void, scooped up. You get the first Void Bucket by aiming an empty bucket straight down into the
 * void (nothing below you all the way to the bottom of the world); after that, void liquid can be bucketed like
 * any other liquid. It spreads 2 blocks, and anything alive in it is hurt exactly like the void hurts (4 a tick,
 * through armor). Creative and spectator players are left alone. The Void Bucket is the void habitat's bucket.
 */
public final class VoidLiquid {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, OreFishFarm.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, OreFishFarm.MODID);

    public static final RegistryObject<FluidType> TYPE = FLUID_TYPES.register("void_liquid", () -> new VoidFluidType(
            FluidType.Properties.create()
                    .descriptionId("block.orefishfarm.void_liquid")
                    .density(3000).viscosity(6000).temperature(0)
                    .motionScale(0.0023D).canSwim(false).canDrown(true).canExtinguish(true)
                    .canConvertToSource(false).supportsBoating(false).fallDistanceModifier(0F)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final RegistryObject<FlowingFluid> SOURCE = FLUIDS.register("void_liquid", () -> new ForgeFlowingFluid.Source(props()));
    public static final RegistryObject<FlowingFluid> FLOWING = FLUIDS.register("flowing_void_liquid", () -> new ForgeFlowingFluid.Flowing(props()));

    public static final RegistryObject<LiquidBlock> BLOCK = Registry.BLOCKS.register("void_liquid", () -> new LiquidBlock(SOURCE,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).replaceable().noCollission().strength(100F)
                    .pushReaction(PushReaction.DESTROY).noLootTable().liquid()));

    public static final RegistryObject<Item> BUCKET = Registry.ITEMS.register("void_bucket",
            () -> new BucketItem(SOURCE, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static ForgeFlowingFluid.Properties props() {
        // Levels drop 3 a block from a source (8 -> 5 -> 2), so it reaches 2 blocks out.
        return new ForgeFlowingFluid.Properties(TYPE, SOURCE, FLOWING)
                .bucket(BUCKET).block(BLOCK)
                .levelDecreasePerBlock(3).slopeFindDistance(2).tickRate(10).explosionResistance(100F);
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    /** Living things in void liquid take the void's damage. */
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !e.isInFluidType(TYPE.get())) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        e.hurt(e.damageSources().fellOutOfWorld(), 4.0F);
    }

    /** An empty bucket aimed into the void comes back full of it. */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        if (!held.is(Items.BUCKET)) return;
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (!aimsIntoVoid(level, player)) return;
        if (!level.isClientSide) {
            ItemStack filled = ItemUtils.createFilledResult(held, player, new ItemStack(BUCKET.get()));
            player.setItemInHand(event.getHand(), filled);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1F, 0.6F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 0.5F);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }

    /**
     * The player looks well downwards (at least 45 degrees) and nothing is in the way, blocks or liquids, all the
     * way to the bottom of the world. Every chunk along the way must already be loaded.
     */
    static boolean aimsIntoVoid(Level level, Player player) {
        Vec3 look = player.getLookAngle();
        if (look.y > -0.7) return false;
        Vec3 eye = player.getEyePosition();
        double drop = eye.y - (level.getMinBuildHeight() - 1);
        if (drop <= 0) return true;
        Vec3 end = eye.add(look.scale(drop / -look.y));
        for (double t = 0; t <= 1.0001; t += 16.0 / Math.max(16.0, eye.distanceTo(end))) {
            Vec3 p = eye.lerp(end, Math.min(1, t));
            if (!level.hasChunkAt(BlockPos.containing(p))) return false;
        }
        if (!level.hasChunkAt(BlockPos.containing(end))) return false;
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private VoidLiquid() {}
}

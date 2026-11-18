package IGoByJake.orefishfarm.fishing;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.ItemFishedEvent;

import java.util.List;

/**
 * The bobber cast by any rod in {@code #orefishfarm:lava_rods} or {@code #orefishfarm:void_rods} (FishingEvents
 * swaps it in for the vanilla bobber). It flies like a normal bobber, then:
 * <ul>
 *   <li>lands in water: turns back into a vanilla bobber, so water fishing is untouched;</li>
 *   <li>lands in lava (lava rods): floats and fishes the lava table;</li>
 *   <li>drops off an edge over the open void (void rods, in a dimension type tagged
 *       {@code #orefishfarm:void_fishing}): stops a few blocks down, hovers and fishes the void table.</li>
 * </ul>
 * What it catches depends on the liquid and the dimension: {@code orefishfarm:gameplay/lava_fishing/<dim ns>/<dim path>}
 * (for example {@code .../lava_fishing/minecraft/the_nether}) if that table exists, otherwise the liquid's
 * plain table {@code orefishfarm:gameplay/lava_fishing} or {@code orefishfarm:gameplay/void_fishing}. Bite timing is vanilla's (100-600 ticks, minus Lure); Luck of the Sea
 * counts. Catches go straight to the player, since nothing could be pulled out of lava or the void.
 */
public class OreFishingHook extends FishingHook {
    public static final TagKey<net.minecraft.world.item.Item> LAVA_RODS = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "lava_rods"));
    public static final TagKey<net.minecraft.world.item.Item> VOID_RODS = ItemTags.create(new ResourceLocation(OreFishFarm.MODID, "void_rods"));
    public static final TagKey<DimensionType> VOID_FISHING = TagKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(OreFishFarm.MODID, "void_fishing"));
    public static final ResourceLocation LAVA_TABLE = new ResourceLocation(OreFishFarm.MODID, "gameplay/lava_fishing");
    public static final ResourceLocation VOID_TABLE = new ResourceLocation(OreFishFarm.MODID, "gameplay/void_fishing");
    /** Persistent-data flag on a vanilla bobber this hook handed over to, so it isn't swapped again. */
    public static final String VANILLA_TAG = "orefishfarm_vanilla";
    /** How far below the cast a void bobber falls before it settles. */
    private static final double VOID_DROP = 4;

    private static final EntityDataAccessor<Boolean> BITING = SynchedEntityData.defineId(OreFishingHook.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(OreFishingHook.class, EntityDataSerializers.BYTE);
    private static final byte FLYING = 0, LAVA = 1, VOID = 2;

    private boolean lavaOk, voidOk;
    private int luck, lure;
    private double castY, anchorY;
    private int life, nibble, timeUntilLured, timeUntilHooked;
    private float fishAngle;

    public OreFishingHook(EntityType<? extends FishingHook> type, Level level) {
        super(type, level);
    }

    /** Takes over from a vanilla bobber that was just cast: same place, same speed. */
    public OreFishingHook(Player player, FishingHook from, int luck, int lure, boolean lavaOk, boolean voidOk) {
        this(Registry.ORE_FISHING_HOOK.get(), player.level());
        this.luck = Math.max(0, luck);
        this.lure = Math.max(0, lure);
        this.lavaOk = lavaOk;
        this.voidOk = voidOk;
        this.setOwner(player); // also sets player.fishing
        this.moveTo(from.getX(), from.getY(), from.getZ(), from.getYRot(), from.getXRot());
        this.setDeltaMovement(from.getDeltaMovement());
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.castY = player.getY();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BITING, false);
        this.entityData.define(MODE, FLYING);
    }

    private byte mode() { return this.entityData.get(MODE); }

    private boolean holdsRod(Player player) {
        return player.getMainHandItem().canPerformAction(ToolActions.FISHING_ROD_CAST) || player.getOffhandItem().canPerformAction(ToolActions.FISHING_ROD_CAST);
    }

    private boolean shouldStop(Player player) {
        if (!player.isRemoved() && player.isAlive() && holdsRod(player) && this.distanceToSqr(player) <= 1024.0D) return false;
        this.discard();
        return true;
    }

    @Override
    public void tick() {
        this.baseTick();
        Player player = this.getPlayerOwner();
        if (player == null) {
            if (!this.level().isClientSide) this.discard();
            return;
        }
        if (!this.level().isClientSide && shouldStop(player)) return;
        if (this.getY() < this.level().getMinBuildHeight() - 64) {
            if (!this.level().isClientSide) this.discard();
            return;
        }

        if (this.onGround()) {
            if (++this.life >= 1200) {
                this.discard();
                return;
            }
        } else {
            this.life = 0;
        }

        BlockPos pos = this.blockPosition();
        FluidState fluid = this.level().getFluidState(pos);
        byte mode = mode();

        if (mode == FLYING && !this.level().isClientSide) {
            if (fluid.is(FluidTags.WATER)) {
                handToVanilla(player);
                return;
            }
            if (this.lavaOk && fluid.is(FluidTags.LAVA)) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.3D, 0.2D, 0.3D));
                this.entityData.set(MODE, LAVA);
                return;
            }
            if (this.voidOk && this.getY() < this.castY - VOID_DROP && overVoid()) {
                this.anchorY = this.getY();
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.2D, 0.0D, 0.2D));
                this.entityData.set(MODE, VOID);
                return;
            }
        }

        if (mode == LAVA) {
            float height = fluid.is(FluidTags.LAVA) ? fluid.getHeight(this.level(), pos) : 0.0F;
            Vec3 v = this.getDeltaMovement();
            double d = this.getY() + v.y - pos.getY() - height;
            if (Math.abs(d) < 0.01D) d += Math.signum(d) * 0.1D;
            this.setDeltaMovement(v.x * 0.9D, v.y - d * this.random.nextFloat() * 0.2D, v.z * 0.9D);
            if (height > 0.0F) {
                if (biting()) this.setDeltaMovement(this.getDeltaMovement().add(0, -0.1D * this.random.nextFloat() * this.random.nextFloat(), 0));
                if (!this.level().isClientSide) catching(true);
            }
        } else if (mode == VOID) {
            // Hover at the settle height with a slow bob; a bite tugs it down.
            Vec3 v = this.getDeltaMovement();
            double target = this.anchorY + Mth.sin(this.tickCount * 0.08F) * 0.08 - (biting() ? 0.25 : 0);
            this.setDeltaMovement(v.x * 0.8D, (target - this.getY()) * 0.2D, v.z * 0.8D);
            if (!this.level().isClientSide) catching(false);
        }

        boolean floats = (mode == LAVA && fluid.is(FluidTags.LAVA)) || mode == VOID;
        if (!floats) this.setDeltaMovement(this.getDeltaMovement().add(0, -0.03D, 0));
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.updateRotation();
        if (mode == FLYING && (this.onGround() || this.horizontalCollision)) this.setDeltaMovement(Vec3.ZERO);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92D));
        this.reapplyPosition();
    }

    /** Nothing at all below, all the way down: the open void. */
    private boolean overVoid() {
        if (!this.level().dimensionTypeRegistration().is(VOID_FISHING)) return false;
        int top = this.level().getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(this.getX()), Mth.floor(this.getZ()));
        return top <= this.level().getMinBuildHeight();
    }

    /** Water: carry on as a plain vanilla bobber, with the same Luck and Lure. */
    private void handToVanilla(Player player) {
        Vec3 pos = this.position(), speed = this.getDeltaMovement();
        this.discard();
        FishingHook v = new FishingHook(player, this.level(), this.luck, this.lure);
        v.getPersistentData().putBoolean(VANILLA_TAG, true);
        v.moveTo(pos.x, pos.y, pos.z, this.getYRot(), this.getXRot());
        v.setDeltaMovement(speed);
        this.level().addFreshEntity(v);
    }

    private boolean biting() {
        return this.entityData.get(BITING);
    }

    /** Vanilla's bite timer, with lava or void particles. */
    private void catching(boolean lava) {
        ServerLevel level = (ServerLevel) this.level();
        ParticleOptions trail = lava ? ParticleTypes.SMOKE : ParticleTypes.PORTAL;
        ParticleOptions spark = lava ? ParticleTypes.LAVA : ParticleTypes.REVERSE_PORTAL;
        if (this.nibble > 0) {
            if (--this.nibble <= 0) {
                this.timeUntilLured = 0;
                this.timeUntilHooked = 0;
                this.entityData.set(BITING, false);
            }
        } else if (this.timeUntilHooked > 0) {
            if (--this.timeUntilHooked > 0) {
                this.fishAngle += (float) this.random.triangle(0.0D, 9.188D);
                float a = this.fishAngle * ((float) Math.PI / 180F);
                double r = this.timeUntilHooked * 0.1F;
                double x = this.getX() + Mth.sin(a) * r;
                double z = this.getZ() + Mth.cos(a) * r;
                double y = lava ? this.blockPosition().getY() + 1.0D : this.getY();
                if (this.random.nextFloat() < 0.15F) level.sendParticles(trail, x, y, z, 1, 0, 0, 0, 0.0D);
                if (this.random.nextFloat() < 0.05F) level.sendParticles(spark, x, y, z, 1, 0, 0, 0, 0.0D);
            } else {
                double y = this.getY() + 0.5D;
                if (lava) {
                    this.playSound(SoundEvents.LAVA_POP, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
                    level.sendParticles(ParticleTypes.LAVA, this.getX(), y, this.getZ(), 4, 0.1, 0, 0.1, 0.0D);
                    level.sendParticles(ParticleTypes.FLAME, this.getX(), y, this.getZ(), 8, 0.2, 0, 0.2, 0.02D);
                } else {
                    this.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.2F + this.random.nextFloat() * 0.4F);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), y, this.getZ(), 12, 0.2, 0.2, 0.2, 0.05D);
                }
                this.nibble = Mth.nextInt(this.random, 20, 40);
                this.entityData.set(BITING, true);
            }
        } else if (this.timeUntilLured > 0) {
            if (--this.timeUntilLured <= 0) {
                this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
            } else if (this.random.nextFloat() < 0.02F) {
                level.sendParticles(trail, this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.2, 0, 0.2, 0.01D);
            }
        } else {
            this.timeUntilLured = Math.max(20, Mth.nextInt(this.random, 100, 600) - this.lure * 20 * 5);
        }
    }

    @Override
    public int retrieve(ItemStack rod) {
        Player player = this.getPlayerOwner();
        if (this.level().isClientSide || player == null || shouldStop(player)) return 0;
        int damage = 0;
        byte mode = mode();
        if (this.nibble > 0 && mode != FLYING) {
            ServerLevel level = (ServerLevel) this.level();
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, this.position())
                    .withParameter(LootContextParams.TOOL, rod)
                    .withParameter(LootContextParams.THIS_ENTITY, this)
                    .withLuck(this.luck + player.getLuck())
                    .create(LootContextParamSets.FISHING);
            LootTable table = tableFor(level, mode == LAVA ? LAVA_TABLE : VOID_TABLE);
            List<ItemStack> loot = table.getRandomItems(params);
            ItemFishedEvent event = new ItemFishedEvent(loot, this.onGround() ? 2 : 1, this);
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                this.discard();
                return event.getRodDamage();
            }
            CriteriaTriggers.FISHING_ROD_HOOKED.trigger((ServerPlayer) player, rod, this, loot);
            for (ItemStack stack : loot) {
                if (stack.is(ItemTags.FISHES)) player.awardStat(Stats.FISH_CAUGHT, 1);
                if (!player.getInventory().add(stack)) {
                    ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack);
                    drop.setNoPickUpDelay();
                    level.addFreshEntity(drop);
                }
                level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D, player.getZ() + 0.5D, this.random.nextInt(6) + 1));
            }
            player.playSound(SoundEvents.ITEM_PICKUP, 0.3F, 1.0F);
            damage = 1;
        }
        if (this.onGround()) damage = 2;
        this.discard();
        return damage;
    }

    /** The dimension's own table for this liquid if a datapack has one, else the liquid's plain table. */
    private static LootTable tableFor(ServerLevel level, ResourceLocation base) {
        ResourceLocation dim = level.dimension().location();
        ResourceLocation own = new ResourceLocation(base.getNamespace(), base.getPath() + "/" + dim.getNamespace() + "/" + dim.getPath());
        LootTable t = level.getServer().getLootData().getLootTable(own);
        return t != LootTable.EMPTY ? t : level.getServer().getLootData().getLootTable(base);
    }
}

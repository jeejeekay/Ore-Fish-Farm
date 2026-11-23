package IGoByJake.orefishfarm.voidliquid;

import IGoByJake.orefishfarm.OreFishFarm;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * The "green screen": every face of void liquid near the camera is drawn again with the void_liquid shader, which
 * paints a starfield that depends only on the direction you look (like the sky), so the liquid looks like a hole
 * onto one fixed background. The chunk renderer still draws the liquid with its own dark texture underneath, which
 * is what shows beyond the overlay's range, or if a shader pack replaces the shader.
 */
public final class VoidLiquidRenderer {
    /** How far (in chunks) void liquid gets the starfield. */
    private static final int RANGE = 8;
    private static ShaderInstance shader;
    private static final LongArrayList blocks = new LongArrayList();

    @Mod.EventBusSubscriber(modid = OreFishFarm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onShaders(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(OreFishFarm.MODID, "void_liquid"),
                    DefaultVertexFormat.POSITION), s -> shader = s);
        }
    }

    @Mod.EventBusSubscriber(modid = OreFishFarm.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        /** Finds the void liquid near the player, once a tick. Chunk sections that can't hold it are skipped by their palette. */
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            blocks.clear();
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || mc.player == null) return;
            int range = Math.min(RANGE, mc.options.getEffectiveRenderDistance());
            int cx = SectionPos.blockToSectionCoord(mc.player.getBlockX()), cz = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = cx - range; x <= cx + range; x++) {
                for (int z = cz - range; z <= cz + range; z++) {
                    LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);
                    if (chunk == null) continue;
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int i = 0; i < sections.length; i++) {
                        LevelChunkSection s = sections[i];
                        if (s == null || s.hasOnlyAir() || !s.maybeHas(VoidLiquidRenderer::isVoid)) continue;
                        int baseY = SectionPos.sectionToBlockCoord(chunk.getMinSection() + i);
                        for (int ly = 0; ly < 16; ly++)
                            for (int lz = 0; lz < 16; lz++)
                                for (int lx = 0; lx < 16; lx++)
                                    if (isVoid(s.getBlockState(lx, ly, lz)))
                                        blocks.add(p.set(x * 16 + lx, baseY + ly, z * 16 + lz).asLong());
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onRender(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || blocks.isEmpty() || shader == null) return;
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            draw(level, event.getPoseStack(), event.getCamera().getPosition());
        }
    }

    private static boolean isVoid(BlockState s) {
        return isVoid(s.getFluidState());
    }

    private static boolean isVoid(FluidState f) {
        return !f.isEmpty() && f.getFluidType() == VoidLiquid.TYPE.get();
    }

    private static void draw(ClientLevel level, PoseStack pose, Vec3 cam) {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int quads = 0;
        for (int i = 0; i < blocks.size(); i++) {
            pos.set(blocks.getLong(i));
            if (cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE * 16.0 * RANGE * 16.0) continue;
            quads += block(level, pos.immutable(), buf, (float) (pos.getX() - cam.x), (float) (pos.getY() - cam.y), (float) (pos.getZ() - cam.z));
        }
        if (quads == 0) {
            buf.end().release();
            return;
        }
        // Positions are camera-relative world offsets, so the shader can read the view direction from them; the
        // camera's rotation goes in the model-view matrix.
        var mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1F, -10F);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.polygonOffset(0F, 0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** The liquid's faces at one block, shaped like the chunk renderer shapes them. Returns how many quads. */
    private static int block(ClientLevel level, BlockPos pos, BufferBuilder buf, float x, float y, float z) {
        FluidState fluid = level.getFluidState(pos);
        if (!isVoid(fluid)) return 0;
        float own = height(level, pos);
        float ne, nw, se, sw;
        if (own >= 1F) {
            ne = nw = se = sw = 1F;
        } else {
            float n = height(level, pos.north()), s = height(level, pos.south()), e = height(level, pos.east()), w = height(level, pos.west());
            ne = corner(level, own, n, e, pos.north().east());
            nw = corner(level, own, n, w, pos.north().west());
            se = corner(level, own, s, e, pos.south().east());
            sw = corner(level, own, s, w, pos.south().west());
        }
        int q = 0;
        if (!isVoid(level.getFluidState(pos.above()))) {
            float d = 0.001F;
            quad(buf, x, y + nw - d, z, x, y + sw - d, z + 1, x + 1, y + se - d, z + 1, x + 1, y + ne - d, z);
            q++;
        }
        if (show(level, pos, Direction.DOWN)) {
            quad(buf, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
            q++;
        }
        if (show(level, pos, Direction.NORTH)) { side(buf, x, y, z, x + 1, z, nw, ne); q++; }
        if (show(level, pos, Direction.SOUTH)) { side(buf, x + 1, y, z + 1, x, z + 1, se, sw); q++; }
        if (show(level, pos, Direction.WEST)) { side(buf, x, y, z + 1, x, z, sw, nw); q++; }
        if (show(level, pos, Direction.EAST)) { side(buf, x + 1, y, z, x + 1, z + 1, ne, se); q++; }
        return q;
    }

    private static void side(BufferBuilder buf, float x0, float y, float z0, float x1, float z1, float h0, float h1) {
        quad(buf, x0, y, z0, x1, y, z1, x1, y + h1, z1, x0, y + h0, z0);
    }

    private static void quad(BufferBuilder buf, float... v) {
        for (int i = 0; i < 12; i += 3) buf.vertex(v[i], v[i + 1], v[i + 2]).endVertex();
    }

    /** A face shows unless the next block is void liquid too or a full block covers it. */
    private static boolean show(ClientLevel level, BlockPos pos, Direction dir) {
        BlockPos n = pos.relative(dir);
        BlockState s = level.getBlockState(n);
        if (isVoid(s.getFluidState())) return false;
        return !s.isSolidRender(level, n);
    }

    /** As LiquidBlockRenderer.getHeight: 1 with void liquid above, its own height, 0 for open space, -1 for solid. */
    private static float height(ClientLevel level, BlockPos pos) {
        FluidState f = level.getFluidState(pos);
        if (isVoid(f)) return isVoid(level.getFluidState(pos.above())) ? 1F : f.getOwnHeight();
        return level.getBlockState(pos).isSolid() ? -1F : 0F;
    }

    /** As LiquidBlockRenderer.calculateAverageHeight. */
    private static float corner(ClientLevel level, float own, float a, float b, BlockPos diagonal) {
        if (a >= 1F || b >= 1F) return 1F;
        float[] sum = new float[2];
        if (a > 0F || b > 0F) {
            float d = height(level, diagonal);
            if (d >= 1F) return 1F;
            add(sum, d);
        }
        add(sum, own);
        add(sum, b);
        add(sum, a);
        return sum[1] == 0 ? own : sum[0] / sum[1];
    }

    private static void add(float[] sum, float h) {
        if (h >= 0.8F) {
            sum[0] += h * 10F;
            sum[1] += 10F;
        } else if (h >= 0F) {
            sum[0] += h;
            sum[1] += 1F;
        }
    }

    private VoidLiquidRenderer() {}
}

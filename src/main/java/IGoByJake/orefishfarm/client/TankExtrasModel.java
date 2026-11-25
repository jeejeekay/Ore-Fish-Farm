package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.block.TankBlock;
import IGoByJake.orefishfarm.block.TankBlockEntity;
import IGoByJake.orefishfarm.block.TankGeometry;
import IGoByJake.orefishfarm.block.TankLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The fish tank's block model: the block state's own parts (the 3 px base, the floor, its decor, the coil) plus all
 * of the glass and liquid, which {@link TankGeometry} works out from the block's shape (model data
 * {@link TankBlockEntity#EXTRAS}). The walls' outer faces are on the tripwire layer, which always draws after the
 * translucent layer, so the outside of the glass is over everything behind it from every angle and never hides
 * it. The glass seen from inside or across the tank (inner faces, the top outline, steps' undersides, corner
 * pieces) is on the translucent layer with the liquid. (All on tripwire, the front glass sometimes drew first and
 * hid the far walls' edges, depending on Minecraft's occasional re-sort: 2.26.4.) Outer faces are culled against
 * solid neighbours, so nothing ever shares their plane. Baked once per shape and look.
 */
public class TankExtrasModel extends BakedModelWrapper<BakedModel> {
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final ResourceLocation MODEL = new ResourceLocation("orefishfarm", "block/fish_tank_glass");
    /** Per shape and look: [layer 0 = tripwire (outer glass), 1 = translucent (liquid, inner glass)][cull side, 6 = none] */
    private static final Map<Long, List<BakedQuad>[][]> CACHE = new ConcurrentHashMap<>();
    private static final Direction[] DIRS = Direction.values();

    public TankExtrasModel(BakedModel base) {
        super(base);
    }

    /** Nothing extra to load: the glass and liquid are built in code. */
    public static void register(ModelEvent.RegisterAdditional event) {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Wraps every fish tank state's model. */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (BlockState state : Registry.FISH_TANK.get().getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            BakedModel base = models.get(key);
            if (base != null && !(base instanceof TankExtrasModel)) models.put(key, new TankExtrasModel(base));
        }
        clearCache();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        if (state == null) return base;
        boolean glass = renderType == null || renderType == RenderType.tripwire(), liquid = renderType == null || renderType == RenderType.translucent();
        if (!glass && !liquid) return base;
        Integer shape = data.get(TankBlockEntity.EXTRAS);
        int s = shape == null || (shape & TankGeometry.KNOWN) == 0 ? lone(state) : shape;
        TankLook look = state.getValue(TankBlock.LOOK);
        List<BakedQuad>[][] baked = CACHE.computeIfAbsent(((long) look.ordinal() << 32) | (s & 0xFFFFFFFFL), k -> bake(s, look));
        int slot = side == null ? 6 : side.ordinal();
        List<BakedQuad> add = glass && liquid ? concat(baked[0][slot], baked[1][slot]) : baked[glass ? 0 : 1][slot];
        if (add.isEmpty()) return base;
        if (base.isEmpty()) return add;
        return concat(base, add);
    }

    private static List<BakedQuad> concat(List<BakedQuad> a, List<BakedQuad> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        List<BakedQuad> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /** No model data (an item frame, a piston): go by the block state's own joins. */
    private static int lone(BlockState state) {
        int s = TankGeometry.KNOWN;
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < 4; i++) if (state.getValue(TankBlock.side(sides[i]))) s |= TankGeometry.joined(i);
        if (state.getValue(TankBlock.UP)) s |= TankGeometry.UP;
        if (state.getValue(TankBlock.DOWN)) s |= TankGeometry.DOWN;
        return s;
    }

    /** No ambient occlusion: it would shade glass and water darker toward every corner. */
    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return ChunkRenderTypeSet.union(super.getRenderTypes(state, rand, data), ChunkRenderTypeSet.of(RenderType.translucent(), RenderType.tripwire()));
    }

    // ------------------------------------------------------------------ baking

    private static TextureAtlasSprite sprite(String id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation(id));
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[][] bake(int shape, TankLook look) {
        List<BakedQuad>[][] out = new List[2][7];
        for (int l = 0; l < 2; l++) for (int k = 0; k < 7; k++) out[l][k] = new ArrayList<>();
        String liquid = switch (look) {
            case LAVA -> "minecraft:block/lava_still";
            case VOID -> "orefishfarm:block/tank_void";
            default -> "minecraft:block/water_still";
        };
        TextureAtlasSprite fill = sprite(liquid);
        for (TankGeometry.Face f : TankGeometry.faces(shape)) {
            Direction dir = DIRS[f.dir];
            float[] uv = defaultUv(dir, f.from, f.to);
            TextureAtlasSprite sp;
            int tint = -1;
            boolean shade = true;
            if (f.glass) {
                int mask = mask(dir, f.fades);
                sp = sprite("orefishfarm:block/tank_glass_" + mask);
                window(uv, mask);
            } else {
                sp = fill;
                if (look == TankLook.WATER) tint = 0;
                if (look == TankLook.LAVA) shade = false;
            }
            Direction cull = f.cull ? dir : null;
            BlockElementFace face = new BlockElementFace(cull, tint, "", new BlockFaceUV(uv, 0));
            BakedQuad q = BAKERY.bakeQuad(new Vector3f(f.from[0], f.from[1], f.from[2]), new Vector3f(f.to[0], f.to[1], f.to[2]),
                    face, sp, dir, BlockModelRotation.X0_Y0, null, shade, MODEL);
            if (!f.glass && look == TankLook.LAVA) q = withAlpha(q, 0xB4);
            out[f.glass && f.cull ? 0 : 1][cull == null ? 6 : cull.ordinal()].add(q);
        }
        for (int l = 0; l < 2; l++) for (int k = 0; k < 7; k++) out[l][k] = List.copyOf(out[l][k]);
        return out;
    }

    /**
     * Lines a glass texture up with its face: along an axis with a fade at only one end, the texture's fading end
     * goes on that edge; with fades at both ends, the whole texture spans the face (a face across a whole block
     * keeps Minecraft's own uv, so the fades land on the block's edges); with none, the block's own uv.
     */
    private static void window(float[] uv, int mask) {
        for (int axis = 0; axis < 2; axis++) {
            int a = axis * 1, lo = mask >> (axis * 2) & 1, hi = mask >> (axis * 2 + 1) & 1;
            float u0 = uv[a], u1 = uv[a + 2], len = Math.abs(u1 - u0);
            boolean flipped = u1 < u0;
            float min, max;
            if (lo == 1 && hi == 0) { min = 0; max = len; }
            else if (hi == 1 && lo == 0) { min = 16 - len; max = 16; }
            else if (lo == 1) { min = 0; max = 16; }
            else continue;
            uv[a] = flipped ? max : min;
            uv[a + 2] = flipped ? min : max;
        }
    }

    /** Lava, see-through like the tanks always had it (the old models' forge_data colour 0xB4FFFFFF). */
    private static BakedQuad withAlpha(BakedQuad q, int alpha) {
        int[] v = q.getVertices().clone();
        int stride = v.length / 4;
        for (int i = 0; i < 4; i++) v[i * stride + 3] = (alpha << 24) | 0xFFFFFF;
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade());
    }

    /** Minecraft's own uv for a face of a box (what a JSON model gets when it gives none). */
    private static float[] defaultUv(Direction d, float[] f, float[] t) {
        return switch (d) {
            case DOWN -> new float[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case UP -> new float[]{f[0], f[2], t[0], t[2]};
            case NORTH -> new float[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case SOUTH -> new float[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case WEST -> new float[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            case EAST -> new float[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    /**
     * The glass texture for these fading edges: tank_glass_&lt;mask&gt; has the fade on its u-min (1), u-max (2), v-min
     * (4) and v-max (8) edges. Which world direction each texture edge faces depends on the face (Minecraft's uv).
     */
    private static int mask(Direction d, int fades) {
        // per face: the directions its u-min, u-max, v-min, v-max edges face
        Direction[] e = switch (d) {
            case UP -> new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
            case DOWN -> new Direction[]{Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH};
            case NORTH -> new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
            case SOUTH -> new Direction[]{Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
            case WEST -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN};
            case EAST -> new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN};
        };
        int m = 0;
        for (int k = 0; k < 4; k++) if ((fades & (1 << e[k].ordinal())) != 0) m |= 1 << k;
        return m;
    }
}

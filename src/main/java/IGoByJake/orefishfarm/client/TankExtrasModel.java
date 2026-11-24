package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.block.TankBlock;
import IGoByJake.orefishfarm.block.TankBlockEntity;
import IGoByJake.orefishfarm.block.TankRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the fish tank's block models to add the pieces an aquarium needs where it turns inward, chosen by the block
 * entity's {@link TankBlockEntity#EXTRAS} model data (see Aquarium.extras): liquid seams at inside corners (they close the sliver where two panes end), and at
 * steps the glass and water above the water line plus the top rim. Keeping these out of the block state stops the
 * state count multiplying.
 */
public class TankExtrasModel extends BakedModelWrapper<BakedModel> {
    private static final String[] CORNERS = {"ne", "nw", "se", "sw"};
    private static final String[] SIDES = {"north", "east", "south", "west"};
    private static final String[] SIDE_LETTER = {"n", "e", "s", "w"};

    public TankExtrasModel(BakedModel base) {
        super(base);
    }

    private static ResourceLocation tank(String path) {
        return new ResourceLocation(OreFishFarm.MODID, "block/tank/" + path);
    }

    /** Every extra piece, for ModelEvent.RegisterAdditional. */
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String role : new String[]{"fish", "power"}) {
            for (String l : SIDE_LETTER) event.register(tank(role + "_rim_" + l));
        }
        for (String side : SIDES) {
            event.register(tank("glass_" + side + "_step"));
            for (String look : new String[]{"water", "lava", "void"}) event.register(tank("fill_" + look + "_" + side + "_step"));
        }
        for (String look : new String[]{"water", "lava", "void"})
            for (String c : CORNERS)
                for (String ud : new String[]{"ff", "ft", "tf", "tt"}) event.register(tank("fill_" + look + "_seam_" + c + "_" + ud));
    }

    /** Wraps every fish tank state's model. */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (BlockState state : Registry.FISH_TANK.get().getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            BakedModel base = models.get(key);
            if (base != null && !(base instanceof TankExtrasModel)) models.put(key, new TankExtrasModel(base));
        }
    }

    private static List<ResourceLocation> pieces(BlockState state, int bits) {
        List<ResourceLocation> out = new ArrayList<>();
        String role = state.getValue(TankBlock.ROLE) == TankRole.POWER ? "power" : "fish";
        String look = state.getValue(TankBlock.LOOK).getSerializedName();
        String ud = (state.getValue(TankBlock.UP) ? "t" : "f") + (state.getValue(TankBlock.DOWN) ? "t" : "f");
        for (int i = 0; i < 4; i++) {
            // inside corner: short strips of the liquid that close the sliver where the two neighbours' panes end
            if ((bits & (1 << i)) != 0) out.add(tank("fill_" + look + "_seam_" + CORNERS[i] + "_" + ud));
            if ((bits & (1 << (4 + i))) != 0) {
                out.add(tank("glass_" + SIDES[i] + "_step"));
                out.add(tank("fill_" + look + "_" + SIDES[i] + "_step"));
            }
            if ((bits & (1 << (8 + i))) != 0) out.add(tank(role + "_rim_" + SIDE_LETTER[i]));
        }
        return out;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        Integer bits = data.get(TankBlockEntity.EXTRAS);
        if (state == null || bits == null || bits == 0) return base;
        List<BakedQuad> out = new ArrayList<>(base);
        var manager = Minecraft.getInstance().getModelManager();
        for (ResourceLocation id : pieces(state, bits)) {
            BakedModel m = manager.getModel(id);
            if (m == manager.getMissingModel()) continue;
            if (renderType != null && !m.getRenderTypes(state, rand, ModelData.EMPTY).contains(renderType)) continue;
            out.addAll(m.getQuads(state, side, rand, ModelData.EMPTY, renderType));
        }
        return out;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        Integer bits = data.get(TankBlockEntity.EXTRAS);
        ChunkRenderTypeSet base = super.getRenderTypes(state, rand, data);
        if (bits == null || bits == 0) return base;
        // the extras can bring glass (cutout) and water (translucent) to a block whose own parts have neither
        return ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent()));
    }
}

package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.tackle.Tackle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's bobber, except when the rod's tackle is in {@code #orefishfarm:custom_bobber}: then the bobber is drawn
 * with {@code <ns>:textures/entity/bobber/<path>.png}. Vanilla does all the drawing (bobber and line); only the
 * bobber's texture is swapped.
 */
public class TackleBobberRenderer extends FishingHookRenderer {
    private static final RenderType VANILLA = RenderType.entityCutout(new ResourceLocation("textures/entity/fishing_hook.png"));

    public TackleBobberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(FishingHook hook, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        ResourceLocation tex = texture(hook);
        if (tex == null) {
            super.render(hook, yaw, partial, pose, buffers, light);
            return;
        }
        RenderType own = RenderType.entityCutout(tex);
        super.render(hook, yaw, partial, pose, type -> buffers.getBuffer(type == VANILLA ? own : type), light);
    }

    private static ResourceLocation texture(FishingHook hook) {
        Player player = hook.getPlayerOwner();
        if (player == null) return null;
        ItemStack tackle = Tackle.get(Tackle.rodOf(player));
        if (tackle.isEmpty() || !tackle.is(Tackle.CUSTOM_BOBBER)) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(tackle.getItem());
        return new ResourceLocation(id.getNamespace(), "textures/entity/bobber/" + id.getPath() + ".png");
    }
}

package IGoByJake.orefishfarm.client;

import IGoByJake.orefishfarm.OreFishFarm;
import IGoByJake.orefishfarm.entity.Seagull;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A herring-gull shape: white body and head, grey wings with black tips, yellow beak, orange legs. */
public class SeagullModel extends HierarchicalModel<Seagull> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(OreFishFarm.MODID, "seagull"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart tail;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public SeagullModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.tail = root.getChild("tail");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -4.0F, 5, 5, 9), PartPose.offset(0, 19, 0));
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 14).addBox(-2.0F, -3.0F, -3.5F, 4, 4, 4),
                PartPose.offset(0, 16.5F, -4));
        head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(16, 14).addBox(-0.5F, -1.5F, -6.5F, 1, 1, 3), PartPose.ZERO);
        r.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(28, 0).addBox(-2.0F, -0.5F, 0.0F, 4, 1, 4), PartPose.offset(0, 18, 5));
        PartDefinition rw = r.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(28, 6).addBox(-8.0F, 0.0F, -2.0F, 8, 1, 6),
                PartPose.offset(-2.5F, 17, -1));
        rw.addOrReplaceChild("right_tip", CubeListBuilder.create().texOffs(0, 22).addBox(-12.0F, 0.0F, -1.0F, 4, 1, 4), PartPose.ZERO);
        PartDefinition lw = r.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(28, 14).addBox(0.0F, 0.0F, -2.0F, 8, 1, 6),
                PartPose.offset(2.5F, 17, -1));
        lw.addOrReplaceChild("left_tip", CubeListBuilder.create().texOffs(16, 22).addBox(8.0F, 0.0F, -1.0F, 4, 1, 4), PartPose.ZERO);
        r.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(24, 14).addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1), PartPose.offset(-1, 21, 0));
        r.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(24, 14).addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1), PartPose.offset(1, 21, 0));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(Seagull gull, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        if (gull.isPerched()) {
            // Wings folded down along the body, legs down.
            this.rightWing.zRot = 1.45F;
            this.leftWing.zRot = -1.45F;
            this.rightWing.y += 0.5F;
            this.leftWing.y += 0.5F;
            this.head.xRot = Mth.sin(age * 0.05F) * 0.1F;
            this.head.yRot = Mth.sin(age * 0.03F) * 0.4F;
            return;
        }
        // Legs tucked back in flight.
        this.rightLeg.xRot = 1.3F;
        this.leftLeg.xRot = 1.3F;
        float flap;
        if (gull.isDiving()) {
            flap = -0.9F; // wings swept up and back for the dive
        } else {
            // Mostly gliding with the odd burst of flapping.
            float burst = Mth.clamp(Mth.sin(age * 0.04F) * 3F - 1.5F, 0F, 1F);
            flap = Mth.sin(age * 0.6F) * 0.6F * burst + Mth.sin(age * 0.15F) * 0.08F;
        }
        this.rightWing.zRot = flap;
        this.leftWing.zRot = -flap;
        this.tail.xRot = Mth.sin(age * 0.1F) * 0.05F;
    }
}

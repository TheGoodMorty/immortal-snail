package com.yourmodid.client.model; // TODO: change to your package

import com.mojang.blaze3d.vertex.PoseStack; // only needed if you add custom transforms

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Snail entity model for Minecraft 1.21.11 (render-state pipeline).
 *
 * IMPORTANT: this mesh is authored at 2x scale (a 4x4x4-pixel envelope) so
 * that every cube sits on whole-pixel boundaries and the texture UVs are
 * clean. The renderer scales it by 0.5F, giving the requested 2x2x2-pixel
 * snail in the world. Match it with a hitbox of .sized(0.125F, 0.125F).
 *
 * Geometry (model space, ground at y=24, before the 0.5x render scale):
 *
 *   body  4 x 2 x 4   the foot/slab, sits on the ground
 *   shell 3 x 3 x 3   rear-biased, embedded 1px into the body
 *   head  2 x 1 x 1   front-top of the body
 *   eyestalks         two 1x2x1 cubes squeezed to 0.5px wide via
 *                     CubeDeformation; they poke ~1px above the shell
 *                     (0.5 real pixels), which reads very "snail".
 *                     Shorten the box to 1x1x1 if you want a strict
 *                     2px ceiling.
 *
 * Texture: 32x32. UV regions used:
 *   body      texOffs( 0, 0)  -> u 0..16, v 0..6
 *   shell     texOffs( 0, 8)  -> u 0..12, v 8..14
 *   head      texOffs(16, 0)  -> u 16..22, v 0..2
 *   stalk L   texOffs(16, 4)  -> u 16..20, v 4..7
 *   stalk R   texOffs(24, 4)  -> u 24..28, v 4..7
 *
 * This uses plain LivingEntityRenderState. If you later want custom data
 * (an AnimationState, a "retracted into shell" flag), make a
 * SnailRenderState extends LivingEntityRenderState, swap the generic here
 * and in SnailRenderer, and fill it in SnailRenderer#extractRenderState.
 */
public class SnailModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("yourmodid", "snail"), "main");

    private static final float DEG_TO_RAD = (float) Math.PI / 180F;

    private final ModelPart body;
    private final ModelPart shell;
    private final ModelPart head;
    private final ModelPart leftEyestalk;
    private final ModelPart rightEyestalk;

    public SnailModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.shell = this.body.getChild("shell");
        this.head = this.body.getChild("head");
        this.leftEyestalk = this.head.getChild("left_eyestalk");
        this.rightEyestalk = this.head.getChild("right_eyestalk");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The foot: 4 wide, 2 tall, 4 long, bottom flush with the ground (y=24).
        PartDefinition bodyPart = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // Shell: 3x3x3, pivot on the body's back (rear-biased), embedded
        // 1px into the body so it looks attached.
        bodyPart.addOrReplaceChild("shell",
                CubeListBuilder.create().texOffs(0, 8)
                        .addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, -1.0F, 0.5F));

        // Head: small block on the front-top edge of the foot.
        PartDefinition headPart = bodyPart.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(16, 0)
                        .addBox(-1.0F, -1.0F, -0.5F, 2.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, -2.0F, -1.5F));

        // Eyestalks: nominal 1x2x1 cubes, deflated to 0.5 wide/deep.
        // The Y deformation is 0 so the stalk stays full height and its
        // base stays flush with the head.
        CubeDeformation stalkSqueeze = new CubeDeformation(-0.25F, 0.0F, -0.25F);

        headPart.addOrReplaceChild("left_eyestalk",
                CubeListBuilder.create().texOffs(16, 4)
                        .addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, stalkSqueeze),
                PartPose.offset(0.5F, -1.0F, 0.0F));

        headPart.addOrReplaceChild("right_eyestalk",
                CubeListBuilder.create().texOffs(24, 4)
                        .addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, stalkSqueeze),
                PartPose.offset(-0.5F, -1.0F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // Resets every part to its PartPose default before we touch anything.
        super.setupAnim(state);

        // Snails don't whip their heads around - track at half strength.
        // On LivingEntityRenderState, yRot is head-yaw-relative-to-body and
        // xRot is head pitch, both in degrees.
        this.head.yRot = state.yRot * DEG_TO_RAD * 0.5F;
        this.head.xRot = state.xRot * DEG_TO_RAD * 0.5F;

        // Idle eyestalk wiggle, slightly out of phase so it feels organic.
        float wiggle = Mth.sin(state.ageInTicks * 0.12F) * 0.15F;
        this.leftEyestalk.zRot  =  0.20F + wiggle;
        this.rightEyestalk.zRot = -0.20F - wiggle;
        this.leftEyestalk.xRot  = Mth.cos(state.ageInTicks * 0.09F) * 0.10F;
        this.rightEyestalk.xRot = Mth.cos(state.ageInTicks * 0.09F + 1.3F) * 0.10F;

        // Crawl: a gentle shell sway + head bob driven by movement.
        float crawl = Mth.sin(state.walkAnimationPos * 0.6F) * state.walkAnimationSpeed;
        this.shell.zRot = crawl * 0.10F;
        this.head.y += crawl * 0.15F; // relative to the pose reset above
    }
}

package com.yourname.immortalsnail.client;

import com.yourname.immortalsnail.ImmortalSnail;
import com.yourname.immortalsnail.entity.SnailEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * Snail entity model for Minecraft 1.21.11 (render-state pipeline).
 *
 * Geometry (model space, body bottom at y=22 before the 0.5x render scale):
 *
 *   body  4 x 2 x 4   the foot/slab, bottom flush with the feet
 *   shell 3 x 3 x 3   rear-biased, embedded 1px into the body
 *   head  2 x 1 x 1   front-top of the body
 *   eyestalks         two 1x2x1 cubes squeezed via Dilation
 *
 * Texture: 32x32 snail.png. UV regions used:
 *   body      texOffs( 0, 0)  -> u 0..16, v 0..6
 *   shell     texOffs( 0, 8)  -> u 0..12, v 8..14
 *   head      texOffs(16, 0)  -> u 16..22, v 0..2
 *   stalk L   texOffs(16, 4)  -> u 16..20, v 4..7
 *   stalk R   texOffs(24, 4)  -> u 24..28, v 4..7
 */
public class SnailModel extends EntityModel<SnailRenderState> {

    public static final EntityModelLayer LAYER_LOCATION =
            new EntityModelLayer(Identifier.of(ImmortalSnail.MOD_ID, "snail"), "main");

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

    public static TexturedModelData createBodyLayer() {
        ModelData mesh = new ModelData();

        // The foot: 4 wide, 2 tall, 4 long. After the 0.5x render scale this
        // is a 2x1x2-pixel slab sitting on the entity's feet. The body's
        // bottom (model-space y=22) is the "ground" face that touches whatever
        // surface the snail is climbing or walking on.
        //
        // Anchor: pivot at y=24, cuboid extends y=-2..0 so absolute y range is
        // [22, 24]. This is Claude's original calibration for the 0.5x render
        // scale — the underside lands flush on the block.
        mesh.getRoot().addChild("body",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F),
                ModelTransform.origin(0.0F, 24.0F, 0.0F));

        // Shell: 3x3x3, pivot on the body's back (rear-biased), embedded
        // 1px into the body so it looks attached. Rear = +Z (the renderer
        // rotates the model by 180°-bodyYaw, so the model's -Z is forward).
        mesh.getRoot().getChild("body").addChild("shell",
                ModelPartBuilder.create().uv(0, 8)
                        .cuboid(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F),
                ModelTransform.origin(0.0F, -1.0F, 0.5F));

        // Head: small block on the front-top edge of the foot. The renderer
        // rotates the model by 180°-bodyYaw, so the model's -Z is the front
        // and the head leads when the snail moves.
        mesh.getRoot().getChild("body").addChild("head",
                ModelPartBuilder.create().uv(16, 0)
                        .cuboid(-1.0F, -1.0F, -0.5F, 2.0F, 1.0F, 1.0F),
                ModelTransform.origin(0.0F, -2.0F, -1.5F));

        // Eyestalks: nominal 1x2x1 cubes, deflated to 0.5 wide/deep.
        // The Y dilation is 0 so the stalk stays full height and its base
        // stays flush with the head.
        Dilation stalkSqueeze = new Dilation(-0.25F, 0.0F, -0.25F);

        mesh.getRoot().getChild("body").getChild("head").addChild("left_eyestalk",
                ModelPartBuilder.create().uv(16, 4)
                        .cuboid(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, stalkSqueeze),
                ModelTransform.origin(0.5F, -1.0F, 0.0F));

        mesh.getRoot().getChild("body").getChild("head").addChild("right_eyestalk",
                ModelPartBuilder.create().uv(24, 4)
                        .cuboid(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, stalkSqueeze),
                ModelTransform.origin(-0.5F, -1.0F, 0.0F));

        return TexturedModelData.of(mesh, 32, 32);
    }

    @Override
    public void setAngles(SnailRenderState state) {
        // Resets every part to its default transform before we touch anything.
        this.body.resetTransform();
        this.shell.resetTransform();
        this.head.resetTransform();
        this.leftEyestalk.resetTransform();
        this.rightEyestalk.resetTransform();

        // Strike the pose for the surface the snail is on. The server picks
        // the pose every tick (SnailEntity.POSE_*) so the head points along
        // the snail's direction of travel, and each pose both rotates the
        // contact face into the surface and shifts the pivot so the contact
        // face lands on the surface plane instead of floating at the hitbox
        // centre-line. Render scale is 1.0, so 1 model unit = 1 pixel.
        // Shift amounts: half hitbox width = 0.125 blocks = 2 model units
        // (wall faces); full hitbox height = 0.25 blocks = 4 model units
        // (ceiling); the 2-unit lift keeps the body in the same band above
        // the origin that it occupies on the floor.
        float halfPi = DEG_TO_RAD * 90F;
        switch (state.surfacePose) {
            case SnailEntity.POSE_WALL_UP -> {
                // Head up the wall. The entity yaws INTO the wall, so the
                // toward-wall axis is model -Z.
                this.body.pitch = -halfPi;
                this.body.originZ -= 2.0F;
                this.body.originY -= 2.0F;
            }
            case SnailEntity.POSE_WALL_DOWN -> {
                // Head down the wall. The entity yaws AWAY from the wall,
                // so the toward-wall axis is model +Z.
                this.body.pitch = halfPi;
                this.body.originZ += 2.0F;
                this.body.originY -= 2.0F;
            }
            case SnailEntity.POSE_WALL_SIDE_LEFT -> {
                // Sideways along the wall with the wall on the snail's
                // left. The renderer's scale(-1,-1,1) mirrors model X, so
                // the entity's LEFT side is model +X: roll -90° swings the
                // foot onto raw +X and the pivot shift pushes it out to
                // the wall plane. Head stays facing forward.
                this.body.roll = -halfPi;
                this.body.originX += 2.0F;
                this.body.originY -= 2.0F;
            }
            case SnailEntity.POSE_WALL_SIDE_RIGHT -> {
                // Sideways along the wall with the wall on the snail's
                // right: mirrored pose (foot onto model -X).
                this.body.roll = halfPi;
                this.body.originX -= 2.0F;
                this.body.originY -= 2.0F;
            }
            case SnailEntity.POSE_CEILING -> {
                // Upside down; lift the contact face up to the hitbox top.
                this.body.pitch = (float) Math.PI;
                this.body.originY -= 4.0F;
            }
            default -> {
                // Floor (and free fall): flat, no shift.
            }
        }

        // The head is a child of the body, so it inherits the body rotation.
        // No independent head pitch/yaw — the snail doesn't look around.

        // Idle eyestalk wiggle, slightly out of phase so it feels organic.
        float wiggle = (float) Math.sin(state.age * 0.12F) * 0.15F;
        this.leftEyestalk.roll  =  0.20F + wiggle;
        this.rightEyestalk.roll = -0.20F - wiggle;
        this.leftEyestalk.pitch  = (float) Math.cos(state.age * 0.09F) * 0.10F;
        this.rightEyestalk.pitch = (float) Math.cos(state.age * 0.09F + 1.3F) * 0.10F;

        // Crawl: a gentle shell sway + head bob driven by movement.
        float crawl = (float) Math.sin(state.limbSwingAnimationProgress * 0.6F) * state.limbSwingAmplitude;
        this.shell.roll = crawl * 0.10F;
        this.head.originY += crawl * 0.15F; // relative to the pose reset above
    }
}

package com.yourname.immortalsnail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.immortalsnail.ImmortalSnail;
import com.yourname.immortalsnail.entity.SnailEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Snail renderer for 26.2.
 *
 * The model is authored at 2x so its cubes sit on whole pixels; the
 * renderer's scale() brings it down to the intended ~2x1x2-pixel
 * footprint. The entity's hitbox is much larger than the model (0.6 x 0.5)
 * so the visual sits centered on the hitbox regardless of which surface
 * the snail is climbing or walking on.
 */
public class SnailRenderer extends MobRenderer<SnailEntity, SnailRenderState, SnailModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ImmortalSnail.MOD_ID, "textures/entity/snail.png");

    public SnailRenderer(EntityRendererProvider.Context context) {
        super(context, new SnailModel(context.bakeLayer(SnailModel.LAYER_LOCATION)), 0.1F);
    }

    @Override
    public SnailRenderState createRenderState() {
        return new SnailRenderState();
    }

    @Override
    public void extractRenderState(SnailEntity entity, SnailRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.surfacePose = entity.getSurfacePose();
    }

    /**
     * The model is authored at 2x so its cubes sit on whole pixels;
     * rendered at 1.0 it is a 4x2x4-pixel footprint, matching the 0.25
     * block (4px) hitbox exactly so the model edge tracks the hitbox edge
     * while cresting.
     */
    @Override
    protected void scale(SnailRenderState renderState, PoseStack poseStack) {
        poseStack.scale(1.0F, 1.0F, 1.0F);
    }

    @Override
    public Identifier getTextureLocation(SnailRenderState state) {
        return TEXTURE;
    }
}
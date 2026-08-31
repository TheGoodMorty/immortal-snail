package com.yourmodid.client.renderer; // TODO: change to your package

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourmodid.client.model.SnailModel;  // TODO: your package
import com.yourmodid.entity.SnailEntity;       // TODO: your entity class (must extend Mob)

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Snail renderer for 1.21.11.
 *
 * Nothing snail-specific needs extracting, so this uses the vanilla
 * LivingEntityRenderState directly. If you add a custom state later,
 * change the generic here and in SnailModel, return your class from
 * createRenderState(), and override extractRenderState() to fill it.
 */
public class SnailRenderer extends MobRenderer<SnailEntity, LivingEntityRenderState, SnailModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("yourmodid", "textures/entity/snail.png");

    public SnailRenderer(EntityRendererProvider.Context context) {
        super(context, new SnailModel(context.bakeLayer(SnailModel.LAYER_LOCATION)), 0.1F);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    /**
     * The model is authored at 2x so its cubes sit on whole pixels;
     * this brings it down to the intended 2x2x2-pixel footprint.
     */
    @Override
    protected void scale(LivingEntityRenderState renderState, PoseStack poseStack) {
        poseStack.scale(0.5F, 0.5F, 0.5F);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}

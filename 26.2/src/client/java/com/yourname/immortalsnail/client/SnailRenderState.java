package com.yourname.immortalsnail.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the snail. Carries the server-decided surface pose
 * (see {@link com.yourname.immortalsnail.entity.SnailEntity} POSE_* constants)
 * so the model can strike the matching floor / wall / ceiling orientation
 * with the head pointing along the snail's direction of travel.
 */
public class SnailRenderState extends LivingEntityRenderState {

    public int surfacePose;
}

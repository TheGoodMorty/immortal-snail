package com.yourname.immortalsnail.client;

import com.yourname.immortalsnail.BargainSubmitPayload;
import com.yourname.immortalsnail.entity.ModEntities;
import com.yourname.immortalsnail.player.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public class ImmortalSnailClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register the entity type on the client side too. Fabric's registry
        // sync requires the entry to exist locally so server-spawned entities
        // can be resolved to a local EntityType.
        ModEntities.register();
        ModMenus.register();

        // Register the model layer
        ModelLayerRegistry.registerModelLayer(SnailModel.LAYER_LOCATION, SnailModel::createBodyLayer);

        // Register the renderer
        EntityRendererRegistry.register(ModEntities.SNAIL, SnailRenderer::new);

        // Register payload types on the client side so we can send/receive them.
        // C2S: BargainSubmitPayload (client sends to server when player confirms)
        PayloadTypeRegistry.serverboundPlay().register(BargainSubmitPayload.TYPE, BargainSubmitPayload.CODEC);

        // Register the screen factory so when the server opens a BargainScreenHandler,
        // Minecraft automatically constructs the matching BargainScreen pair.
        MenuScreens.register(ModMenus.BARGAIN, BargainScreen::new);

        // Note: BargainOpenResponsePayload is no longer needed; the server
        // opens the screen via player.openMenu(...) which sends the
        // standard ClientboundOpenScreenPacket that MenuScreens handles.
    }
}
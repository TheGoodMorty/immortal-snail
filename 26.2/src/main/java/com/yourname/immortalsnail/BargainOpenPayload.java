package com.yourname.immortalsnail;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: client requests opening the bargain GUI.
 * Currently unused (server opens on first-join) but kept for symmetry.
 */
public record BargainOpenPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BargainOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ImmortalSnail.MOD_ID, "bargain_request_open"));

    public static final StreamCodec<FriendlyByteBuf, BargainOpenPayload> CODEC =
            StreamCodec.unit(new BargainOpenPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
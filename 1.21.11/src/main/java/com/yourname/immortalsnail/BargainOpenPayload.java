package com.yourname.immortalsnail;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: client requests opening the bargain GUI.
 * Currently unused (server opens on first-join) but kept for symmetry.
 */
public record BargainOpenPayload() implements CustomPayload {

    public static final CustomPayload.Id<BargainOpenPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ImmortalSnail.MOD_ID, "bargain_request_open"));

    public static final CustomPayload.Type<PacketByteBuf, BargainOpenPayload> TYPE =
            new CustomPayload.Type<>(ID, PacketCodec.unit(new BargainOpenPayload()));

    public static final PacketCodec<PacketByteBuf, BargainOpenPayload> CODEC = TYPE.codec();

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

package com.yourname.immortalsnail;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.ArrayList;

/**
 * S2C: tells the client to open the bargain GUI.
 * Carries the blacklist (list of resource locations to exclude from the picker).
 */
public record BargainOpenResponsePayload(List<String> blacklist) implements CustomPayload {

    public static final CustomPayload.Id<BargainOpenResponsePayload> ID =
            new CustomPayload.Id<>(Identifier.of(ImmortalSnail.MOD_ID, "bargain_open"));

    private static final PacketCodec<RegistryByteBuf, List<String>> LIST_CODEC = new PacketCodec<>() {
        @Override
        public void encode(RegistryByteBuf buf, List<String> list) {
            buf.writeVarInt(list.size());
            for (String s : list) buf.writeString(s);
        }
        @Override
        public List<String> decode(RegistryByteBuf buf) {
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(buf.readString());
            return list;
        }
    };

    public static final PacketCodec<RegistryByteBuf, BargainOpenResponsePayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> LIST_CODEC.encode(buf, payload.blacklist()),
                    buf -> new BargainOpenResponsePayload(LIST_CODEC.decode(buf)));

    public static final CustomPayload.Type<RegistryByteBuf, BargainOpenResponsePayload> TYPE =
            new CustomPayload.Type<>(ID, CODEC);

    public static void send(ServerPlayerEntity player) {
        List<String> blacklist = List.of(
                "minecraft:command_block",
                "minecraft:chain_command_block",
                "minecraft:repeating_command_block",
                "minecraft:command_block_minecart",
                "minecraft:structure_block",
                "minecraft:structure_void",
                "minecraft:jigsaw",
                "minecraft:barrier",
                "minecraft:light",
                "minecraft:spawner"
        );
        ServerPlayNetworking.send(player, new BargainOpenResponsePayload(blacklist));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

package com.yourname.immortalsnail;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.ArrayList;

/**
 * S2C: tells the client to open the bargain GUI.
 * Carries the blacklist (list of resource locations to exclude from the picker).
 */
public record BargainOpenResponsePayload(List<String> blacklist) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BargainOpenResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ImmortalSnail.MOD_ID, "bargain_open"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> LIST_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, List<String> list) {
            buf.writeVarInt(list.size());
            for (String s : list) buf.writeUtf(s);
        }
        @Override
        public List<String> decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(buf.readUtf());
            return list;
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, BargainOpenResponsePayload> CODEC =
            StreamCodec.composite(LIST_CODEC, BargainOpenResponsePayload::blacklist, BargainOpenResponsePayload::new);

    public static void send(ServerPlayer player) {
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
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
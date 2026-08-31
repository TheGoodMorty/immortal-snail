package com.yourname.immortalsnail;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: client submits the contents of the two 27-slot shulkers for the bargain.
 * Carries 54 ItemStack entries.
 */
public record BargainSubmitPayload(List<ItemStack> contents) implements CustomPayload {

    public static final CustomPayload.Id<BargainSubmitPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ImmortalSnail.MOD_ID, "bargain_submit"));

    private static final PacketCodec<RegistryByteBuf, ItemStack> ITEM_CODEC = new PacketCodec<>() {
        @Override
        public void encode(RegistryByteBuf buf, ItemStack s) {
            buf.writeBoolean(!s.isEmpty());
            if (!s.isEmpty()) ItemStack.PACKET_CODEC.encode(buf, s);
        }
        @Override
        public ItemStack decode(RegistryByteBuf buf) {
            if (buf.readBoolean()) return ItemStack.PACKET_CODEC.decode(buf);
            return ItemStack.EMPTY;
        }
    };

    private static final PacketCodec<RegistryByteBuf, List<ItemStack>> LIST_CODEC = new PacketCodec<>() {
        @Override
        public void encode(RegistryByteBuf buf, List<ItemStack> list) {
            buf.writeVarInt(list.size());
            for (ItemStack s : list) ITEM_CODEC.encode(buf, s);
        }
        @Override
        public List<ItemStack> decode(RegistryByteBuf buf) {
            int n = buf.readVarInt();
            List<ItemStack> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(ITEM_CODEC.decode(buf));
            return list;
        }
    };

    public static final PacketCodec<RegistryByteBuf, BargainSubmitPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> LIST_CODEC.encode(buf, payload.contents()),
                    buf -> new BargainSubmitPayload(LIST_CODEC.decode(buf)));

    public static final CustomPayload.Type<RegistryByteBuf, BargainSubmitPayload> TYPE =
            new CustomPayload.Type<>(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

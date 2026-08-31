package com.yourname.immortalsnail;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: client submits the contents of the two 27-slot shulkers for the bargain.
 * Carries 54 ItemStack entries.
 */
public record BargainSubmitPayload(List<ItemStack> contents) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BargainSubmitPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ImmortalSnail.MOD_ID, "bargain_submit"));

    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, ItemStack s) {
            buf.writeBoolean(!s.isEmpty());
            if (!s.isEmpty()) ItemStack.STREAM_CODEC.encode(buf, s);
        }
        @Override
        public ItemStack decode(RegistryFriendlyByteBuf buf) {
            if (buf.readBoolean()) return ItemStack.STREAM_CODEC.decode(buf);
            return ItemStack.EMPTY;
        }
    };

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> LIST_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, List<ItemStack> list) {
            buf.writeVarInt(list.size());
            for (ItemStack s : list) ITEM_CODEC.encode(buf, s);
        }
        @Override
        public List<ItemStack> decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<ItemStack> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(ITEM_CODEC.decode(buf));
            return list;
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, BargainSubmitPayload> CODEC =
            StreamCodec.composite(LIST_CODEC, BargainSubmitPayload::contents, BargainSubmitPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
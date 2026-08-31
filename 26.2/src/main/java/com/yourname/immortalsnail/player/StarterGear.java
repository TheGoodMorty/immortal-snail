package com.yourname.immortalsnail.player;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Builds the starter shulker and other starter gear for first-join players.
 *
 * The starter is a shulker box filled with max-enchanted Netherite gear,
 * food, and a wide variety of basic and utility materials (blocks, ammo,
 * redstone, blaze rods, etc.). See DESIGN.md §4.2 for the full spec.
 */
public final class StarterGear {

    private StarterGear() {}

    public static void giveStarter(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();

        // Build exactly 27 items that all fit inside the starter shulker box.
        var contents = new ItemStack[]{
                // Gear (12 items) — slots 0..11
                maxNetheriteHelmet(server),
                maxNetheriteChestplate(server),
                maxNetheriteLeggings(server),
                maxNetheriteBoots(server),
                maxNetheriteSword(server),
                maxNetheritePickaxe(server),
                maxNetheriteAxe(server),
                maxNetheriteShovel(server),
                maxBow(server),
                maxCrossbow(server),
                maxTrident(server),
                maxMace(server),
                // Elytra + consumables (5 items) — slots 12..16
                maxElytra(server),                          // slot 12
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 4),
                new ItemStack(Items.ENDER_PEARL, 16),
                new ItemStack(Items.GOLDEN_CARROT, 64),
                // Basic materials (10 items) — slots 17..26 (fills the shulker)
                new ItemStack(Items.STONE, 64),             // 17
                new ItemStack(Items.OAK_LOG, 32),
                new ItemStack(Items.TORCH, 64),
                new ItemStack(Items.GOLD_BLOCK, 64),
                new ItemStack(Items.DIAMOND_BLOCK, 64),
                new ItemStack(Items.EMERALD_BLOCK, 64),
                new ItemStack(Items.IRON_BLOCK, 64),
                new ItemStack(Items.OBSIDIAN, 64),
                new ItemStack(Items.COOKED_BEEF, 64),
                new ItemStack(Items.REDSTONE_BLOCK, 64),    // 26
        };

        // Shulker holds all 27 items.
        ItemStack shulker = makeShulkerBox(contents);

        // Give the shulker; drop if inventory full.
        giveOrDrop(player, shulker);

        // Dedicated rockets-only shulker: 2 stacks of firework rockets.
        // Kept separate so it doesn't compete with starter gear slots.
        ItemStack rocketsShulker = makeRocketsShulker();
        giveOrDrop(player, rocketsShulker);
    }

    /**
     * Build a shulker box pre-filled with firework rockets. Used for
     * elytra-boosting during long-distance travel.
     */
    private static ItemStack makeRocketsShulker() {
        // Rocket stack max is 64. Two stacks = 128 rockets total.
        var rockets = new ItemStack[]{
                new ItemStack(Items.FIREWORK_ROCKET, 64),
                new ItemStack(Items.FIREWORK_ROCKET, 64),
        };
        return makeShulkerBox(rockets);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Build a shulker box ItemStack whose contents are stored under the
     * minecraft:container data component (the 1.21+ format).
     */
    private static ItemStack makeShulkerBox(ItemStack[] contents) {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        java.util.List<ItemStack> containerItems = new java.util.ArrayList<>(27);
        for (int i = 0; i < 27; i++) containerItems.add(ItemStack.EMPTY);
        for (int i = 0; i < contents.length && i < 27; i++) {
            ItemStack s = contents[i];
            if (s != null && !s.isEmpty()) containerItems.set(i, s.copy());
        }
        shulker.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(containerItems));
        return shulker;
    }

    // ---- Gear builders ----

    private static ItemStack maxNetheriteHelmet(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_HELMET);
        addEnchant(server, s, Enchantments.PROTECTION, 4);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.RESPIRATION, 3);
        addEnchant(server, s, Enchantments.AQUA_AFFINITY, 1);
        addEnchant(server, s, Enchantments.THORNS, 3);
        return s;
    }

    private static ItemStack maxNetheriteChestplate(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_CHESTPLATE);
        addEnchant(server, s, Enchantments.PROTECTION, 4);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.THORNS, 3);
        return s;
    }

    private static ItemStack maxNetheriteLeggings(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_LEGGINGS);
        addEnchant(server, s, Enchantments.PROTECTION, 4);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.SWIFT_SNEAK, 3);
        addEnchant(server, s, Enchantments.THORNS, 3);
        return s;
    }

    private static ItemStack maxNetheriteBoots(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_BOOTS);
        addEnchant(server, s, Enchantments.PROTECTION, 4);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.FEATHER_FALLING, 4);
        addEnchant(server, s, Enchantments.DEPTH_STRIDER, 3);
        addEnchant(server, s, Enchantments.SOUL_SPEED, 3);
        addEnchant(server, s, Enchantments.THORNS, 3);
        return s;
    }

    private static ItemStack maxNetheriteSword(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        addEnchant(server, s, Enchantments.SHARPNESS, 5);
        addEnchant(server, s, Enchantments.SWEEPING_EDGE, 3);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.LOOTING, 3);
        addEnchant(server, s, Enchantments.FIRE_ASPECT, 2);
        return s;
    }

    private static ItemStack maxNetheritePickaxe(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
        addEnchant(server, s, Enchantments.EFFICIENCY, 5);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.FORTUNE, 3);
        return s;
    }

    private static ItemStack maxNetheriteAxe(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_AXE);
        addEnchant(server, s, Enchantments.SHARPNESS, 5);
        addEnchant(server, s, Enchantments.EFFICIENCY, 5);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        return s;
    }

    private static ItemStack maxNetheriteShovel(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.NETHERITE_SHOVEL);
        addEnchant(server, s, Enchantments.EFFICIENCY, 5);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.SILK_TOUCH, 1);
        return s;
    }

    private static ItemStack maxBow(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.BOW);
        addEnchant(server, s, Enchantments.POWER, 5);
        addEnchant(server, s, Enchantments.INFINITY, 1);
        addEnchant(server, s, Enchantments.FLAME, 1);
        addEnchant(server, s, Enchantments.PUNCH, 2);
        return s;
    }

    private static ItemStack maxCrossbow(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.CROSSBOW);
        addEnchant(server, s, Enchantments.QUICK_CHARGE, 3);
        addEnchant(server, s, Enchantments.MULTISHOT, 1);
        addEnchant(server, s, Enchantments.MENDING, 1);
        return s;
    }

    private static ItemStack maxTrident(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.TRIDENT);
        addEnchant(server, s, Enchantments.LOYALTY, 3);
        addEnchant(server, s, Enchantments.CHANNELING, 1);
        addEnchant(server, s, Enchantments.IMPALING, 5);
        addEnchant(server, s, Enchantments.MENDING, 1);
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        return s;
    }

    private static ItemStack maxMace(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.MACE);
        Identifier densityId = Identifier.fromNamespaceAndPath("minecraft", "density");
        Identifier breachId = Identifier.fromNamespaceAndPath("minecraft", "breach");
        Identifier windBurstId = Identifier.fromNamespaceAndPath("minecraft", "wind_burst");
        var registryAccess = server.registryAccess();
        var enchReg = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        enchReg.get(densityId).ifPresent(h -> addEnchantHolder(s, h, 5));
        enchReg.get(breachId).ifPresent(h -> addEnchantHolder(s, h, 4));
        enchReg.get(windBurstId).ifPresent(h -> addEnchantHolder(s, h, 3));
        return s;
    }

    private static ItemStack maxElytra(MinecraftServer server) {
        ItemStack s = new ItemStack(Items.ELYTRA);
        // Unbreaking + Mending are the only applicable elytra enchantments.
        addEnchant(server, s, Enchantments.UNBREAKING, 3);
        addEnchant(server, s, Enchantments.MENDING, 1);
        return s;
    }

    private static void addEnchant(MinecraftServer server, ItemStack stack, ResourceKey<Enchantment> key, int level) {
        var registryAccess = server.registryAccess();
        var enchReg = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        var entryOpt = enchReg.get(key.identifier());
        entryOpt.ifPresent(holder -> addEnchantHolder(stack, holder, level));
    }

    private static void addEnchantHolder(ItemStack stack, Holder<Enchantment> holder, int level) {
        EnchantmentHelper.updateEnchantments(stack, builder -> builder.set(holder, level));
    }
}
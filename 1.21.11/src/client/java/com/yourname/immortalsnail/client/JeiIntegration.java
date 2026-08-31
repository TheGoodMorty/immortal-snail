package com.yourname.immortalsnail.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * Optional JEI (Just Enough Items) integration.
 *
 * JEI is detected at runtime via FabricLoader.isModLoaded("jei").
 * We don't compile against JEI because its 1.21.11 build isn't on Maven Central
 * or Fabric Maven. Users install JEI from Modrinth/CurseForge.
 *
 * The integration is intentionally minimal: JEI's item list overlay is visible
 * on the side of any screen automatically once JEI is loaded. Our mod just
 * detects whether JEI is present so the bargain screen can show a helpful
 * message and label.
 */
public final class JeiIntegration {

    private static final boolean JEI_LOADED =
            FabricLoader.getInstance().isModLoaded("jei");

    static {
        if (JEI_LOADED) {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.info("JEI detected — item list overlay will be available.");
        } else {
            com.yourname.immortalsnail.ImmortalSnail.LOGGER.info("JEI not detected. Install JEI from modrinth.com/mod/jei to enable item list overlay.");
        }
    }

    private JeiIntegration() {}

    /**
     * Returns true if JEI is installed.
     */
    public static boolean isAvailable() {
        return JEI_LOADED;
    }

    /**
     * Called when the player presses the "Pick items..." button.
     * JEI's overlay is always visible on screen, so we just inform the player.
     */
    public static void openItemPicker(MinecraftClient client) {
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal(
                    "JEI is loaded. Use the JEI item list overlay (right side of screen) to find items, then drag/click to place them in the bargain shulkers."), false);
        }
    }
}

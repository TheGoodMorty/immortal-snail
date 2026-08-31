package com.yourname.immortalsnail.player;

import com.yourname.immortalsnail.ImmortalSnail;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * Registers all mod MenuTypes.
 *
 * Must be called from both the server (logical server) initializer AND the
 * client initializer so the entries exist on both sides for Fabric's
 * registry synchronization.
 */
public final class ModMenus {

    public static final Identifier BARGAIN_ID = Identifier.fromNamespaceAndPath(ImmortalSnail.MOD_ID, "bargain");

    /**
     * Custom MenuType for the bargain screen.
     *
     * The vanilla constructor takes a FeatureFlagSet. We use the default
     * (empty) flag set.
     */
    public static final MenuType<BargainScreenHandler> BARGAIN =
            new MenuType<>(
                    BargainScreenHandler::new,
                    FeatureFlags.DEFAULT_FLAGS);

    private ModMenus() {}

    public static void register() {
        // Register our custom MenuType so both server and client have
        // a registered entry in BuiltInRegistries.MENU. The client uses
        // this for MenuScreens.register(); the server uses it for opening.
        Registry.register(BuiltInRegistries.MENU, BARGAIN_ID, BARGAIN);
    }
}
package com.yourname.immortalsnail.player;

import com.yourname.immortalsnail.ImmortalSnail;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * Registers all mod ScreenHandlerTypes.
 *
 * Must be called from both the server (logical server) initializer AND the
 * client initializer so the entries exist on both sides for Fabric's
 * registry synchronization.
 */
public final class ModMenus {

    public static final Identifier BARGAIN_ID = Identifier.of(ImmortalSnail.MOD_ID, "bargain");

    /**
     * The type used for the bargain screen.
     *
     * We can't easily construct a vanilla ScreenHandlerType directly because
     * Yarn 1.21.11 changed its constructor signature to require a
     * {@code FeatureSet} as the second argument. Instead we simply REUSE the
     * vanilla {@link ScreenHandlerType#GENERIC_9X6} type. This is acceptable
     * because:
     *   - Our menu has 90 slots (54 shulker + 36 player inv), but Minecraft's
     *     click routing is determined by the slot instances we add, not by
     *     the declared container size.
     *   - We don't need server-to-client extra data, so a regular
     *     NamedScreenHandlerFactory is sufficient.
     */
    /**
     * Custom ScreenHandlerType for the bargain screen.
     *
     * The vanilla constructor takes a FeatureSet. We use a static helper.
     */
    public static final ScreenHandlerType<BargainScreenHandler> BARGAIN =
            new ScreenHandlerType<>(
                    BargainScreenHandler::new,
                    net.minecraft.resource.featuretoggle.FeatureSet.empty());

    private ModMenus() {}

    public static void register() {
        // Register our custom ScreenHandlerType so both server and client have
        // a registered entry in Registries.SCREEN_HANDLER. The client uses
        // this for HandledScreens.bind(); the server uses it for opening.
        Registry.register(Registries.SCREEN_HANDLER, BARGAIN_ID, BARGAIN);
    }
}

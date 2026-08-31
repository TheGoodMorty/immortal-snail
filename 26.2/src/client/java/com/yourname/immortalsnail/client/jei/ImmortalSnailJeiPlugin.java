package com.yourname.immortalsnail.client.jei;

import com.yourname.immortalsnail.client.BargainScreen;
import com.yourname.immortalsnail.player.BargainScreenHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI (Just Enough Items) plugin for the Immortal Snail mod.
 *
 * Registers the BargainScreen with JEI so:
 *   1. The item list sidebar appears on the screen
 *   2. Players can drag items from JEI into the shulker slots (ghost ingredients)
 *
 * The plugin is discovered via {@code META-INF/services/mezz.jei.api.IModPlugin}
 * and is only loaded when JEI is installed.
 *
 * NOTE (26.2 port): the {@code mezz.jei.api.*} references target the 26.2 JEI
 * API. Re-extract the JEI API jar for 26.2 (jei-26.2-fabric-30.24.0.176.jar)
 * into {@code libs/jei-api-intermediate.jar} before compiling; the JEI API
 * surface may have shifted between 1.21.11 and 26.2.
 */
@JeiPlugin
public class ImmortalSnailJeiPlugin implements IModPlugin {

    private static final Identifier PLUGIN_UID =
            Identifier.fromNamespaceAndPath("immortalsnail", "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(BargainScreen.class, ImmortalSnailJeiPlugin::getGuiProperties);
        // Register ghost ingredient handler so JEI can place items into the shulker slots
        registration.addGhostIngredientHandler(BargainScreen.class, new BargainGhostIngredientHandler());
    }

    /**
     * Returns the GUI properties (bounds) for the BargainScreen.
     * JEI uses this to know where the GUI is on screen so it can position
     * the item list sidebar to the right of it.
     */
    private static IGuiProperties getGuiProperties(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof BargainScreen bargainScreen)) {
            return null;
        }
        return new BargainGuiProperties(
                BargainScreen.class,
                bargainScreen.getGuiLeft(),
                bargainScreen.getGuiTop(),
                bargainScreen.getGuiWidth(),
                bargainScreen.getGuiHeight(),
                screen.width, screen.height
        );
    }

    /**
     * Simple IGuiProperties implementation for the BargainScreen.
     */
    private static final class BargainGuiProperties implements IGuiProperties {
        private final Class<? extends Screen> screenClass;
        private final int guiLeft, guiTop, guiXSize, guiYSize, screenWidth, screenHeight;

        BargainGuiProperties(
                Class<? extends Screen> screenClass,
                int guiLeft, int guiTop,
                int guiXSize, int guiYSize,
                int screenWidth, int screenHeight
        ) {
            this.screenClass = screenClass;
            this.guiLeft = guiLeft;
            this.guiTop = guiTop;
            this.guiXSize = guiXSize;
            this.guiYSize = guiYSize;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        @Override public Class<? extends Screen> screenClass() { return screenClass; }
        @Override public int guiLeft() { return guiLeft; }
        @Override public int guiTop() { return guiTop; }
        @Override public int guiXSize() { return guiXSize; }
        @Override public int guiYSize() { return guiYSize; }
        @Override public int screenWidth() { return screenWidth; }
        @Override public int screenHeight() { return screenHeight; }
    }

    /**
     * Ghost ingredient handler for the BargainScreen.
     * Allows JEI to place items from its sidebar into the shulker slots (0-53).
     * The menu contains ONLY the two shulker grids — the player's real
     * inventory is not part of the bargain screen.
     */
    private static final class BargainGhostIngredientHandler implements IGhostIngredientHandler<BargainScreen> {

        @Override
        public <I> List<IGhostIngredientHandler.Target<I>> getTargetsTyped(
                BargainScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
            List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();

            // Only accept ItemStack ingredients (not fluids, etc.)
            if (!(ingredient.getIngredient() instanceof ItemStack)) {
                return targets;
            }

            BargainScreenHandler handler = screen.getMenu();
            int guiLeft = screen.getGuiLeft();
            int guiTop = screen.getGuiTop();

            // All menu slots are the two shulkers (27 each = 54 total)
            for (int slotIndex = 0; slotIndex < BargainScreenHandler.TOTAL_SLOTS; slotIndex++) {
                Slot slot = handler.getSlot(slotIndex);
                if (slot == null || !slot.isActive()) continue;

                // Slot position is relative to the screen's guiLeft/guiTop
                int slotX = guiLeft + slot.x;
                int slotY = guiTop + slot.y;

                // Convert screen coordinates to Rect2i (used by JEI for ghost ingredient areas)
                Rect2i area = new Rect2i(slotX, slotY, 18, 18);

                targets.add(new SlotTarget<>(area, slotIndex, screen, ingredient));
            }

            return targets;
        }

        @Override
        public void onComplete() {
            // No-op
        }

        @Override
        public boolean shouldHighlightTargets() {
            return true;
        }
    }

    /**
     * A JEI ghost ingredient target that places an ItemStack into a specific slot
     * when the player clicks it.
     */
    private static final class SlotTarget<I> implements IGhostIngredientHandler.Target<I> {
        private final Rect2i area;
        private final int slotIndex;
        private final BargainScreen screen;
        private final ITypedIngredient<I> ingredient;

        SlotTarget(Rect2i area, int slotIndex,
                   BargainScreen screen, ITypedIngredient<I> ingredient) {
            this.area = area;
            this.slotIndex = slotIndex;
            this.screen = screen;
            this.ingredient = ingredient;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack) {
                // Place a full stack, matching the built-in picker's behavior.
                ItemStack copy = stack.copy();
                copy.setCount(copy.getMaxStackSize());
                Slot slot = screen.getMenu().getSlot(slotIndex);
                if (slot.mayPlace(copy)) {
                    slot.set(copy);
                }
            }
        }
    }
}
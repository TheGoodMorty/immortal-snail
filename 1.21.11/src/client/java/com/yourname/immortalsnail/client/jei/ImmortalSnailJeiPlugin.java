package com.yourname.immortalsnail.client.jei;

import com.yourname.immortalsnail.client.BargainScreen;
import com.yourname.immortalsnail.player.BargainScreenHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

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
 */
@JeiPlugin
public class ImmortalSnailJeiPlugin implements IModPlugin {

    private static final Identifier PLUGIN_UID =
            Identifier.of("immortalsnail", "jei_plugin");

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
    private static IGuiProperties getGuiProperties(HandledScreen<?> screen) {
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
        private final Class<? extends net.minecraft.client.gui.screen.Screen> screenClass;
        private final int guiLeft, guiTop, guiXSize, guiYSize, screenWidth, screenHeight;

        BargainGuiProperties(
                Class<? extends net.minecraft.client.gui.screen.Screen> screenClass,
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

        @Override public Class<? extends net.minecraft.client.gui.screen.Screen> screenClass() { return screenClass; }
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

            BargainScreenHandler handler = screen.getScreenHandler();
            int guiLeft = screen.getGuiLeft();
            int guiTop = screen.getGuiTop();

            // All menu slots are the two shulkers (27 each = 54 total)
            for (int slotIndex = 0; slotIndex < BargainScreenHandler.TOTAL_SLOTS; slotIndex++) {
                net.minecraft.screen.slot.Slot slot = handler.getSlot(slotIndex);
                if (slot == null || !slot.isEnabled()) continue;

                // Slot position is relative to the screen's guiLeft/guiTop
                int slotX = guiLeft + slot.x;
                int slotY = guiTop + slot.y;

                // Convert screen coordinates to Rect2i (used by JEI for ghost ingredient areas)
                net.minecraft.client.util.math.Rect2i area = new net.minecraft.client.util.math.Rect2i(
                        slotX, slotY, 18, 18
                );

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
        private final net.minecraft.client.util.math.Rect2i area;
        private final int slotIndex;
        private final BargainScreen screen;
        private final ITypedIngredient<I> ingredient;

        SlotTarget(net.minecraft.client.util.math.Rect2i area, int slotIndex,
                   BargainScreen screen, ITypedIngredient<I> ingredient) {
            this.area = area;
            this.slotIndex = slotIndex;
            this.screen = screen;
            this.ingredient = ingredient;
        }

        @Override
        public net.minecraft.client.util.math.Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack) {
                // Place a full stack, matching the built-in picker's behavior.
                ItemStack copy = stack.copy();
                copy.setCount(copy.getMaxCount());
                net.minecraft.screen.slot.Slot slot = screen.getScreenHandler().getSlot(slotIndex);
                if (slot.canInsert(copy)) {
                    slot.setStack(copy);
                }
            }
        }
    }
}
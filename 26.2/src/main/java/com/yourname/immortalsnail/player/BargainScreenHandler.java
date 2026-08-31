package com.yourname.immortalsnail.player;

import com.yourname.immortalsnail.ImmortalSnail;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side screen handler for the bargain GUI.
 *
 * Layout (identical to the client-side {@code BargainScreen}):
 *   - Slots 0..26:  Shulker A (3 rows x 9 cols)
 *   - Slots 27..53: Shulker B (3 rows x 9 cols)
 *
 * The player's own inventory is intentionally NOT part of this menu. The
 * bargain screen is purely a chooser: the player picks items into the two
 * shulker grids (client-side ghost slots / JEI ghost ingredients), and the
 * chosen contents are submitted as a {@code BargainSubmitPayload} on confirm.
 * Nothing is ever taken from or placed into the real player inventory here,
 * so rendering it would only make the screen needlessly tall.
 *
 * The two shulker inventories are stored as fields so they can be read out
 * when the player submits the bargain.
 */
public class BargainScreenHandler extends AbstractContainerMenu {

    public static final int SHULKER_COLS = 9;
    public static final int SHULKER_ROWS = 3;
    public static final int SLOT_SIZE = 18;
    public static final int TOTAL_SLOTS = 2 * SHULKER_ROWS * SHULKER_COLS; // 54

    public static final int SHULKER_OFFSET_X = 8;
    public static final int SHULKER_GAP = 12;
    public static final int SHULKER_B_X = SHULKER_OFFSET_X + SHULKER_COLS * SLOT_SIZE + SHULKER_GAP;
    /** Grids start at y=30, leaving room for the "Shulker A"/"Shulker B" labels above them. */
    public static final int SHULKER_Y = 30;

    // The two shulker inventories
    private final SimpleContainer shulkerA = new SimpleContainer(27);
    private final SimpleContainer shulkerB = new SimpleContainer(27);

    public BargainScreenHandler(int syncId, Inventory playerInventory) {
        super(ModMenus.BARGAIN, syncId);

        // Shulker A inventory (slots 0-26)
        for (int i = 0; i < SHULKER_ROWS * SHULKER_COLS; i++) {
            int x = SHULKER_OFFSET_X + (i % SHULKER_COLS) * SLOT_SIZE;
            int y = SHULKER_Y + (i / SHULKER_COLS) * SLOT_SIZE;
            this.addSlot(new Slot(shulkerA, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !isBlacklisted(stack);
                }
            });
        }

        // Shulker B inventory (slots 27-53)
        for (int i = 0; i < SHULKER_ROWS * SHULKER_COLS; i++) {
            int x = SHULKER_B_X + (i % SHULKER_COLS) * SLOT_SIZE;
            int y = SHULKER_Y + (i / SHULKER_COLS) * SLOT_SIZE;
            this.addSlot(new Slot(shulkerB, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !isBlacklisted(stack);
                }
            });
        }
    }

    /**
     * Read the contents of both shulker inventories.
     * Returns arrays indexed 0..26; null entries mean empty slots.
     */
    public ItemStack[] getShulkerAContents() {
        ItemStack[] out = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack s = shulkerA.getItem(i);
            out[i] = s.isEmpty() ? null : s.copy();
        }
        return out;
    }

    public ItemStack[] getShulkerBContents() {
        ItemStack[] out = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack s = shulkerB.getItem(i);
            out[i] = s.isEmpty() ? null : s.copy();
        }
        return out;
    }

    /**
     * Quick-move (shift-click): nothing to move — this menu contains only the
     * two bargain shulkers and no other inventory. All item placement is
     * client-side (ghost slots) and submitted as a payload on confirm.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private boolean isBlacklisted(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (key.isEmpty()) return false;
        String id = key.get().identifier().toString();
        for (String blocked : ImmortalSnail.BLACKLIST) {
            if (id.equals(blocked)) return true;
        }
        return false;
    }

    /**
     * Factory used to open the bargain screen on the server.
     * Implements {@link MenuProvider}.
     */
    public static class Factory implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.literal("The Bargain");
        }

        @Override
        public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
            return new BargainScreenHandler(syncId, inv);
        }
    }
}

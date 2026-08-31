package com.yourname.immortalsnail.client;

import com.yourname.immortalsnail.BargainSubmitPayload;
import com.yourname.immortalsnail.player.BargainScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Bargain Screen — the two shulker grids on top, the item picker below,
 * buttons at the bottom. A single 352-wide panel, so it fits comfortably
 * even on small screens. (The player's real inventory is not shown: nothing
 * is ever taken from it — this screen is purely a chooser.)
 *
 * Layout:
 *   ┌──────────────────────────────────────┐
 *   │              The Bargain             │
 *   │   Shulker A            Shulker B     │
 *   │   (3×9)                (3×9)         │
 *   ├──────────────────────────────────────┤
 *   │          Item Picker (scroll)        │
 *   │        18×4 grid of all items        │
 *   │    L: next empty  |  R: selected     │
 *   ├──────────────────────────────────────┤
 *   │  [Clear]                   [Confirm]  │
 *   └──────────────────────────────────────┘
 *
 * Interaction:
 *   - Left-click a picker item: place a FULL stack into the next empty
 *     shulker slot (fills successive slots, so you can queue many items).
 *   - Right-click a picker item: place a full stack into the selected slot.
 *   - Left-click a shulker slot: select it (yellow highlight).
 *   - Right-click a shulker slot: clear it.
 */
public class BargainScreen extends HandledScreen<BargainScreenHandler> {

    private static final int SLOT_SIZE = 18;

    // Shulker grid geometry (mirrors BargainScreenHandler)
    private static final int SHULKER_OFFSET_X = BargainScreenHandler.SHULKER_OFFSET_X;
    private static final int SHULKER_B_X = BargainScreenHandler.SHULKER_B_X;
    private static final int SHULKER_Y = BargainScreenHandler.SHULKER_Y;
    private static final int SHULKER_COLS = BargainScreenHandler.SHULKER_COLS;
    private static final int SHULKER_ROWS = BargainScreenHandler.SHULKER_ROWS;
    private static final int SHULKER_WIDTH = SHULKER_COLS * SLOT_SIZE;   // 162
    private static final int SHULKER_HEIGHT = SHULKER_ROWS * SLOT_SIZE;  // 54
    private static final int TOTAL_SLOTS = BargainScreenHandler.TOTAL_SLOTS; // 54

    // Item picker (below the shulker grids, centered)
    private static final int PICKER_COLS = 18;
    private static final int PICKER_ROWS = 4;
    private static final int PICKER_HEADER = 12;
    private static final int PICKER_INSTR_HEIGHT = 10;
    private static final int PICKER_WIDTH = PICKER_COLS * SLOT_SIZE + 8;   // 332
    private static final int PICKER_HEIGHT =
            PICKER_HEADER + PICKER_ROWS * SLOT_SIZE + PICKER_INSTR_HEIGHT + 2; // 96

    // Panel geometry
    private static final int SCREEN_PADDING = 8;
    private static final int PICKER_SECTION_GAP = 8;
    private static final int BUTTONS_GAP = 6;
    private static final int BUTTONS_HEIGHT = 18;
    private static final int BOTTOM_PADDING = 4;

    private static final int SLOT_PANEL_WIDTH = SHULKER_B_X + SHULKER_WIDTH + SCREEN_PADDING; // 352
    private static final int PICKER_X = (SLOT_PANEL_WIDTH - PICKER_WIDTH) / 2;               // 10
    private static final int PICKER_TOP = SHULKER_Y + SHULKER_HEIGHT + PICKER_SECTION_GAP;   // 92
    private static final int SLOT_PANEL_HEIGHT =
            PICKER_TOP + PICKER_HEIGHT + BUTTONS_GAP + BUTTONS_HEIGHT + BOTTOM_PADDING;     // 216

    // Item picker state
    private final List<Item> allItems;
    private int pickerScrollOffset = 0;
    private int selectedSlotIndex = 0;

    public BargainScreen(BargainScreenHandler handler, PlayerInventory playerInventory, Text title) {
        super(handler, playerInventory, title);
        this.backgroundWidth = SLOT_PANEL_WIDTH;
        this.backgroundHeight = SLOT_PANEL_HEIGHT;
        this.titleX = (SLOT_PANEL_WIDTH - this.textRenderer.getWidth(title)) / 2;
        this.titleY = 4;
        this.playerInventoryTitleX = -1000;
        this.playerInventoryTitleY = -1000;

        // Build the item picker list: every item in the registry except air
        // and blacklisted items, sorted by id.
        List<Item> items = new ArrayList<>();
        Registries.ITEM.stream()
                .filter(item -> !item.getTranslationKey().contains("air"))
                .filter(item -> {
                    String id = Registries.ITEM.getId(item).toString();
                    return !com.yourname.immortalsnail.ImmortalSnail.BLACKLIST.contains(id);
                })
                .sorted(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()))
                .forEach(items::add);
        this.allItems = items;
    }

    @Override
    protected void init() {
        super.init();

        int buttonsY = this.y + SLOT_PANEL_HEIGHT - BOTTOM_PADDING - BUTTONS_HEIGHT;

        // Clear button (bottom-left)
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), btn -> clearShulkers())
                .dimensions(this.x + SCREEN_PADDING, buttonsY, 70, BUTTONS_HEIGHT)
                .build());

        // Confirm button (bottom-right)
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), btn -> confirm())
                .dimensions(this.x + SLOT_PANEL_WIDTH - SCREEN_PADDING - 70, buttonsY, 70, BUTTONS_HEIGHT)
                .build());
    }

    private void confirm() {
        List<ItemStack> contents = new ArrayList<>();
        int nonEmpty = 0;
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack stack = this.handler.getSlot(i).getStack();
            contents.add(stack);
            if (!stack.isEmpty()) nonEmpty++;
        }
        if (this.client.player != null) {
            this.client.player.sendMessage(Text.literal("Bargain: " + nonEmpty + " items placed."), false);
        }
        ClientPlayNetworking.send(new BargainSubmitPayload(contents));
        this.client.setScreen(null);
    }

    private void clearShulkers() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            this.handler.getSlot(i).setStack(ItemStack.EMPTY);
        }
    }

    // ---- Rendering ----

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawPickerTooltip(context, mouseX, mouseY);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Outer panel
        context.fill(this.x - 2, this.y - 2,
                this.x + SLOT_PANEL_WIDTH + 2, this.y + SLOT_PANEL_HEIGHT + 2, 0xFF555555);
        context.fill(this.x, this.y,
                this.x + SLOT_PANEL_WIDTH, this.y + SLOT_PANEL_HEIGHT, 0xFF222222);

        // Shulker grid backgrounds
        int gridAY = this.y + SHULKER_Y;
        int gridBY = gridAY;
        context.fill(this.x + SHULKER_OFFSET_X - 2, gridAY - 2,
                this.x + SHULKER_OFFSET_X + SHULKER_WIDTH + 2, gridAY + SHULKER_HEIGHT + 2, 0xFF555555);
        context.fill(this.x + SHULKER_OFFSET_X, gridAY,
                this.x + SHULKER_OFFSET_X + SHULKER_WIDTH, gridAY + SHULKER_HEIGHT, 0xFF383838);
        context.fill(this.x + SHULKER_B_X - 2, gridBY - 2,
                this.x + SHULKER_B_X + SHULKER_WIDTH + 2, gridBY + SHULKER_HEIGHT + 2, 0xFF555555);
        context.fill(this.x + SHULKER_B_X, gridBY,
                this.x + SHULKER_B_X + SHULKER_WIDTH, gridBY + SHULKER_HEIGHT, 0xFF383838);

        // Grid labels
        context.drawText(this.textRenderer, "Shulker A",
                this.x + SHULKER_OFFSET_X + 2, gridAY - 11, 0xFFFFFFFF, false);
        context.drawText(this.textRenderer, "Shulker B",
                this.x + SHULKER_B_X + 2, gridBY - 11, 0xFFFFFFFF, false);

        // Selected slot highlight
        if (selectedSlotIndex >= 0 && selectedSlotIndex < TOTAL_SLOTS) {
            Slot s = this.handler.getSlot(selectedSlotIndex);
            int sx = this.x + s.x;
            int sy = this.y + s.y;
            context.fill(sx - 1, sy - 1, sx + SLOT_SIZE + 1, sy + SLOT_SIZE + 1, 0x80FFFF00);
        }

        // Item picker panel
        int pickerX = this.x + PICKER_X;
        int pickerY = this.y + PICKER_TOP;
        context.fill(pickerX - 2, pickerY - 2,
                pickerX + PICKER_WIDTH + 2, pickerY + PICKER_HEIGHT + 2, 0xFF555555);
        context.fill(pickerX, pickerY, pickerX + PICKER_WIDTH, pickerY + PICKER_HEIGHT, 0xFF222222);

        // Picker header
        context.drawText(this.textRenderer, "Item Picker (scroll)", pickerX + 4, pickerY + 3, 0xFFFFFFFF, false);

        // Picker grid
        int gridX = pickerX + 4;
        int gridY = pickerY + PICKER_HEADER;
        for (int i = 0; i < PICKER_COLS * PICKER_ROWS; i++) {
            int itemIndex = pickerScrollOffset * PICKER_COLS + i;
            if (itemIndex >= allItems.size()) break;
            Item item = allItems.get(itemIndex);
            int col = i % PICKER_COLS;
            int row = i / PICKER_COLS;
            int ix = gridX + col * SLOT_SIZE;
            int iy = gridY + row * SLOT_SIZE;
            context.fill(ix, iy, ix + SLOT_SIZE, iy + SLOT_SIZE, 0xFF383838);
            context.drawItem(new ItemStack(item), ix + 1, iy + 1);
        }

        // Scroll bar
        int totalRows = (allItems.size() + PICKER_COLS - 1) / PICKER_COLS;
        if (totalRows > PICKER_ROWS) {
            int scrollBarX = pickerX + PICKER_WIDTH - 5;
            int scrollBarH = PICKER_ROWS * SLOT_SIZE;
            context.fill(scrollBarX, gridY, scrollBarX + 3, gridY + scrollBarH, 0xFF555555);
            int thumbH = Math.max(8, scrollBarH * PICKER_ROWS / totalRows);
            int thumbY = gridY + (scrollBarH - thumbH) * pickerScrollOffset / Math.max(1, totalRows - PICKER_ROWS);
            context.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }

        // Instructions
        int instrY = pickerY + PICKER_HEIGHT - PICKER_INSTR_HEIGHT;
        context.drawText(this.textRenderer, "L: next empty | R: selected", pickerX + 4, instrY, 0xFFAAAAAA, false);
    }

    private void drawPickerTooltip(DrawContext context, int mouseX, int mouseY) {
        Item hovered = pickerItemAt(mouseX, mouseY);
        if (hovered != null) {
            context.drawItemTooltip(this.textRenderer, new ItemStack(hovered), mouseX, mouseY);
        }
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // === Item picker ===
        if (isOverPickerGrid(mouseX, mouseY)) {
            Item item = pickerItemAt(mouseX, mouseY);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                stack.setCount(stack.getMaxCount());
                if (button == 0) {
                    // Left: fill the next empty slot (auto-advance)
                    int nextEmpty = findNextEmptySlot();
                    if (nextEmpty >= 0) {
                        placeInSlot(nextEmpty, stack);
                        selectedSlotIndex = nextEmpty;
                    }
                } else if (button == 1) {
                    // Right: overwrite the selected slot
                    placeInSlot(selectedSlotIndex, stack);
                }
            }
            return true;
        }

        // === Shulker slots: left-click select, right-click clear ===
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            Slot slot = this.handler.getSlot(i);
            int slotX = this.x + slot.x;
            int slotY = this.y + slot.y;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                if (button == 1) {
                    slot.setStack(ItemStack.EMPTY);
                } else if (button == 0) {
                    selectedSlotIndex = i;
                }
                // Consume the click: slot contents are client-side ghosts
                // only; falling through to vanilla click handling would send
                // server packets and resync (wiping the ghost items).
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && isOverPickerGrid(mouseX, mouseY)) {
            int totalRows = (allItems.size() + PICKER_COLS - 1) / PICKER_COLS;
            int maxScroll = Math.max(0, totalRows - PICKER_ROWS);
            pickerScrollOffset = Math.max(0, Math.min(maxScroll,
                    pickerScrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---- Helpers ----

    private void placeInSlot(int slotIndex, ItemStack stack) {
        if (slotIndex < 0 || slotIndex >= TOTAL_SLOTS) return;
        Slot slot = this.handler.getSlot(slotIndex);
        if (slot.canInsert(stack)) {
            slot.setStack(stack.copy());
        }
    }

    private int findNextEmptySlot() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (this.handler.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    private boolean isOverPickerGrid(double mouseX, double mouseY) {
        int gridX = this.x + PICKER_X + 4;
        int gridY = this.y + PICKER_TOP + PICKER_HEADER;
        return mouseX >= gridX && mouseX < gridX + PICKER_COLS * SLOT_SIZE
                && mouseY >= gridY && mouseY < gridY + PICKER_ROWS * SLOT_SIZE;
    }

    private Item pickerItemAt(double mouseX, double mouseY) {
        if (!isOverPickerGrid(mouseX, mouseY)) return null;
        int gridX = this.x + PICKER_X + 4;
        int gridY = this.y + PICKER_TOP + PICKER_HEADER;
        int col = (int) ((mouseX - gridX) / SLOT_SIZE);
        int row = (int) ((mouseY - gridY) / SLOT_SIZE);
        int index = (pickerScrollOffset + row) * PICKER_COLS + col;
        if (index >= 0 && index < allItems.size()) {
            return allItems.get(index);
        }
        return null;
    }

    // ---- Accessors used by the JEI plugin ----

    public int getGuiLeft() {
        return this.x;
    }

    public int getGuiTop() {
        return this.y;
    }

    public int getGuiWidth() {
        return this.backgroundWidth;
    }

    public int getGuiHeight() {
        return this.backgroundHeight;
    }
}

package com.yourname.immortalsnail.client;

import com.yourname.immortalsnail.BargainSubmitPayload;
import com.yourname.immortalsnail.player.BargainScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
public class BargainScreen extends AbstractContainerScreen<BargainScreenHandler> {

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

    public BargainScreen(BargainScreenHandler menu, Inventory inv, Component title) {
        super(menu, inv, title, SLOT_PANEL_WIDTH, SLOT_PANEL_HEIGHT);
        this.titleLabelX = (SLOT_PANEL_WIDTH - this.font.width(title)) / 2;
        this.titleLabelY = 4;
        this.inventoryLabelX = -1000;
        this.inventoryLabelY = -1000;

        // Build the item picker list: every item in the registry, sorted by
        // translation key, excluding the blacklist.
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getResourceKey(item);
            if (key.isEmpty()) continue;
            String id = key.get().identifier().toString();
            if (com.yourname.immortalsnail.ImmortalSnail.BLACKLIST.contains(id)) continue;
            items.add(item);
        }
        items.sort(Comparator.comparing(item -> item.getDescriptionId()));
        this.allItems = items;
    }

    @Override
    protected void init() {
        super.init();

        int buttonsY = this.topPos + SLOT_PANEL_HEIGHT - BOTTOM_PADDING - BUTTONS_HEIGHT;

        // Clear button (bottom-left)
        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> this.clearAll())
                .bounds(this.leftPos + SCREEN_PADDING, buttonsY, 70, BUTTONS_HEIGHT)
                .build());

        // Confirm button (bottom-right)
        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> this.confirm())
                .bounds(this.leftPos + SLOT_PANEL_WIDTH - SCREEN_PADDING - 70, buttonsY, 70, BUTTONS_HEIGHT)
                .build());
    }

    private void clearAll() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            this.menu.getSlot(i).set(ItemStack.EMPTY);
        }
    }

    private void confirm() {
        ItemStack[] a = this.menu.getShulkerAContents();
        ItemStack[] b = this.menu.getShulkerBContents();
        List<ItemStack> contents = new ArrayList<>(TOTAL_SLOTS);
        int nonEmpty = 0;
        for (ItemStack s : a) {
            contents.add(s == null ? ItemStack.EMPTY : s);
            if (s != null) nonEmpty++;
        }
        for (ItemStack s : b) {
            contents.add(s == null ? ItemStack.EMPTY : s);
            if (s != null) nonEmpty++;
        }
        if (this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(
                    Component.literal("Bargain: " + nonEmpty + " items placed."));
        }
        ClientPlayNetworking.send(new BargainSubmitPayload(contents));
        // 26.2: setScreen moved from Minecraft to Gui.
        this.minecraft.gui.setScreen(null);
    }

    // ---- Rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        // Tooltip for the hovered picker item (26.2 queues tooltips for the
        // next frame instead of rendering them immediately).
        Item hovered = pickerItemAt(mouseX, mouseY);
        if (hovered != null) {
            context.setTooltipForNextFrame(this.font, new ItemStack(hovered), mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Outer panel
        context.fill(this.leftPos - 2, this.topPos - 2,
                this.leftPos + SLOT_PANEL_WIDTH + 2, this.topPos + SLOT_PANEL_HEIGHT + 2, 0xFF555555);
        context.fill(this.leftPos, this.topPos,
                this.leftPos + SLOT_PANEL_WIDTH, this.topPos + SLOT_PANEL_HEIGHT, 0xFF222222);

        // Shulker grid backgrounds
        int gridAY = this.topPos + SHULKER_Y;
        int gridBY = gridAY;
        context.fill(this.leftPos + SHULKER_OFFSET_X - 2, gridAY - 2,
                this.leftPos + SHULKER_OFFSET_X + SHULKER_WIDTH + 2, gridAY + SHULKER_HEIGHT + 2, 0xFF555555);
        context.fill(this.leftPos + SHULKER_OFFSET_X, gridAY,
                this.leftPos + SHULKER_OFFSET_X + SHULKER_WIDTH, gridAY + SHULKER_HEIGHT, 0xFF383838);
        context.fill(this.leftPos + SHULKER_B_X - 2, gridBY - 2,
                this.leftPos + SHULKER_B_X + SHULKER_WIDTH + 2, gridBY + SHULKER_HEIGHT + 2, 0xFF555555);
        context.fill(this.leftPos + SHULKER_B_X, gridBY,
                this.leftPos + SHULKER_B_X + SHULKER_WIDTH, gridBY + SHULKER_HEIGHT, 0xFF383838);

        // Grid labels
        context.text(this.font, "Shulker A", this.leftPos + SHULKER_OFFSET_X + 2, gridAY - 11, 0xFFFFFFFF, false);
        context.text(this.font, "Shulker B", this.leftPos + SHULKER_B_X + 2, gridBY - 11, 0xFFFFFFFF, false);

        // Selected slot highlight
        if (selectedSlotIndex >= 0 && selectedSlotIndex < TOTAL_SLOTS) {
            Slot s = this.menu.getSlot(selectedSlotIndex);
            int sx = this.leftPos + s.x;
            int sy = this.topPos + s.y;
            context.fill(sx - 1, sy - 1, sx + SLOT_SIZE + 1, sy + SLOT_SIZE + 1, 0x80FFFF00);
        }

        // Item picker panel
        int pickerX = this.leftPos + PICKER_X;
        int pickerY = this.topPos + PICKER_TOP;
        context.fill(pickerX - 2, pickerY - 2,
                pickerX + PICKER_WIDTH + 2, pickerY + PICKER_HEIGHT + 2, 0xFF555555);
        context.fill(pickerX, pickerY, pickerX + PICKER_WIDTH, pickerY + PICKER_HEIGHT, 0xFF222222);

        // Picker header
        context.text(this.font, "Item Picker (scroll)", pickerX + 4, pickerY + 3, 0xFFFFFFFF, false);

        // Picker grid
        int gridX = pickerX + 4;
        int gridY = pickerY + PICKER_HEADER;
        for (int i = 0; i < PICKER_COLS * PICKER_ROWS; i++) {
            int itemIndex = pickerScrollOffset * PICKER_COLS + i;
            if (itemIndex >= allItems.size()) break;
            Item item = allItems.get(itemIndex);
            int col = i % PICKER_COLS;
            int row = i / PICKER_COLS;
            int x = gridX + col * SLOT_SIZE;
            int y = gridY + row * SLOT_SIZE;
            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF383838);
            context.item(new ItemStack(item), x + 1, y + 1);
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
        context.text(this.font, "L: next empty | R: selected", pickerX + 4, instrY, 0xFFAAAAAA, false);
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // === Item picker ===
        if (isOverPickerGrid(mouseX, mouseY)) {
            Item item = pickerItemAt(mouseX, mouseY);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                stack.setCount(stack.getMaxStackSize());
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
            Slot slot = this.menu.getSlot(i);
            int slotX = this.leftPos + slot.x;
            int slotY = this.topPos + slot.y;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                if (button == 1) {
                    slot.set(ItemStack.EMPTY);
                } else if (button == 0) {
                    selectedSlotIndex = i;
                }
                // Consume the click: slot contents are client-side ghosts
                // only; falling through to vanilla click handling would send
                // server packets and resync (wiping the ghost items).
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
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
        Slot slot = this.menu.getSlot(slotIndex);
        if (slot.mayPlace(stack)) {
            slot.set(stack.copy());
        }
    }

    private int findNextEmptySlot() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (this.menu.getSlot(i).getItem().isEmpty()) return i;
        }
        return -1;
    }

    private boolean isOverPickerGrid(double mouseX, double mouseY) {
        int pickerX = this.leftPos + PICKER_X;
        int pickerY = this.topPos + PICKER_TOP;
        int gridX = pickerX + 4;
        int gridY = pickerY + PICKER_HEADER;
        return mouseX >= gridX && mouseX < gridX + PICKER_COLS * SLOT_SIZE
                && mouseY >= gridY && mouseY < gridY + PICKER_ROWS * SLOT_SIZE;
    }

    private Item pickerItemAt(double mouseX, double mouseY) {
        if (!isOverPickerGrid(mouseX, mouseY)) return null;
        int pickerX = this.leftPos + PICKER_X;
        int pickerY = this.topPos + PICKER_TOP;
        int col = (int) ((mouseX - (pickerX + 4)) / SLOT_SIZE);
        int row = (int) ((mouseY - (pickerY + PICKER_HEADER)) / SLOT_SIZE);
        int index = (pickerScrollOffset + row) * PICKER_COLS + col;
        if (index >= 0 && index < allItems.size()) {
            return allItems.get(index);
        }
        return null;
    }

    // ---- Accessors used by the JEI plugin ----

    public int getGuiLeft() {
        return this.leftPos;
    }

    public int getGuiTop() {
        return this.topPos;
    }

    public int getGuiWidth() {
        return this.imageWidth;
    }

    public int getGuiHeight() {
        return this.imageHeight;
    }
}

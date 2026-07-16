package com.Emil.TCAutoResearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.lib.UtilsFX;

public class GuiAspectCosts extends GuiScreen {

    private static final int MIN_ROWS_PER_PAGE = 4;
    private static final int MAX_ROWS_PER_PAGE = 24;
    private static final int ROW_START = 82;
    private static final int ROW_HEIGHT = 22;
    private static final int BOTTOM_RESERVED = 64;
    private static final int PANEL_COLOR = 0xFF505458;
    private static final int DIVIDER_COLOR = 0xFF3F4347;

    private final GuiScreen parent;
    private final Map<String, Integer> pendingCosts = new LinkedHashMap<>();
    private final List<String> filteredTags = new ArrayList<>();
    private final List<GuiTextField> costFields = new ArrayList<>();
    private GuiTextField searchField;
    private boolean inventoryPriority;
    private int page;
    private int rowsPerPage;

    public GuiAspectCosts(GuiScreen parent) {
        this.parent = parent;
        pendingCosts.putAll(Config.getAspectCostsSnapshot());
        inventoryPriority = Config.InventoryPriority;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int center = width / 2;
        rowsPerPage = Math.max(
            MIN_ROWS_PER_PAGE,
            Math.min(MAX_ROWS_PER_PAGE, (height - ROW_START - BOTTOM_RESERVED) / ROW_HEIGHT));
        searchField = new GuiTextField(fontRendererObj, center - 95, 30, 190, 18);
        searchField.setMaxStringLength(64);

        buttonList.add(new GuiButton(0, center - 145, height - 28, 70, 20, "\u4fdd\u5b58"));
        buttonList.add(new GuiButton(1, center - 70, height - 28, 70, 20, "\u53d6\u6d88"));
        buttonList.add(new GuiButton(2, center + 5, height - 28, 140, 20, "\u6062\u590d\u9ed8\u8ba4"));
        buttonList.add(new GuiButton(3, center - 145, height - 52, 70, 20, "\u4e0a\u4e00\u9875"));
        buttonList.add(new GuiButton(4, center + 75, height - 52, 70, 20, "\u4e0b\u4e00\u9875"));
        buttonList.add(new GuiButton(5, center - 70, height - 52, 140, 20, modeButtonText()));
        applyFilter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
        for (GuiTextField field : costFields) field.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            commitVisibleFields();
            Config.saveAspectCosts(pendingCosts, inventoryPriority);
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 2) {
            for (String tag : pendingCosts.keySet()) pendingCosts.put(tag, Config.getDefaultAspectCost(tag));
            rebuildCostFields();
        } else if (button.id == 3 && page > 0) {
            commitVisibleFields();
            page--;
            rebuildCostFields();
        } else if (button.id == 4 && page + 1 < pageCount()) {
            commitVisibleFields();
            page++;
            rebuildCostFields();
        } else if (button.id == 5) {
            inventoryPriority = !inventoryPriority;
            button.displayString = modeButtonText();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        String previousSearch = searchField.getText();
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            if (!previousSearch.equals(searchField.getText())) {
                commitVisibleFields();
                page = 0;
                applyFilter();
            }
            return;
        }
        for (GuiTextField field : costFields) {
            if (field.textboxKeyTyped(typedChar, keyCode)) return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : costFields) field.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int center = width / 2;
        int panelHalfWidth = Math.min(180, width / 2 - 8);
        int left = center - panelHalfWidth;
        int right = center + panelHalfWidth;
        drawRect(left, 8, right, height - 6, PANEL_COLOR);
        drawCenteredString(fontRendererObj, "\u8981\u7d20\u6743\u91cd", center, 14, 0xFFFFFF);
        fontRendererObj.drawString("\u641c\u7d22", center - 128, 35, 0xE0E0E0);
        searchField.drawTextBox();
        drawCenteredString(
            fontRendererObj,
            inventoryPriority
                ? "\u4f18\u5148\u4f7f\u7528\u5e93\u5b58\u5145\u8db3\u7684\u8981\u7d20\uff0c\u518d\u6bd4\u8f83\u6743\u91cd"
                : "\u6743\u91cd\u8d8a\u9ad8\uff0c\u5728\u89e3\u7b14\u8bb0\u65f6\u8d8a\u4e0d\u4f1a\u88ab\u4f7f\u7528\u5230",
            center,
            53,
            0xD0B878);
        fontRendererObj.drawString("\u8981\u7d20", left + 34, 69, 0xE0E0E0);
        fontRendererObj.drawString("\u6743\u91cd", right - 70, 69, 0xE0E0E0);

        int from = page * rowsPerPage;
        int y = ROW_START;
        boolean valid = true;
        for (int i = 0; i < costFields.size(); i++) {
            String tag = filteredTags.get(from + i);
            GuiTextField field = costFields.get(i);
            Aspect aspect = Aspect.getAspect(tag);
            drawRect(left + 6, y + 20, right - 6, y + 21, DIVIDER_COLOR);
            if (aspect != null) UtilsFX.drawTag(left + 10, y + 1, aspect, 0.0F, 0, 0.0D, 771, 1.0F, false);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(aspectName(tag), field.xPosition - left - 42),
                left + 34,
                y + 5,
                0xFFFFFF);
            boolean fieldValid = isPositiveInteger(field.getText());
            field.setTextColor(fieldValid ? 0xE0E0E0 : 0xFF6060);
            valid &= fieldValid;
            field.drawTextBox();
            y += ROW_HEIGHT;
        }
        ((GuiButton) buttonList.get(0)).enabled = valid;
        drawCenteredString(fontRendererObj, (page + 1) + " / " + pageCount(), center, height - 46, 0xD0D0D0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void applyFilter() {
        filteredTags.clear();
        String query = searchField.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        for (String tag : pendingCosts.keySet()) {
            String name = aspectName(tag)
                .toLowerCase(Locale.ROOT);
            if (query.isEmpty() || tag.toLowerCase(Locale.ROOT)
                .contains(query)
                || name.contains(query)) filteredTags.add(tag);
        }
        if (page >= pageCount()) page = pageCount() - 1;
        rebuildCostFields();
    }

    private void rebuildCostFields() {
        costFields.clear();
        int center = width / 2;
        int panelHalfWidth = Math.min(180, width / 2 - 8);
        int right = center + panelHalfWidth;
        int from = page * rowsPerPage;
        int to = Math.min(from + rowsPerPage, filteredTags.size());
        int y = ROW_START;
        for (int i = from; i < to; i++) {
            String tag = filteredTags.get(i);
            GuiTextField field = new GuiTextField(fontRendererObj, right - 74, y, 62, 18);
            field.setMaxStringLength(10);
            field.setText(Integer.toString(pendingCosts.get(tag)));
            costFields.add(field);
            y += ROW_HEIGHT;
        }
        ((GuiButton) buttonList.get(3)).enabled = page > 0;
        ((GuiButton) buttonList.get(4)).enabled = page + 1 < pageCount();
    }

    private void commitVisibleFields() {
        int from = page * rowsPerPage;
        for (int i = 0; i < costFields.size(); i++) {
            String tag = filteredTags.get(from + i);
            try {
                int value = Integer.parseInt(costFields.get(i)
                    .getText());
                if (value > 0) pendingCosts.put(tag, value);
            } catch (NumberFormatException ignored) {}
        }
    }

    private int pageCount() {
        return Math.max(1, (filteredTags.size() + rowsPerPage - 1) / rowsPerPage);
    }

    private boolean isPositiveInteger(String text) {
        try {
            return Integer.parseInt(text) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String aspectName(String tag) {
        return AspectNames.getDisplayName(tag);
    }

    private String modeButtonText() {
        return inventoryPriority ? "\u6a21\u5f0f: \u5e93\u5b58\u4f18\u5148" : "\u6a21\u5f0f: \u6743\u91cd\u4f18\u5148";
    }
}

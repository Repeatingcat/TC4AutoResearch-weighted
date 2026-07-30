package com.Emil.TCAutoResearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import org.lwjgl.input.Keyboard;

import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategoryList;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.common.lib.research.ResearchManager;

public class GuiResearchList extends GuiScreen {

    private static final int ROW_TOP = 68;
    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_COLOR = 0xFF505458;
    private static final int SELECTED_COLOR = 0xFF6E6242;

    private final GuiScreen parent;
    private final EntityPlayer player;
    private final Container container;
    private final List<ResearchItem> allResearch = new ArrayList<>();
    private final List<ResearchItem> filtered = new ArrayList<>();
    private GuiTextField searchField;
    private String selectedKey;
    private int page;
    private int rowsPerPage;

    public GuiResearchList(GuiScreen parent, EntityPlayer player, Container container) {
        this.parent = parent;
        this.player = player;
        this.container = container;
        loadResearch();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        rowsPerPage = Math.max(4, Math.min(20, (height - ROW_TOP - 76) / ROW_HEIGHT));
        searchField = new GuiTextField(fontRendererObj, width / 2 - 110, 32, 220, 18);
        searchField.setMaxStringLength(80);
        buttonList.add(new GuiButton(0, width / 2 - 150, height - 28, 95, 20, "\u5f00\u59cb\u7814\u7a76"));
        buttonList.add(new GuiButton(1, width / 2 - 50, height - 28, 100, 20, "\u8fd4\u56de"));
        buttonList.add(new GuiButton(2, width / 2 - 150, height - 52, 70, 20, "\u4e0a\u4e00\u9875"));
        buttonList.add(new GuiButton(3, width / 2 + 80, height - 52, 70, 20, "\u4e0b\u4e00\u9875"));
        applyFilter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && selectedKey != null) {
            ResearchPlan plan = ResearchPlan.build(player.getCommandSenderName(), selectedKey);
            if (!plan.blockers.isEmpty()) return;
            if (!Config.AutoResearch) {
                Config.AutoResearch = true;
                Config.SaveConfiguration();
            }
            mc.displayGuiScreen(parent);
            ResearchPlanController.start(mc, player, container, selectedKey);
        } else if (button.id == 1) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 2 && page > 0) {
            page--;
        } else if (button.id == 3 && page + 1 < pageCount()) {
            page++;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        String before = searchField.getText();
        if (searchField.textboxKeyTyped(typedChar, keyCode) && !before.equals(searchField.getText())) {
            page = 0;
            selectedKey = null;
            applyFilter();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        int left = width / 2 - Math.min(220, width / 2 - 8);
        int right = width / 2 + Math.min(220, width / 2 - 8);
        if (mouseX < left + 8 || mouseX >= right - 8 || mouseY < ROW_TOP) return;
        int row = (mouseY - ROW_TOP) / ROW_HEIGHT;
        int index = page * rowsPerPage + row;
        if (row >= 0 && row < rowsPerPage && index < filtered.size()) selectedKey = filtered.get(index).key;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int center = width / 2;
        int half = Math.min(220, width / 2 - 8);
        int left = center - half;
        int right = center + half;
        drawRect(left, 8, right, height - 6, PANEL_COLOR);
        drawCenteredString(fontRendererObj, "\u7814\u7a76\u5217\u8868", center, 14, 0xFFFFFF);
        fontRendererObj.drawString("\u641c\u7d22", center - 145, 37, 0xE0E0E0);
        searchField.drawTextBox();

        int from = page * rowsPerPage;
        int to = Math.min(from + rowsPerPage, filtered.size());
        for (int i = from; i < to; i++) {
            ResearchItem item = filtered.get(i);
            int y = ROW_TOP + (i - from) * ROW_HEIGHT;
            if (item.key.equals(selectedKey)) drawRect(left + 8, y, right - 8, y + ROW_HEIGHT - 1, SELECTED_COLOR);
            String category = categoryName(item);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(item.getName(), right - left - 150), left + 14, y + 6, 0xFFFFFF);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(category, 115), right - 125, y + 6, 0xC8C8C8);
        }

        ResearchPlan selectedPlan = selectedKey == null ? null : ResearchPlan.build(player.getCommandSenderName(), selectedKey);
        String status = "\u8bf7\u9009\u62e9\u76ee\u6807\u7814\u7a76";
        boolean canStart = false;
        if (selectedPlan != null) {
            if (selectedPlan.blockers.isEmpty()) {
                status = "\u5c06\u6309\u4f9d\u8d56\u987a\u5e8f\u5904\u7406 " + selectedPlan.steps.size() + " \u9879\u7814\u7a76";
                canStart = !selectedPlan.steps.isEmpty();
            } else {
                status = "\u9700\u5148\u624b\u52a8\u89e6\u53d1: " + selectedPlan.blockers.get(0);
            }
        }
        drawCenteredString(fontRendererObj, status, center, height - 48, canStart ? 0xD8D090 : 0xFF9090);
        drawCenteredString(fontRendererObj, (page + 1) + " / " + pageCount(), center, height - 65, 0xD0D0D0);
        ((GuiButton) buttonList.get(0)).enabled = canStart;
        ((GuiButton) buttonList.get(2)).enabled = page > 0;
        ((GuiButton) buttonList.get(3)).enabled = page + 1 < pageCount();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void loadResearch() {
        String playerName = player.getCommandSenderName();
        for (ResearchCategoryList category : ResearchCategories.researchCategories.values()) {
            for (ResearchItem item : category.research.values()) {
                if (item == null || item.isVirtual() || item.isAutoUnlock() || item.isStub() || item.isHidden()
                    || item.isLost() || ResearchManager.isResearchComplete(playerName, item.key)) continue;
                allResearch.add(item);
            }
        }
        allResearch.sort(Comparator.comparing((ResearchItem item) -> item.getName()).thenComparing(item -> item.key));
    }

    private void applyFilter() {
        filtered.clear();
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (ResearchItem item : allResearch) {
            String category = categoryName(item);
            if (query.isEmpty() || item.key.toLowerCase(Locale.ROOT).contains(query)
                || item.getName().toLowerCase(Locale.ROOT).contains(query)
                || category.toLowerCase(Locale.ROOT).contains(query)) filtered.add(item);
        }
        if (page >= pageCount()) page = pageCount() - 1;
    }

    private int pageCount() {
        return Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
    }

    private String categoryName(ResearchItem item) {
        String name = ResearchCategories.getCategoryName(item.category);
        return name == null ? item.category : name;
    }
}

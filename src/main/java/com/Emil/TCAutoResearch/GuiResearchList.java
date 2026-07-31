package com.Emil.TCAutoResearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategoryList;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.utils.InventoryUtils;

public class GuiResearchList extends GuiScreen {

    private static final ResourceLocation BOOK_TEXTURE =
        new ResourceLocation("thaumcraft", "textures/gui/gui_researchbook.png");
    private static final int SOURCE_BOOK_WIDTH = 512;
    private static final int SOURCE_BOOK_HEIGHT = 352;
    private static final int ROW_HEIGHT = 27;
    private static final int LIST_TOP_OFFSET = 88;
    private static final int DROPDOWN_NONE = 0;
    private static final int DROPDOWN_MOD = 4;
    private static final int DROPDOWN_CATEGORY = 5;
    private static final String ALL = "";

    private final GuiScreen parent;
    private final EntityPlayer player;
    private final Container container;
    private final List<ResearchItem> allResearch = new ArrayList<>();
    private final List<ResearchItem> filtered = new ArrayList<>();
    private final List<String> modFilters = new ArrayList<>();
    private final List<String> categoryFilters = new ArrayList<>();
    private final Map<String, String> researchModIds = new LinkedHashMap<>();
    private final Map<String, String> modNames = new LinkedHashMap<>();
    private GuiTextField searchField;
    private BookButton modButton;
    private BookButton categoryButton;
    private String selectedKey;
    private String selectedMod = ALL;
    private String selectedCategory = ALL;
    private int page;
    private int openDropdown = DROPDOWN_NONE;
    private int dropdownPage;
    private int rowsPerColumn;
    private int bookLeft;
    private int bookTop;
    private int bookWidth;
    private int bookHeight;

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
        calculateBookBounds();
        rowsPerColumn = Math.max(3, Math.min(7, (bookHeight - 150) / ROW_HEIGHT));

        int half = bookWidth / 2;
        searchField = new GuiTextField(fontRendererObj, bookLeft + 72, bookTop + 31, half - 108, 16);
        searchField.setMaxStringLength(80);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setTextColor(0x3C2515);

        int filterWidth = Math.min(174, half - 72);
        int navWidth = Math.max(50, Math.min(68, half / 3));
        int commandWidth = Math.max(58, half - navWidth - 42);
        modButton = new BookButton(4, bookLeft + 36, bookTop + 52, filterWidth, 18, modButtonText());
        categoryButton =
            new BookButton(5, bookLeft + half + 36, bookTop + 52, filterWidth, 18, categoryButtonText());
        buttonList.add(
            new BookButton(0, bookLeft + 24 + navWidth, bookTop + bookHeight - 27, commandWidth, 18, "\u5f00\u59cb\u7814\u7a76"));
        buttonList.add(
            new BookButton(1, bookLeft + half + 18, bookTop + bookHeight - 27, commandWidth, 18, "\u8fd4\u56de"));
        buttonList.add(new BookButton(2, bookLeft + 18, bookTop + bookHeight - 27, navWidth, 18, "\u4e0a\u4e00\u9875"));
        buttonList.add(
            new BookButton(3, bookLeft + bookWidth - navWidth - 18, bookTop + bookHeight - 27, navWidth, 18, "\u4e0b\u4e00\u9875"));
        buttonList.add(modButton);
        buttonList.add(categoryButton);
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
        } else if (button.id == 4) {
            toggleDropdown(DROPDOWN_MOD);
        } else if (button.id == 5) {
            toggleDropdown(DROPDOWN_CATEGORY);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            if (openDropdown != DROPDOWN_NONE) {
                openDropdown = DROPDOWN_NONE;
                return;
            }
            mc.displayGuiScreen(parent);
            return;
        }
        String before = searchField.getText();
        if (searchField.textboxKeyTyped(typedChar, keyCode) && !before.equals(searchField.getText())) {
            resetAndFilter();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (openDropdown != DROPDOWN_NONE) {
            if (isMouseOver(modButton, mouseX, mouseY)) {
                toggleDropdown(DROPDOWN_MOD);
                return;
            }
            if (isMouseOver(categoryButton, mouseX, mouseY)) {
                toggleDropdown(DROPDOWN_CATEGORY);
                return;
            }
            handleDropdownClick(mouseX, mouseY);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        int listTop = bookTop + LIST_TOP_OFFSET;
        int half = bookWidth / 2;
        for (int column = 0; column < 2; column++) {
            int left = bookLeft + 31 + column * half;
            int right = bookLeft + (column + 1) * half - 31;
            if (mouseX < left || mouseX >= right || mouseY < listTop) continue;
            int row = (mouseY - listTop) / ROW_HEIGHT;
            int index = page * itemsPerPage() + column * rowsPerColumn + row;
            if (row >= 0 && row < rowsPerColumn && index < filtered.size()) selectedKey = filtered.get(index).key;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawBookBackground();
        int center = width / 2;
        int half = bookWidth / 2;
        int leftPageCenter = bookLeft + half / 2;
        int rightPageCenter = bookLeft + half + half / 2;
        drawCenteredString(fontRendererObj, "\u7814\u7a76\u5217\u8868", center, bookTop + 14, 0x3A2415);
        fontRendererObj.drawString("\u641c\u7d22", bookLeft + 36, bookTop + 35, 0x5B4028);
        drawRect(bookLeft + 68, bookTop + 29, bookLeft + half - 32, bookTop + 49, 0x55382416);
        drawRect(bookLeft + 68, bookTop + 48, bookLeft + half - 32, bookTop + 49, 0x885B3B23);
        searchField.drawTextBox();

        if (openDropdown == DROPDOWN_NONE) {
            int from = page * itemsPerPage();
            int to = Math.min(from + itemsPerPage(), filtered.size());
            for (int i = from; i < to; i++) drawResearchEntry(filtered.get(i), i - from);
        }

        ResearchPlan selectedPlan = selectedKey == null ? null : ResearchPlan.build(player.getCommandSenderName(), selectedKey);
        String status = "\u8bf7\u9009\u62e9\u76ee\u6807\u7814\u7a76";
        boolean canStart = false;
        if (selectedPlan != null) {
            if (selectedPlan.blockers.isEmpty()) {
                status = "\u5c06\u6309\u4f9d\u8d56\u987a\u5e8f\u5904\u7406 " + selectedPlan.steps.size() + " \u9879\u7814\u7a76";
                canStart = !selectedPlan.steps.isEmpty();
            } else status = "\u9700\u8981\u624b\u52a8\u626b\u63cf\uff1a" + selectedPlan.blockers.get(0);
        }
        drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(status, half - 72),
            rightPageCenter,
            bookTop + bookHeight - 44,
            canStart ? 0x5C4A16 : 0x9A3028);
        if (openDropdown == DROPDOWN_NONE) {
            drawCenteredString(
                fontRendererObj,
                (page + 1) + "/" + pageCount() + " \u00b7 " + filtered.size(),
                leftPageCenter,
                bookTop + 74,
                0x6B5035);
        }
        ((GuiButton) buttonList.get(0)).enabled = canStart;
        ((GuiButton) buttonList.get(2)).enabled = page > 0;
        ((GuiButton) buttonList.get(3)).enabled = page + 1 < pageCount();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (openDropdown != DROPDOWN_NONE) drawDropdown(mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void calculateBookBounds() {
        bookWidth = Math.min(SOURCE_BOOK_WIDTH, width - 12);
        bookHeight = bookWidth * SOURCE_BOOK_HEIGHT / SOURCE_BOOK_WIDTH;
        if (bookHeight > height - 12) {
            bookHeight = height - 12;
            bookWidth = bookHeight * SOURCE_BOOK_WIDTH / SOURCE_BOOK_HEIGHT;
        }
        bookLeft = (width - bookWidth) / 2;
        bookTop = (height - bookHeight) / 2;
    }

    private void drawBookBackground() {
        mc.getTextureManager().bindTexture(BOOK_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(bookLeft, bookTop + bookHeight, zLevel, 0.0D, 0.6875D);
        tessellator.addVertexWithUV(bookLeft + bookWidth, bookTop + bookHeight, zLevel, 1.0D, 0.6875D);
        tessellator.addVertexWithUV(bookLeft + bookWidth, bookTop, zLevel, 1.0D, 0.0D);
        tessellator.addVertexWithUV(bookLeft, bookTop, zLevel, 0.0D, 0.0D);
        tessellator.draw();
    }

    private void drawResearchEntry(ResearchItem item, int offset) {
        int column = offset / rowsPerColumn;
        int row = offset % rowsPerColumn;
        int half = bookWidth / 2;
        int x = bookLeft + 36 + column * half;
        int y = bookTop + LIST_TOP_OFFSET + row * ROW_HEIGHT;
        int entryWidth = half - 72;
        if (item.key.equals(selectedKey)) {
            drawRect(x - 5, y, x + entryWidth + 5, y + ROW_HEIGHT - 2, 0x66563422);
            drawRect(x - 5, y + ROW_HEIGHT - 3, x + entryWidth + 5, y + ROW_HEIGHT - 2, 0xAA6D4A2E);
        }
        drawResearchIcon(item, x, y + 4);
        String name = fontRendererObj.trimStringToWidth(item.getName(), entryWidth - 22);
        String meta = modName(item) + " \u00b7 " + categoryName(item);
        fontRendererObj.drawString(name, x + 22, y + 3, 0x352115);
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(meta, entryWidth - 22), x + 22, y + 15, 0x765B3D);
    }

    private void drawResearchIcon(ResearchItem item, int x, int y) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (item.icon_item != null) {
            ItemStack icon = InventoryUtils.cycleItemStack(item.icon_item);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), icon, x, y);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
        } else if (item.icon_resource != null) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            mc.getTextureManager().bindTexture(item.icon_resource);
            UtilsFX.drawTexturedQuadFull(x, y, zLevel);
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void loadResearch() {
        String playerName = player.getCommandSenderName();
        Set<String> mods = new LinkedHashSet<>();
        Set<String> categories = new LinkedHashSet<>();
        modFilters.add(ALL);
        categoryFilters.add(ALL);
        for (ResearchCategoryList category : ResearchCategories.researchCategories.values()) {
            for (ResearchItem item : category.research.values()) {
                if (item == null || item.isVirtual() || item.isAutoUnlock() || item.isStub() || item.isHidden()
                    || item.isLost() || ResearchManager.isResearchComplete(playerName, item.key)) continue;
                allResearch.add(item);
                String modId = resolveModId(item);
                researchModIds.put(item.key, modId);
                modNames.put(modId, resolveModName(modId));
                mods.add(modId);
                categories.add(item.category);
            }
        }
        allResearch.sort(Comparator.comparing((ResearchItem item) -> item.getName()).thenComparing(item -> item.key));
        List<String> sortedMods = new ArrayList<>(mods);
        sortedMods.sort(Comparator.comparing(id -> modNames.get(id)));
        modFilters.addAll(sortedMods);
        List<String> sortedCategories = new ArrayList<>(categories);
        sortedCategories.sort(Comparator.comparing(this::categoryName));
        categoryFilters.addAll(sortedCategories);
    }

    private void applyFilter() {
        filtered.clear();
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (ResearchItem item : allResearch) {
            String category = categoryName(item);
            String mod = modName(item);
            if (!selectedMod.isEmpty() && !selectedMod.equals(researchModIds.get(item.key))) continue;
            if (!selectedCategory.isEmpty() && !selectedCategory.equals(item.category)) continue;
            if (query.isEmpty() || item.key.toLowerCase(Locale.ROOT).contains(query)
                || item.getName().toLowerCase(Locale.ROOT).contains(query)
                || category.toLowerCase(Locale.ROOT).contains(query)
                || mod.toLowerCase(Locale.ROOT).contains(query)) filtered.add(item);
        }
        if (page >= pageCount()) page = pageCount() - 1;
    }

    private void resetAndFilter() {
        page = 0;
        selectedKey = null;
        applyFilter();
    }

    private int itemsPerPage() {
        return rowsPerColumn * 2;
    }

    private int pageCount() {
        return Math.max(1, (filtered.size() + itemsPerPage() - 1) / itemsPerPage());
    }

    private String categoryName(ResearchItem item) {
        return categoryName(item.category);
    }

    private String categoryName(String category) {
        String name = ResearchCategories.getCategoryName(category);
        return name == null ? category : name;
    }

    private String modName(ResearchItem item) {
        String id = researchModIds.get(item.key);
        String name = modNames.get(id);
        return name == null ? id : name;
    }

    private String resolveModId(ResearchItem item) {
        if (item.icon_item != null) {
            GameRegistry.UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(item.icon_item.getItem());
            if (identifier != null) return identifier.modId;
        }
        if (item.icon_resource != null) return item.icon_resource.getResourceDomain();
        ResearchCategoryList category = ResearchCategories.getResearchList(item.category);
        if (category != null && category.icon != null) return category.icon.getResourceDomain();
        return "thaumcraft";
    }

    private String resolveModName(String modId) {
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        if (mod != null && mod.getName() != null && !mod.getName().isEmpty()) return mod.getName();
        if ("minecraft".equals(modId)) return "Minecraft";
        if ("thaumcraft".equalsIgnoreCase(modId)) return "Thaumcraft";
        return modId;
    }

    private String modButtonText() {
        String name = selectedMod.isEmpty() ? "\u5168\u90e8" : modNames.get(selectedMod);
        return "\u6a21\u7ec4: " + name + " \u25be";
    }

    private String categoryButtonText() {
        return "\u5206\u7c7b: " + (selectedCategory.isEmpty() ? "\u5168\u90e8" : categoryName(selectedCategory))
            + " \u25be";
    }

    private void toggleDropdown(int dropdown) {
        if (openDropdown == dropdown) {
            openDropdown = DROPDOWN_NONE;
            return;
        }
        openDropdown = dropdown;
        dropdownPage = selectedDropdownPage();
    }

    private List<String> dropdownFilters() {
        return openDropdown == DROPDOWN_MOD ? modFilters : categoryFilters;
    }

    private int dropdownRows() {
        return Math.max(3, (dropdownBottom() - dropdownTop() - 43) / 18);
    }

    private int dropdownItemsPerPage() {
        return dropdownRows() * 2;
    }

    private int dropdownPageCount() {
        return Math.max(1, (dropdownFilters().size() + dropdownItemsPerPage() - 1) / dropdownItemsPerPage());
    }

    private int selectedDropdownPage() {
        List<String> filters = dropdownFilters();
        String selected = openDropdown == DROPDOWN_MOD ? selectedMod : selectedCategory;
        int index = Math.max(0, filters.indexOf(selected));
        return index / dropdownItemsPerPage();
    }

    private int dropdownLeft() {
        return bookLeft + 54;
    }

    private int dropdownRight() {
        return bookLeft + bookWidth - 54;
    }

    private int dropdownTop() {
        return bookTop + 78;
    }

    private int dropdownBottom() {
        return bookTop + bookHeight - 48;
    }

    private void drawDropdown(int mouseX, int mouseY) {
        int left = dropdownLeft();
        int right = dropdownRight();
        int top = dropdownTop();
        int bottom = dropdownBottom();
        drawRect(left, top, right, bottom, 0xFF6A472A);
        drawRect(left + 2, top + 2, right - 2, bottom - 2, 0xF2DCC99E);
        String title = openDropdown == DROPDOWN_MOD ? "\u9009\u62e9\u6a21\u7ec4" : "\u9009\u62e9\u5206\u7c7b";
        drawCenteredString(fontRendererObj, title, (left + right) / 2, top + 7, 0x3B2617);

        List<String> filters = dropdownFilters();
        int first = dropdownPage * dropdownItemsPerPage();
        int columnWidth = (right - left - 16) / 2;
        for (int offset = 0; offset < dropdownItemsPerPage() && first + offset < filters.size(); offset++) {
            int column = offset / dropdownRows();
            int row = offset % dropdownRows();
            int x = left + 8 + column * columnWidth;
            int y = top + 23 + row * 18;
            String value = filters.get(first + offset);
            String selected = openDropdown == DROPDOWN_MOD ? selectedMod : selectedCategory;
            boolean hovered = mouseX >= x && mouseX < x + columnWidth - 4 && mouseY >= y && mouseY < y + 16;
            if (value.equals(selected)) drawRect(x, y, x + columnWidth - 4, y + 16, 0x886D4A2E);
            else if (hovered) drawRect(x, y, x + columnWidth - 4, y + 16, 0x447B4C27);
            String label = dropdownLabel(value);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(label, columnWidth - 12), x + 5, y + 4, 0x3B2617);
        }

        int navY = bottom - 19;
        if (dropdownPage > 0) fontRendererObj.drawString("< \u4e0a\u4e00\u9875", left + 10, navY, 0x4D301C);
        String pages = (dropdownPage + 1) + "/" + dropdownPageCount();
        drawCenteredString(fontRendererObj, pages, (left + right) / 2, navY, 0x6B5035);
        if (dropdownPage + 1 < dropdownPageCount()) {
            String next = "\u4e0b\u4e00\u9875 >";
            fontRendererObj.drawString(next, right - 10 - fontRendererObj.getStringWidth(next), navY, 0x4D301C);
        }
    }

    private void handleDropdownClick(int mouseX, int mouseY) {
        int left = dropdownLeft();
        int right = dropdownRight();
        int top = dropdownTop();
        int bottom = dropdownBottom();
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) {
            openDropdown = DROPDOWN_NONE;
            return;
        }

        int navY = bottom - 25;
        if (mouseY >= navY) {
            if (mouseX < (left + right) / 2 && dropdownPage > 0) dropdownPage--;
            else if (mouseX >= (left + right) / 2 && dropdownPage + 1 < dropdownPageCount()) dropdownPage++;
            return;
        }

        int columnWidth = (right - left - 16) / 2;
        if (mouseY < top + 23) return;
        if (mouseX < left + 8 || mouseX >= right - 8) return;
        int column = (mouseX - left - 8) / columnWidth;
        int row = (mouseY - top - 23) / 18;
        if (column < 0 || column > 1 || row < 0 || row >= dropdownRows()) return;
        int index = dropdownPage * dropdownItemsPerPage() + column * dropdownRows() + row;
        List<String> filters = dropdownFilters();
        if (index >= filters.size()) return;
        if (openDropdown == DROPDOWN_MOD) {
            selectedMod = filters.get(index);
            modButton.displayString = modButtonText();
        } else {
            selectedCategory = filters.get(index);
            categoryButton.displayString = categoryButtonText();
        }
        openDropdown = DROPDOWN_NONE;
        resetAndFilter();
    }

    private String dropdownLabel(String value) {
        if (value.isEmpty()) return "\u5168\u90e8";
        String label = openDropdown == DROPDOWN_MOD ? modNames.get(value) : categoryName(value);
        return label == null ? value : label;
    }

    private boolean isMouseOver(GuiButton button, int mouseX, int mouseY) {
        return mouseX >= button.xPosition && mouseX < button.xPosition + button.width && mouseY >= button.yPosition
            && mouseY < button.yPosition + button.height;
    }

    private static final class BookButton extends GuiButton {
        private BookButton(int id, int x, int y, int width, int height, String text) {
            super(id, x, y, width, height, text);
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            boolean hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width
                && mouseY < yPosition + height;
            int border = enabled ? (hovered ? 0xFF7B4C27 : 0xFF5B3A22) : 0xFF8B7968;
            int fill = enabled ? (hovered ? 0xCCCFB98D : 0xBBDCCAA3) : 0x99C8BCA8;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, border);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, fill);
            int color = enabled ? 0x3B2617 : 0x7C7063;
            drawCenteredString(
                minecraft.fontRenderer,
                minecraft.fontRenderer.trimStringToWidth(displayString, width - 6),
                xPosition + width / 2,
                yPosition + (height - 8) / 2,
                color);
        }
    }
}

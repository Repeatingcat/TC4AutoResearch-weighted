package com.Emil.TCAutoResearch;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import thaumcraft.api.aspects.Aspect;

public class GuiResearchDebug extends GuiScreen {

    private static final int ROWS_PER_PAGE = 8;

    private final GuiScreen parent;
    private int page;

    public GuiResearchDebug(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int center = width / 2;
        buttonList.add(new GuiButton(0, center - 45, height - 28, 90, 20, "\u8fd4\u56de"));
        buttonList.add(new GuiButton(1, center - 145, height - 28, 70, 20, "\u4e0a\u4e00\u9875"));
        buttonList.add(new GuiButton(2, center + 75, height - 28, 70, 20, "\u4e0b\u4e00\u9875"));
        updatePageButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 1 && page > 0) {
            page--;
            updatePageButtons();
        } else if (button.id == 2) {
            int pages = pageCount(ResearchDebugState.getSnapshot().entries);
            if (page + 1 < pages) page++;
            updatePageButtons();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        ResearchDebugState.Snapshot current = ResearchDebugState.getSnapshot();
        updatePageButtons();
        int center = width / 2;
        int left = center - 180;
        int right = center + 180;
        drawRect(left, 8, right, height - 34, 0xD0101010);

        drawCenteredString(fontRendererObj, "\u7814\u7a76\u89e3\u9898\u8c03\u8bd5", center, 16, 0xFFFFFF);
        fontRendererObj.drawString("\u72b6\u6001: " + statusText(current.status), left + 12, 34, statusColor(current.status));
        fontRendererObj.drawString("\u8017\u65f6: " + current.getElapsedMillis() + " ms", left + 180, 34, 0xB0B0B0);

        if (current.status == ResearchDebugState.Status.IDLE) {
            drawCenteredString(fontRendererObj, "\u5c1a\u65e0\u89e3\u9898\u8bb0\u5f55", center, 72, 0xA0A0A0);
        } else if (current.status == ResearchDebugState.Status.RUNNING) {
            drawCenteredString(fontRendererObj, "\u6c42\u89e3\u5668\u6b63\u5728\u8ba1\u7b97", center, 72, 0xFFE080);
        } else if (current.status == ResearchDebugState.Status.ERROR
            || current.status == ResearchDebugState.Status.NO_SOLUTION) {
                List<String> lines = fontRendererObj.listFormattedStringToWidth(current.message, 330);
                int y = 60;
                for (String line : lines) {
                    drawCenteredString(fontRendererObj, line, center, y, 0xFF8080);
                    y += 12;
                }
            } else {
                fontRendererObj.drawString("\u603b\u6210\u672c: " + current.totalCost, left + 12, 50, 0x80FF80);
                fontRendererObj.drawString("\u4f7f\u7528", left + 205, 50, 0xA0A0A0);
                fontRendererObj.drawString("\u5355\u4ef7", left + 250, 50, 0xA0A0A0);
                fontRendererObj.drawString("\u5c0f\u8ba1", left + 300, 50, 0xA0A0A0);
                drawEntries(current, left);
            }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawEntries(ResearchDebugState.Snapshot current, int left) {
        int from = page * ROWS_PER_PAGE;
        int to = Math.min(from + ROWS_PER_PAGE, current.entries.size());
        int y = 66;
        for (int i = from; i < to; i++) {
            ResearchDebugState.CostEntry entry = current.entries.get(i);
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(aspectName(entry.tag), 185), left + 12, y, 0xFFFFFF);
            fontRendererObj.drawString(Integer.toString(entry.count), left + 215, y, 0xD0D0D0);
            fontRendererObj.drawString(Integer.toString(entry.unitCost), left + 258, y, 0xD0D0D0);
            fontRendererObj.drawString(Long.toString(entry.subtotal), left + 308, y, 0x80FF80);
            y += 16;
        }
        String pageText = (page + 1) + " / " + pageCount(current.entries);
        drawCenteredString(fontRendererObj, pageText, width / 2, height - 42, 0x909090);
    }

    private String aspectName(String tag) {
        Aspect aspect = Aspect.getAspect(tag);
        String name = aspect == null ? tag : aspect.getName();
        return name + " (" + tag + ")";
    }

    private void updatePageButtons() {
        List<ResearchDebugState.CostEntry> entries = ResearchDebugState.getSnapshot().entries;
        int pages = pageCount(entries);
        if (page >= pages) page = pages - 1;
        ((GuiButton) buttonList.get(1)).enabled = page > 0;
        ((GuiButton) buttonList.get(2)).enabled = page + 1 < pages;
    }

    private int pageCount(List<ResearchDebugState.CostEntry> entries) {
        return Math.max(1, (entries.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private String statusText(ResearchDebugState.Status status) {
        switch (status) {
            case RUNNING:
                return "\u89e3\u9898\u4e2d";
            case SUCCESS:
                return "\u5df2\u5b8c\u6210";
            case NO_SOLUTION:
                return "\u65e0\u89e3";
            case ERROR:
                return "\u9519\u8bef";
            default:
                return "\u7a7a\u95f2";
        }
    }

    private int statusColor(ResearchDebugState.Status status) {
        if (status == ResearchDebugState.Status.SUCCESS) return 0x80FF80;
        if (status == ResearchDebugState.Status.ERROR || status == ResearchDebugState.Status.NO_SOLUTION) return 0xFF8080;
        if (status == ResearchDebugState.Status.RUNNING) return 0xFFE080;
        return 0xA0A0A0;
    }
}

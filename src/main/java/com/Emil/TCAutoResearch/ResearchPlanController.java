package com.Emil.TCAutoResearch;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import thaumcraft.api.IScribeTools;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.common.items.ItemResearchNotes;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketPlayerCompleteToServer;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;

public final class ResearchPlanController {
    private enum Phase { IDLE, NEXT, CLEAR_TABLE, SCRIBE_OUT, NOTE, SCRIBE_BACK, INSERT, SOLVE, NOTE_OUT, HOTBAR, READ, RESTORE, DIRECT }

    private static final int SCRIBE_SLOT = 0, NOTE_SLOT = 1, FIRST_PLAYER_SLOT = 2;
    private static final long SYNC_TIMEOUT = 5000L, READ_TIMEOUT = 10000L, SOLVE_TIMEOUT = 45000L;
    private static final long HOTBAR_SYNC_DELAY = 350L;
    private static boolean running;
    private static Phase phase = Phase.IDLE;
    private static List<ResearchItem> plan;
    private static int index, completed, windowId = -1;
    private static String currentKey;
    private static long phaseStartedAt, lastTickAt;
    private static int scribeSlot = -1, noteSlot = -1, tempSlot = -1, hotbarSlot = -1, originalHotbar = -1;
    private static boolean hotbarSwapped;

    private ResearchPlanController() {}

    public static boolean start(Minecraft mc, EntityPlayer player, Container container, String targetKey) {
        if (mc == null || player == null || container == null || container.inventorySlots.size() <= NOTE_SLOT) return false;
        if (player.inventory.getItemStack() != null) {
            if (!isResearchNote(player.inventory.getItemStack()))
                return reject(player, "\u9f20\u6807\u6307\u9488\u4e0a\u6709\u7269\u54c1");
            int recoverySlot = findEmpty(container);
            if (recoverySlot < 0)
                return reject(player, "\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u653e\u4e0b\u9f20\u6807\u4e0a\u7684\u7b14\u8bb0");
            click(mc, player, container, recoverySlot);
        }
        ResearchPlan result = ResearchPlan.build(player.getCommandSenderName(), targetKey);
        if (!result.blockers.isEmpty()) return reject(player, "\u9700\u5148\u624b\u52a8\u89e6\u53d1 " + result.blockers.get(0));
        if (result.steps.isEmpty()) return reject(player, "\u76ee\u6807\u7814\u7a76\u5df2\u5b8c\u6210");
        BatchResearchController.stopOnClose();
        plan = result.steps;
        index = completed = 0;
        windowId = container.windowId;
        running = true;
        phase = Phase.NEXT;
        phaseStartedAt = System.currentTimeMillis();
        lastTickAt = 0L;
        resetSlots();
        notify(player, "\u76ee\u6807\u7814\u7a76\u5df2\u5f00\u59cb\uff0c\u5171 " + plan.size() + " \u9879");
        return true;
    }

    public static void tick(Minecraft mc, EntityPlayer player, Container container) {
        if (!running) return;
        if (mc == null || mc.playerController == null || player == null || container == null || container.windowId != windowId) {
            stop(player, "\u7814\u7a76\u53f0\u5bb9\u5668\u5df2\u6539\u53d8", true); return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTickAt < 100L) return;
        lastTickAt = now;
        switch (phase) {
            case NEXT: beginNext(mc, player, container, now); break;
            case CLEAR_TABLE:
                if (!container.getSlot(NOTE_SLOT).getHasStack()) change(Phase.NEXT, now);
                else if (expired(now, SYNC_TIMEOUT))
                    fail(player, "\u65e0\u6cd5\u53d6\u56de\u7814\u7a76\u53f0\u4e2d\u6b8b\u7559\u7684\u5df2\u5b8c\u6210\u7b14\u8bb0");
                break;
            case SCRIBE_OUT:
                if (hasScribe(player)) request(player, now);
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u65e0\u6cd5\u5c06\u7b14\u4e0e\u58a8\u79fb\u5165\u80cc\u5305");
                break;
            case NOTE:
                int found = findNote(container, currentKey);
                if (found >= 0) { noteSlot = found; restoreScribeOrInsert(mc, player, container, now); }
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u672a\u83b7\u53d6\u5230\u7b14\u8bb0\uff0c\u8bf7\u68c0\u67e5\u7eb8\u3001\u7b14\u4e0e\u58a8\u53ca\u80cc\u5305\u7a7a\u4f4d");
                break;
            case SCRIBE_BACK:
                if (isScribe(container.getSlot(SCRIBE_SLOT).getStack())) insert(mc, player, container, now);
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u65e0\u6cd5\u5c06\u7b14\u4e0e\u58a8\u653e\u56de\u7814\u7a76\u53f0");
                break;
            case INSERT:
                ResearchNoteData inserted = data(container.getSlot(NOTE_SLOT).getStack());
                if (inserted != null && currentKey.equals(inserted.key)) change(Phase.SOLVE, now);
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u65e0\u6cd5\u5c06\u7b14\u8bb0\u653e\u5165\u7814\u7a76\u53f0");
                break;
            case SOLVE:
                ResearchNoteData note = data(container.getSlot(NOTE_SLOT).getStack());
                if (note == null || !currentKey.equals(note.key)) fail(player, "\u5f53\u524d\u7b14\u8bb0\u88ab\u79fb\u9664");
                else if (note.complete) takeNote(mc, player, container, now);
                else if (expired(now, SOLVE_TIMEOUT)) fail(player, "\u89e3\u9898\u8d85\u8fc7 45 \u79d2\uff0c\u8bf7\u68c0\u67e5\u8981\u7d20\u5e93\u5b58\u548c\u8c03\u8bd5\u4fe1\u606f");
                break;
            case NOTE_OUT:
                int completedNoteSlot = findCompleteNote(container, currentKey);
                if (completedNoteSlot >= 0) {
                    tempSlot = completedNoteSlot;
                    prepareRead(mc, player, container, now);
                }
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u65e0\u6cd5\u53d6\u56de\u5df2\u5b8c\u6210\u7b14\u8bb0");
                break;
            case HOTBAR:
                if (matchingComplete(player.inventory.getCurrentItem()) && now - phaseStartedAt >= HOTBAR_SYNC_DELAY)
                    use(mc, player, now);
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u65e0\u6cd5\u5c06\u7b14\u8bb0\u79fb\u5230\u5feb\u6377\u680f");
                break;
            case READ:
                if (ResearchManager.isResearchComplete(player.getCommandSenderName(), currentKey)) restoreOrAdvance(mc, player, container, now);
                else if (expired(now, READ_TIMEOUT)) fail(player, "\u670d\u52a1\u5668\u672a\u786e\u8ba4\u7814\u7a76\u5b8c\u6210");
                break;
            case RESTORE:
                if (!matchingComplete(player.inventory.getCurrentItem()) && player.inventory.getItemStack() == null)
                    advance(now);
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u65e0\u6cd5\u6062\u590d\u5feb\u6377\u680f\u7269\u54c1");
                break;
            case DIRECT:
                if (ResearchManager.isResearchComplete(player.getCommandSenderName(), currentKey)) advance(now);
                else if (expired(now, SYNC_TIMEOUT)) fail(player, "\u7814\u7a76\u8d2d\u4e70\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7814\u7a76\u70b9\u6570");
                break;
            default: break;
        }
    }

    public static boolean isRunning() { return running; }
    public static String getButtonText() { return running ? "\u7814\u7a76 " + completed + "/" + plan.size() : "\u7814\u7a76\u5217\u8868"; }
    public static void stopByUser(EntityPlayer player) { stop(player, "\u5df2\u624b\u52a8\u505c\u6b62", true); }
    public static void stopOnClose() { stop(null, "", false); }

    private static void beginNext(Minecraft mc, EntityPlayer player, Container container, long now) {
        ResearchNoteData staleTableNote = data(container.getSlot(NOTE_SLOT).getStack());
        if (staleTableNote != null && staleTableNote.complete
            && ResearchManager.isResearchComplete(player.getCommandSenderName(), staleTableNote.key)) {
            if (findEmpty(container) < 0) {
                fail(player, "\u80cc包没有空位取回研究台中残留的笔记");
                return;
            }
            mc.playerController.windowClick(container.windowId, NOTE_SLOT, 0, 1, player);
            change(Phase.CLEAR_TABLE, now);
            return;
        }
        while (index < plan.size() && ResearchManager.isResearchComplete(player.getCommandSenderName(), plan.get(index).key)) { index++; completed++; }
        if (index >= plan.size()) { finish(player); return; }
        ResearchItem item = plan.get(index);
        currentKey = item.key;
        resetSlots();
        if (!ResearchManager.doesPlayerHaveRequisites(player.getCommandSenderName(), currentKey)) { fail(player, "\u524d\u7f6e\u7814\u7a76\u5c1a\u672a\u540c\u6b65: " + item.getName()); return; }
        if (direct(item)) { send(player, (byte) 0); change(Phase.DIRECT, now); return; }
        ItemStack tableStack = container.getSlot(NOTE_SLOT).getStack();
        if (tableStack != null) {
            ResearchNoteData tableData = data(tableStack);
            if (tableData == null) { fail(player, "\u8bf7\u5148\u53d6\u8d70\u7814\u7a76\u53f0\u4e2d\u7684\u5176\u4ed6\u7269\u54c1"); return; }
            if (!currentKey.equals(tableData.key)) {
                if (!tableData.complete) {
                    fail(player, "\u8bf7\u5148\u53d6\u8d70\u7814\u7a76\u53f0\u4e2d\u7684\u5176\u4ed6\u672a\u5b8c\u6210\u7b14\u8bb0");
                    return;
                }
                if (findEmpty(container) < 0) {
                    fail(player, "\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u53d6\u56de\u7814\u7a76\u53f0\u4e2d\u7684\u7b14\u8bb0");
                    return;
                }
                mc.playerController.windowClick(container.windowId, NOTE_SLOT, 0, 1, player);
                change(Phase.CLEAR_TABLE, now);
                return;
            }
            change(Phase.SOLVE, now); return;
        }
        noteSlot = findNote(container, currentKey);
        if (noteSlot >= 0) { insert(mc, player, container, now); return; }
        if (findEmpty(container) < 0) { fail(player, "\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u63a5\u6536\u7b14\u8bb0"); return; }
        if (!hasPaper(player)) { fail(player, "\u80cc\u5305\u4e2d\u6ca1\u6709\u7eb8"); return; }
        if (hasScribe(player)) { request(player, now); return; }
        if (!isScribe(container.getSlot(SCRIBE_SLOT).getStack())) { fail(player, "\u6ca1\u6709\u53ef\u7528\u7684\u7b14\u4e0e\u58a8"); return; }
        scribeSlot = findEmpty(container);
        click(mc, player, container, SCRIBE_SLOT); click(mc, player, container, scribeSlot);
        change(Phase.SCRIBE_OUT, now);
    }

    private static void request(EntityPlayer player, long now) { send(player, (byte) 1); change(Phase.NOTE, now); }
    private static void send(EntityPlayer player, byte type) {
        PacketHandler.INSTANCE.sendToServer(new PacketPlayerCompleteToServer(currentKey, player.getCommandSenderName(), player.worldObj.provider.dimensionId, type));
    }
    private static void restoreScribeOrInsert(Minecraft mc, EntityPlayer player, Container container, long now) {
        if (scribeSlot >= 0 && !container.getSlot(SCRIBE_SLOT).getHasStack()) {
            click(mc, player, container, scribeSlot); click(mc, player, container, SCRIBE_SLOT); change(Phase.SCRIBE_BACK, now);
        } else insert(mc, player, container, now);
    }
    private static void insert(Minecraft mc, EntityPlayer player, Container container, long now) {
        if (noteSlot < FIRST_PLAYER_SLOT) noteSlot = findNote(container, currentKey);
        if (noteSlot < FIRST_PLAYER_SLOT) { fail(player, "\u627e\u4e0d\u5230\u5f85\u5904\u7406\u7684\u7b14\u8bb0"); return; }
        mc.playerController.windowClick(container.windowId, noteSlot, 0, 1, player); change(Phase.INSERT, now);
    }
    private static void takeNote(Minecraft mc, EntityPlayer player, Container container, long now) {
        if (findEmpty(container) < 0) { fail(player, "\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u53d6\u56de\u7b14\u8bb0"); return; }
        mc.playerController.windowClick(container.windowId, NOTE_SLOT, 0, 1, player);
        change(Phase.NOTE_OUT, now);
    }
    private static void prepareRead(Minecraft mc, EntityPlayer player, Container container, long now) {
        originalHotbar = player.inventory.currentItem;
        int inventoryIndex = container.getSlot(tempSlot).getSlotIndex();
        if (inventoryIndex == originalHotbar) { hotbarSwapped = false; change(Phase.HOTBAR, now); return; }
        hotbarSlot = findInventorySlot(container, originalHotbar);
        if (hotbarSlot < 0) { fail(player, "\u627e\u4e0d\u5230\u5f53\u524d\u5feb\u6377\u680f\u69fd\u4f4d"); return; }
        hotbarSwap(mc, player, container, tempSlot, originalHotbar);
        hotbarSwapped = true;
        change(Phase.HOTBAR, now);
    }
    private static void use(Minecraft mc, EntityPlayer player, long now) {
        mc.playerController.sendUseItem(player, player.worldObj, player.inventory.getCurrentItem()); change(Phase.READ, now);
    }
    private static void restoreOrAdvance(Minecraft mc, EntityPlayer player, Container container, long now) {
        if (!hotbarSwapped) {
            if (!matchingComplete(player.inventory.getCurrentItem())) advance(now);
            else {
                int emptySlot = findEmpty(container);
                if (emptySlot < 0) advance(now);
                else {
                    hotbarSwap(mc, player, container, emptySlot, originalHotbar);
                    change(Phase.RESTORE, now);
                }
            }
            return;
        }
        hotbarSwap(mc, player, container, tempSlot, originalHotbar);
        change(Phase.RESTORE, now);
    }
    private static void advance(long now) { completed++; index++; currentKey = null; resetSlots(); change(Phase.NEXT, now); }
    private static boolean direct(ResearchItem item) {
        return item.tags != null && item.tags.size() > 0 && (thaumcraft.common.config.Config.researchDifficulty == -1
            || (thaumcraft.common.config.Config.researchDifficulty == 0 && item.isSecondary()));
    }
    private static int findNote(Container c, String key) {
        for (int i = FIRST_PLAYER_SLOT; i < c.inventorySlots.size(); i++) { ResearchNoteData d = data(c.getSlot(i).getStack()); if (d != null && key.equals(d.key)) return i; }
        return -1;
    }
    private static int findCompleteNote(Container c, String key) {
        for (int i = FIRST_PLAYER_SLOT; i < c.inventorySlots.size(); i++) {
            ResearchNoteData d = data(c.getSlot(i).getStack());
            if (d != null && d.complete && key.equals(d.key)) return i;
        }
        return -1;
    }
    private static int findEmpty(Container c) { for (int i = FIRST_PLAYER_SLOT; i < c.inventorySlots.size(); i++) if (!c.getSlot(i).getHasStack()) return i; return -1; }
    private static int findInventorySlot(Container c, int index) { for (int i = FIRST_PLAYER_SLOT; i < c.inventorySlots.size(); i++) if (c.getSlot(i).getSlotIndex() == index) return i; return -1; }
    private static boolean hasScribe(EntityPlayer p) { for (ItemStack s : p.inventory.mainInventory) if (isScribe(s)) return true; return false; }
    private static boolean isScribe(ItemStack s) { return s != null && s.getItem() instanceof IScribeTools && s.getItemDamage() < s.getMaxDamage(); }
    private static boolean hasPaper(EntityPlayer p) { for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == Items.paper) return true; return false; }
    private static boolean isResearchNote(ItemStack s) { return s != null && s.getItem() instanceof ItemResearchNotes; }
    private static ResearchNoteData data(ItemStack s) { if (s == null || !(s.getItem() instanceof ItemResearchNotes)) return null; try { return ResearchManager.getData(s); } catch (RuntimeException ignored) { return null; } }
    private static boolean matchingComplete(ItemStack s) { ResearchNoteData d = data(s); return d != null && d.complete && currentKey.equals(d.key); }
    private static void click(Minecraft mc, EntityPlayer p, Container c, int slot) { mc.playerController.windowClick(c.windowId, slot, 0, 0, p); }
    private static void hotbarSwap(Minecraft mc, EntityPlayer p, Container c, int slot, int hotbarIndex) {
        mc.playerController.windowClick(c.windowId, slot, hotbarIndex, 2, p);
    }
    private static boolean expired(long now, long timeout) { return now - phaseStartedAt > timeout; }
    private static void change(Phase next, long now) { phase = next; phaseStartedAt = now; }
    private static void resetSlots() { scribeSlot = noteSlot = tempSlot = hotbarSlot = originalHotbar = -1; hotbarSwapped = false; }
    private static boolean reject(EntityPlayer p, String reason) { notify(p, "\u65e0\u6cd5\u5f00\u59cb\uff1a" + reason); return false; }
    private static void fail(EntityPlayer p, String reason) { stop(p, reason, true); }
    private static void finish(EntityPlayer p) { int count = completed; stop(null, "", false); notify(p, "\u76ee\u6807\u7814\u7a76\u5df2\u5b8c\u6210\uff0c\u5171\u5904\u7406 " + count + " \u9879"); }
    private static void stop(EntityPlayer p, String reason, boolean tell) { running = false; phase = Phase.IDLE; windowId = -1; currentKey = null; plan = null; index = 0; resetSlots(); if (tell) notify(p, "\u76ee\u6807\u7814\u7a76\u5df2\u505c\u6b62\uff1a" + reason); }
    private static void notify(EntityPlayer p, String text) { if (p != null) p.addChatMessage(new ChatComponentText(text)); }
}

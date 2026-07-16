package com.Emil.TCAutoResearch;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import thaumcraft.common.items.ItemResearchNotes;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;

public final class BatchResearchController {

    private enum Phase {
        IDLE,
        WAITING_FOR_INSERT,
        SOLVING,
        WAITING_FOR_PICKUP,
        WAITING_FOR_RETURN
    }

    private static final int NOTE_SLOT = 1;
    private static final int FIRST_PLAYER_SLOT = 2;
    private static final long TICK_INTERVAL = 75;
    private static final long ACTION_DELAY = 300;
    private static final long SLOT_SYNC_TIMEOUT = 3000;
    private static final long SOLVE_TIMEOUT = 30000;

    private static boolean running;
    private static Phase phase = Phase.IDLE;
    private static int windowId = -1;
    private static int total;
    private static int completed;
    private static boolean activeNoteCounted;
    private static int activeSourceSlot = -1;
    private static int returnSlot = -1;
    private static long phaseStartedAt;
    private static long lastActionAt;
    private static long lastTickAt;

    private BatchResearchController() {}

    public static boolean start(Minecraft minecraft, EntityPlayer player, Container container) {
        if (running) return true;
        if (container == null || container.inventorySlots.size() <= NOTE_SLOT) return false;
        if (player != null && player.inventory.getItemStack() != null) {
            notifyPlayer(player, "\u6279\u91cf\u89e3\u9898\u65e0\u6cd5\u5f00\u59cb\uff1a\u8bf7\u5148\u653e\u4e0b\u9f20\u6807\u6307\u9488\u4e0a\u7684\u7269\u54c1");
            return false;
        }
        if (!container.getSlot(0)
            .getHasStack()) {
            notifyPlayer(player, "\u6279\u91cf\u89e3\u9898\u65e0\u6cd5\u5f00\u59cb\uff1a\u7814\u7a76\u53f0\u6ca1\u6709\u7b14\u4e0e\u58a8");
            return false;
        }

        total = countIncompleteNotes(container);
        if (total == 0) {
            notifyPlayer(player, "\u80cc\u5305\u548c\u7814\u7a76\u53f0\u4e2d\u6ca1\u6709\u672a\u5b8c\u6210\u7684\u7814\u7a76\u7b14\u8bb0");
            return false;
        }

        running = true;
        completed = 0;
        windowId = container.windowId;
        long now = System.currentTimeMillis();
        lastActionAt = now - ACTION_DELAY;
        lastTickAt = 0;
        phaseStartedAt = now;
        activeSourceSlot = -1;
        returnSlot = -1;

        ItemStack tableStack = container.getSlot(NOTE_SLOT)
            .getStack();
        if (isResearchNote(tableStack)) {
            ResearchNoteData data = getData(tableStack);
            activeNoteCounted = data != null && !data.complete;
            phase = Phase.SOLVING;
        } else {
            activeNoteCounted = false;
            phase = Phase.IDLE;
        }
        notifyPlayer(player, "\u6279\u91cf\u89e3\u9898\u5df2\u5f00\u59cb\uff0c\u5171 " + total + " \u5f20\u672a\u5b8c\u6210\u7b14\u8bb0");
        return true;
    }

    public static void tick(Minecraft minecraft, EntityPlayer player, Container container) {
        if (!running) return;
        if (minecraft == null || minecraft.playerController == null || player == null || container == null
            || container.windowId != windowId) {
                stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u7814\u7a76\u53f0\u5bb9\u5668\u5df2\u6539\u53d8", true);
                return;
            }

        long now = System.currentTimeMillis();
        if (now - lastTickAt < TICK_INTERVAL) return;
        lastTickAt = now;

        Slot noteSlot = container.getSlot(NOTE_SLOT);
        ItemStack tableStack = noteSlot.getStack();

        switch (phase) {
            case IDLE:
                if (now - lastActionAt < ACTION_DELAY) return;
                if (!container.getSlot(0)
                    .getHasStack()) {
                    stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u7b14\u4e0e\u58a8\u5df2\u8017\u5c3d", true);
                    return;
                }
                int nextSlot = findNextIncompleteNote(container);
                if (nextSlot < 0) {
                    finish(player);
                    return;
                }
                if (player.inventory.getItemStack() != null) {
                    stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u9f20\u6807\u6307\u9488\u4e0a\u6709\u7269\u54c1", true);
                    return;
                }
                activeSourceSlot = nextSlot;
                shiftClick(minecraft, player, container, nextSlot);
                activeNoteCounted = false;
                phase = Phase.WAITING_FOR_INSERT;
                phaseStartedAt = now;
                lastActionAt = now;
                break;
            case WAITING_FOR_INSERT:
                ResearchNoteData insertedData = getData(tableStack);
                if (insertedData != null) {
                    activeNoteCounted = !insertedData.complete;
                    returnSlot = activeSourceSlot;
                    phase = Phase.SOLVING;
                    phaseStartedAt = now;
                } else if (now - phaseStartedAt > SLOT_SYNC_TIMEOUT) {
                    stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u65e0\u6cd5\u5c06\u7b14\u8bb0\u653e\u5165\u7814\u7a76\u53f0", true);
                }
                break;
            case SOLVING:
                if (!isResearchNote(tableStack)) {
                    stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u5f53\u524d\u7b14\u8bb0\u88ab\u79fb\u9664", true);
                    return;
                }
                ResearchNoteData stackData = getData(tableStack);
                boolean isComplete = stackData != null && stackData.complete;
                if (isComplete) {
                    if (now - lastActionAt < ACTION_DELAY) return;
                    returnSlot = findReturnSlot(container, returnSlot);
                    if (returnSlot < 0) {
                        stop(
                            player,
                            "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u53d6\u56de\u5df2\u5b8c\u6210\u7b14\u8bb0",
                            true);
                        return;
                    }
                    normalClick(minecraft, player, container, NOTE_SLOT);
                    phase = Phase.WAITING_FOR_PICKUP;
                    phaseStartedAt = now;
                    lastActionAt = now;
                } else if (!container.getSlot(0)
                    .getHasStack()) {
                    stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u7b14\u4e0e\u58a8\u5df2\u8017\u5c3d", true);
                } else if (now - phaseStartedAt > SOLVE_TIMEOUT) {
                    stop(
                        player,
                        "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u5355\u5f20\u7b14\u8bb0\u8d85\u8fc7 30 \u79d2\u672a\u5b8c\u6210",
                        true);
                }
                break;
            case WAITING_FOR_PICKUP:
                ItemStack carriedStack = player.inventory.getItemStack();
                if (!noteSlot.getHasStack() && isResearchNote(carriedStack)) {
                    returnSlot = findReturnSlot(container, returnSlot);
                    if (returnSlot < 0) {
                        restoreCarriedNote(minecraft, player, container, noteSlot);
                        stop(
                            player,
                            "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u53d6\u56de\u5df2\u5b8c\u6210\u7b14\u8bb0",
                            true);
                        return;
                    }
                    normalClick(minecraft, player, container, returnSlot);
                    phase = Phase.WAITING_FOR_RETURN;
                    phaseStartedAt = now;
                    lastActionAt = now;
                } else if (now - phaseStartedAt > SLOT_SYNC_TIMEOUT) {
                    restoreCarriedNote(minecraft, player, container, noteSlot);
                    stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u65e0\u6cd5\u4ece\u7814\u7a76\u53f0\u53d6\u4e0b\u7b14\u8bb0", true);
                }
                break;
            case WAITING_FOR_RETURN:
                carriedStack = player.inventory.getItemStack();
                if (carriedStack == null && isResearchNote(
                    container.getSlot(returnSlot)
                        .getStack())) {
                    if (activeNoteCounted) completed++;
                    activeNoteCounted = false;
                    activeSourceSlot = -1;
                    returnSlot = -1;
                    phase = Phase.IDLE;
                    phaseStartedAt = now;
                } else if (now - phaseStartedAt > SLOT_SYNC_TIMEOUT) {
                    restoreCarriedNote(minecraft, player, container, noteSlot);
                    stop(
                        player,
                        "\u6279\u91cf\u89e3\u9898\u5df2\u505c\u6b62\uff1a\u65e0\u6cd5\u53d6\u56de\u5df2\u5b8c\u6210\u7b14\u8bb0\uff0c\u8bf7\u68c0\u67e5\u80cc\u5305\u7a7a\u95f4",
                        true);
                }
                break;
            default:
                break;
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static String getButtonText() {
        return running ? "\u6279\u91cf " + completed + "/" + total : "\u6279\u91cf\u89e3\u9898";
    }

    public static void stopByUser(EntityPlayer player) {
        stop(player, "\u6279\u91cf\u89e3\u9898\u5df2\u624b\u52a8\u505c\u6b62", true);
    }

    public static void stopOnClose() {
        stop(null, "", false);
    }

    private static int countIncompleteNotes(Container container) {
        int count = 0;
        for (int slotIndex = NOTE_SLOT; slotIndex < container.inventorySlots.size(); slotIndex++) {
            ItemStack stack = container.getSlot(slotIndex)
                .getStack();
            ResearchNoteData data = getData(stack);
            if (data != null && !data.complete) count++;
        }
        return count;
    }

    private static int findNextIncompleteNote(Container container) {
        for (int slotIndex = FIRST_PLAYER_SLOT; slotIndex < container.inventorySlots.size(); slotIndex++) {
            ItemStack stack = container.getSlot(slotIndex)
                .getStack();
            ResearchNoteData data = getData(stack);
            if (data != null && !data.complete) return slotIndex;
        }
        return -1;
    }

    private static int findReturnSlot(Container container, int preferredSlot) {
        if (isEmptyPlayerSlot(container, preferredSlot)) return preferredSlot;
        for (int slotIndex = FIRST_PLAYER_SLOT; slotIndex < container.inventorySlots.size(); slotIndex++) {
            if (isEmptyPlayerSlot(container, slotIndex)) return slotIndex;
        }
        return -1;
    }

    private static boolean isEmptyPlayerSlot(Container container, int slotIndex) {
        return slotIndex >= FIRST_PLAYER_SLOT && slotIndex < container.inventorySlots.size()
            && !container.getSlot(slotIndex)
                .getHasStack();
    }

    private static ResearchNoteData getData(ItemStack stack) {
        if (!isResearchNote(stack)) return null;
        try {
            return ResearchManager.getData(stack);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isResearchNote(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemResearchNotes;
    }

    private static void shiftClick(Minecraft minecraft, EntityPlayer player, Container container, int slotIndex) {
        minecraft.playerController.windowClick(container.windowId, slotIndex, 0, 1, player);
    }

    private static void normalClick(Minecraft minecraft, EntityPlayer player, Container container, int slotIndex) {
        minecraft.playerController.windowClick(container.windowId, slotIndex, 0, 0, player);
    }

    private static void restoreCarriedNote(
        Minecraft minecraft,
        EntityPlayer player,
        Container container,
        Slot noteSlot) {
        if (!noteSlot.getHasStack() && isResearchNote(player.inventory.getItemStack()))
            normalClick(minecraft, player, container, NOTE_SLOT);
    }

    private static void finish(EntityPlayer player) {
        running = false;
        phase = Phase.IDLE;
        windowId = -1;
        lastTickAt = 0;
        activeSourceSlot = -1;
        returnSlot = -1;
        notifyPlayer(player, "\u6279\u91cf\u89e3\u9898\u5df2\u5b8c\u6210\uff0c\u5171\u5904\u7406 " + completed + " \u5f20\u7b14\u8bb0");
    }

    private static void stop(EntityPlayer player, String message, boolean notify) {
        running = false;
        phase = Phase.IDLE;
        windowId = -1;
        lastTickAt = 0;
        activeSourceSlot = -1;
        returnSlot = -1;
        if (notify) notifyPlayer(player, message);
    }

    private static void notifyPlayer(EntityPlayer player, String message) {
        if (player != null && message != null && !message.isEmpty())
            player.addChatMessage(new ChatComponentText(message));
    }
}

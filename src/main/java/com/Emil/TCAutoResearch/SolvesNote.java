package com.Emil.TCAutoResearch;

import static com.Emil.TCAutoResearch.ResearchCurrectNote.*;
import static java.lang.Thread.sleep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.lib.PlayerNotifications;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.utils.HexUtils;

public class SolvesNote {

    static Aspect Unless;
    public static String LastNote;
    public static int LastNoteID;

    public static void SolvesNoteHandle(String Line) {
        SolvesNote.LastNote = Line;
        ResearchDebugState.recordSolution(Line);
        Unless = null;
        var RetAspectNote = Line.split("&");
        if (RetAspectNote.length == 0) return;
        var ResearchRetData = new LinkedHashMap<HexUtils.Hex, Aspect>();
        var ResearchRetCount = new LinkedHashMap<Aspect, Integer>();

        for (String Item : RetAspectNote) {
            var RetAspectTemp = Item.split("\\|");
            if (RetAspectTemp.length == 2) {
                String coordinate = RetAspectTemp[0];
                HexUtils.Hex Hex = resolveHex(coordinate);
                if (Hex == null) {
                    String message = "Solver returned a coordinate outside this note: " + coordinate;
                    ResearchDebugState.recordFailure(message);
                    PlayerNotifications.addNotification("\u6c42\u89e3\u5668\u8fd4\u56de\u4e86\u65e0\u6548\u5750\u6807: " + coordinate);
                    return;
                }
                var AspectItem = Aspect.getAspect(RetAspectTemp[1]);
                if (AspectItem == null) {
                    String message = "Solver returned an unknown aspect: " + RetAspectTemp[1];
                    ResearchDebugState.recordFailure(message);
                    PlayerNotifications.addNotification("\u6c42\u89e3\u5668\u8fd4\u56de\u4e86\u672a\u77e5\u8981\u7d20: " + RetAspectTemp[1]);
                    return;
                }
                ResearchRetData.put(Hex, AspectItem);
                if (ResearchRetCount.containsKey(AspectItem)) {
                    ResearchRetCount.put(AspectItem, ResearchRetCount.get(AspectItem) + 1);
                } else {
                    ResearchRetCount.put(AspectItem, 1);
                }
            }
        }
        boolean Fail = false;
        var PlayaspectList = Thaumcraft.proxy.getPlayerKnowledge()
            .getAspectsDiscovered(player.getCommandSenderName());
        if (!ResearchRetCount.isEmpty()) {
            for (Map.Entry<Aspect, Integer> entry : ResearchRetCount.entrySet()) {
                while (!Fail) {
                    PlayaspectList = Thaumcraft.proxy.getPlayerKnowledge()
                        .getAspectsDiscovered(player.getCommandSenderName());
                    var AspectNum = PlayaspectList.getAmount(entry.getKey());
                    if (AspectNum < entry.getValue()) {
                        if (!FindCombineAspect(entry.getKey())) {
                            Fail = true;
                        }
                    } else break;
                    try {
                        sleep(250);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        var WaitResearchNote = guiResearchTable.note;
        if (!Fail && !ResearchRetData.isEmpty()) {
            for (Map.Entry<HexUtils.Hex, Aspect> entry : ResearchRetData.entrySet()) {
                var GetNoteAspect = WaitResearchNote.hexEntries.get(
                    entry.getKey()
                        .toString());
                if (GetNoteAspect != null) if (GetNoteAspect.aspect == entry.getValue()) continue;
                ;
                GuiResearchTableHelperInterfaceObj.place(entry.getKey(), entry.getValue());
            }
            PlayerNotifications.addNotification("笔记解锁成功");
        }
        if (Fail || ResearchRetCount.isEmpty()) {
            if (Unless != null) mc.thePlayer.addChatMessage(
                new ChatComponentText(
                    "笔记解锁失败,请补充" + "[" + StatCollector.translateToLocal("tc.aspect.help." + Unless.getTag()) + "]"));
            else mc.thePlayer.addChatMessage(new ChatComponentText("笔记解锁失败,算子解算失败,请手动连接各个元素"));
        }
    }

    private static HexUtils.Hex resolveHex(String coordinate) {
        if (guiResearchTable == null || guiResearchTable.note == null
            || !guiResearchTable.note.hexEntries.containsKey(coordinate)) return null;

        String[] parts = coordinate.split(":");
        if (parts.length != 2) return null;
        try {
            return new HexUtils.Hex(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean FindCombineAspect(Aspect aspect) {
        var Comptent = aspect.getComponents();
        if (Comptent != null) {
            var PlayaspectList = Thaumcraft.proxy.getPlayerKnowledge()
                .getAspectsDiscovered(player.getCommandSenderName());
            if (PlayaspectList.getAmount(Comptent[0]) == 0) {
                if (!FindCombineAspect(Comptent[0])) return false;
            }
            if (PlayaspectList.getAmount(Comptent[1]) == 0) {
                if (!FindCombineAspect(Comptent[1])) return false;
            }
            GuiResearchTableHelperInterfaceObj.combine(Comptent[0], Comptent[1]);
        } else {
            Unless = aspect;
            return false;
        }
        return true;
    }

    public static void killAutoResearchProcess() {
        String processName = "AutoResearch";
        try {
            boolean found = false;
            Process listProc = new ProcessBuilder("tasklist").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(listProc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains(processName.toLowerCase())) {
                    found = true;
                    break;
                }
            }
            listProc.waitFor();
            if (found) {
                Process killProc = new ProcessBuilder("kill", "-kill", processName).start();
                //killProc = new ProcessBuilder("taskkill", "/F", "/IM", processName + ".exe").start();
                killProc.waitFor();
            }
        } catch (Exception e) {
        }
    }
}

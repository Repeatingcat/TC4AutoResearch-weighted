package com.Emil.TCAutoResearch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.common.lib.research.ResearchManager;

public final class ResearchPlan {

    public final List<ResearchItem> steps;
    public final List<String> blockers;

    private ResearchPlan(List<ResearchItem> steps, List<String> blockers) {
        this.steps = steps;
        this.blockers = blockers;
    }

    public static ResearchPlan build(String playerName, String targetKey) {
        List<ResearchItem> steps = new ArrayList<>();
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        collect(playerName, targetKey, new HashSet<>(), new HashSet<>(), steps, blockers);
        return new ResearchPlan(steps, new ArrayList<>(blockers));
    }

    private static void collect(
        String playerName,
        String key,
        Set<String> visiting,
        Set<String> added,
        List<ResearchItem> steps,
        Set<String> blockers) {
        if (key == null || key.isEmpty() || ResearchManager.isResearchComplete(playerName, key) || added.contains(key))
            return;
        ResearchItem item = ResearchCategories.getResearch(key);
        if (item == null) {
            blockers.add(key);
            return;
        }
        if (!visiting.add(key)) {
            blockers.add(key);
            return;
        }
        collectParents(playerName, item.parents, visiting, added, steps, blockers);
        collectParents(playerName, item.parentsHidden, visiting, added, steps, blockers);
        visiting.remove(key);

        if (isExternalUnlock(item)) {
            blockers.add(scanTargetName(item));
            return;
        }
        if (item.tags == null || item.tags.size() == 0) {
            blockers.add(item.getName());
            return;
        }
        if (added.add(key)) steps.add(item);
    }

    private static void collectParents(
        String playerName,
        String[] parents,
        Set<String> visiting,
        Set<String> added,
        List<ResearchItem> steps,
        Set<String> blockers) {
        if (parents == null) return;
        for (String parent : parents) collect(playerName, parent, visiting, added, steps, blockers);
    }

    private static boolean isExternalUnlock(ResearchItem item) {
        return item.isAutoUnlock() || item.isVirtual() || item.isStub() || item.isHidden() || item.isLost();
    }

    private static String scanTargetName(ResearchItem item) {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        ItemStack[] itemTriggers = item.getItemTriggers();
        if (itemTriggers != null) {
            for (ItemStack trigger : itemTriggers) {
                if (trigger == null || trigger.getItem() == null) continue;
                ItemStack display = trigger.copy();
                if (display.getItemDamage() == OreDictionary.WILDCARD_VALUE) display.setItemDamage(0);
                targets.add(display.getDisplayName());
            }
        }
        String[] entityTriggers = item.getEntityTriggers();
        if (entityTriggers != null) {
            for (String trigger : entityTriggers) {
                if (trigger == null || trigger.isEmpty()) continue;
                String translated = StatCollector.translateToLocal("entity." + trigger + ".name");
                targets.add(translated.equals("entity." + trigger + ".name") ? trigger : translated);
            }
        }
        Aspect[] aspectTriggers = item.getAspectTriggers();
        if (aspectTriggers != null) {
            for (Aspect trigger : aspectTriggers) {
                if (trigger != null) targets.add(trigger.getName());
            }
        }
        if (targets.isEmpty()) return item.getName();
        StringBuilder result = new StringBuilder();
        for (String target : targets) {
            if (result.length() > 0) result.append(" / ");
            result.append(target);
        }
        return result.toString();
    }
}

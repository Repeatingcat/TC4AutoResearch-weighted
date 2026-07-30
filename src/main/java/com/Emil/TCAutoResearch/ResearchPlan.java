package com.Emil.TCAutoResearch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
            blockers.add(item.getName());
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
}

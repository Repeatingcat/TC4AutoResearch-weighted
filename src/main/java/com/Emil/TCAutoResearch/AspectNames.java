package com.Emil.TCAutoResearch;

import net.minecraft.util.StatCollector;

import thaumcraft.api.aspects.Aspect;

public final class AspectNames {

    private AspectNames() {}

    public static String getDisplayName(String tag) {
        Aspect aspect = Aspect.getAspect(tag);
        String internalName = aspect == null ? tag : aspect.getName();
        String translationKey = "tc.aspect." + tag;
        String translatedName = StatCollector.translateToLocal(translationKey);

        if (translatedName == null || translatedName.equals(translationKey)
            || translatedName.equalsIgnoreCase(internalName)) return internalName + " (" + tag + ")";

        return translatedName + " / " + internalName + " (" + tag + ")";
    }
}

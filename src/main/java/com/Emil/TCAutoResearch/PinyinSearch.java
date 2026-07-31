package com.Emil.TCAutoResearch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

final class PinyinSearch {
    private static final HanyuPinyinOutputFormat FORMAT = createFormat();
    private static final Map<String, String> CACHE = new HashMap<>();

    private PinyinSearch() {}

    static boolean matches(String query, String... values) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return true;
        for (String value : values) {
            if (value == null) continue;
            String text = value.toLowerCase();
            if (text.contains(normalizedQuery)) return true;
            String pinyin = toPinyin(value);
            if (pinyin.replace(" ", "").contains(normalizedQuery) || initials(pinyin).contains(normalizedQuery)) return true;
        }
        return false;
    }

    private static String toPinyin(String value) {
        String cached = CACHE.get(value);
        if (cached != null) return cached;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            try {
                String[] syllables = PinyinHelper.toHanyuPinyinStringArray(character, FORMAT);
                if (syllables == null || syllables.length == 0) result.append(character);
                else result.append(syllables[0]).append(' ');
            } catch (Exception ignored) {
                result.append(character);
            }
        }
        String pinyin = result.toString().toLowerCase(Locale.ROOT);
        CACHE.put(value, pinyin);
        return pinyin;
    }

    private static String initials(String pinyin) {
        StringBuilder result = new StringBuilder();
        boolean start = true;
        for (int i = 0; i < pinyin.length(); i++) {
            char character = pinyin.charAt(i);
            if (character >= 'a' && character <= 'z') {
                if (start) result.append(character);
                start = false;
            } else start = true;
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static HanyuPinyinOutputFormat createFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }
}

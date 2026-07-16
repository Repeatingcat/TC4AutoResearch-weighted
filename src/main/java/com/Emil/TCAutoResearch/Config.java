package com.Emil.TCAutoResearch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String ASPECT_COST_CATEGORY = "aspect_costs";
    private static final int UNKNOWN_ASPECT_COST = 16;

    public static boolean AutoResearch;
    public static Configuration config;
    public static File ConbfigFilePath;
    public static final Map<String, Integer> AspectCosts = new LinkedHashMap<>();
    private static final Map<String, Integer> DefaultAspectCosts = new LinkedHashMap<>();

    public static void synchronizeConfiguration(File configFile) {
        ConbfigFilePath = new File(configFile.getParent(), "TCAutoResearch.cfg");
        config = new Configuration(ConbfigFilePath);
        AutoResearch = config
            .getBoolean("AutoResearch", Configuration.CATEGORY_GENERAL, false, "Enable the Research Auto Start");
        loadAspectCosts(null);
        var nativeSolver = new File("AutoResearch.exe");
        if (!nativeMatchesResource(nativeSolver)) extractNativeFromZip();
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void SaveConfiguration() {
        config.get(Configuration.CATEGORY_GENERAL, "AutoResearch", false)
            .set(AutoResearch);
        config.save();
    }

    public static void registerAspectCosts(Collection<String> aspectTags) {
        loadAspectCosts(aspectTags);
        if (config.hasChanged()) config.save();
    }

    public static String serializeAspectCosts() {
        StringBuilder serialized = new StringBuilder();
        for (Map.Entry<String, Integer> entry : AspectCosts.entrySet()) {
            serialized.append(entry.getKey())
                .append(':')
                .append(entry.getValue())
                .append('&');
        }
        return serialized.toString();
    }

    public static synchronized Map<String, Integer> getAspectCostsSnapshot() {
        return new LinkedHashMap<>(AspectCosts);
    }

    public static synchronized int getDefaultAspectCost(String tag) {
        Integer cost = DefaultAspectCosts.get(tag);
        return cost == null ? UNKNOWN_ASPECT_COST : cost;
    }

    public static synchronized void saveAspectCosts(Map<String, Integer> costs) {
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            int cost = Math.max(1, entry.getValue());
            config.get(ASPECT_COST_CATEGORY, entry.getKey(), cost)
                .set(cost);
            AspectCosts.put(entry.getKey(), cost);
        }
        config.save();
    }

    private static void loadAspectCosts(Collection<String> aspectTags) {
        Properties defaults = new Properties();
        try (InputStream stream = Config.class.getResourceAsStream("/default_aspect_costs.properties")) {
            if (stream == null) throw new IOException("Missing default_aspect_costs.properties");
            defaults.load(stream);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load default aspect costs", e);
        }

        DefaultAspectCosts.clear();
        for (String tag : defaults.stringPropertyNames()) {
            DefaultAspectCosts.put(tag, Integer.parseInt(defaults.getProperty(tag)));
        }

        TreeSet<String> tags = new TreeSet<>(defaults.stringPropertyNames());
        if (config.hasCategory(ASPECT_COST_CATEGORY)) {
            tags.addAll(config.getCategory(ASPECT_COST_CATEGORY)
                .keySet());
        }
        if (aspectTags != null) tags.addAll(aspectTags);

        AspectCosts.clear();
        for (String tag : tags) {
            int defaultCost = Integer.parseInt(defaults.getProperty(tag, Integer.toString(UNKNOWN_ASPECT_COST)));
            int cost = config.getInt(
                tag,
                ASPECT_COST_CATEGORY,
                defaultCost,
                1,
                Integer.MAX_VALUE,
                "Higher values make this aspect less likely to be used in generated paths.");
            AspectCosts.put(tag, cost);
        }
    }

    private static boolean nativeMatchesResource(File nativeSolver) {
        if (!nativeSolver.isFile()) return false;

        try (InputStream zipStream = Config.class.getResourceAsStream("/AutoResearch.zip");
            ZipInputStream zis = zipStream == null ? null : new ZipInputStream(zipStream)) {
            if (zis == null) return false;
            ZipEntry entry = zis.getNextEntry();
            if (entry == null || entry.getCrc() < 0) return false;

            CRC32 crc = new CRC32();
            try (InputStream nativeStream = new FileInputStream(nativeSolver)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = nativeStream.read(buffer)) > 0) {
                    crc.update(buffer, 0, len);
                }
            }
            return crc.getValue() == entry.getCrc();
        } catch (IOException e) {
            return false;
        }
    }

    public static void extractNativeFromZip() {

        try (InputStream zipStream = AutoResearch.class.getResourceAsStream("/AutoResearch.zip");
            ZipInputStream zis = zipStream == null ? null : new ZipInputStream(zipStream)) {

            if (zis == null) throw new IOException("Missing AutoResearch.zip");

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try (FileOutputStream fos = new FileOutputStream(new File(entry.getName()).getName())) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

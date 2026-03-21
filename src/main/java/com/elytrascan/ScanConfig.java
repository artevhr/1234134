package com.elytrascan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ScanConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("elytrascan.json");

    // ── настройки ─────────────────────────────────────────────
    public static boolean scanEnabled      = false;
    public static List<String> targetBlocks = new ArrayList<>();
    public static int    scanRadius        = 3;   // в чанках (1 чанк = 16 блоков)
    public static boolean onlyWhenElytra   = true;

    // ── сохранение / загрузка ─────────────────────────────────
    public static void load() {
        File f = CONFIG_FILE.toFile();
        if (!f.exists()) {
            // добавим пример
            targetBlocks.add("minecraft:chest");
            save();
            return;
        }
        try (Reader r = new FileReader(f)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;
            scanEnabled   = d.scanEnabled;
            if (d.targetBlocks != null) targetBlocks = new ArrayList<>(d.targetBlocks);
            scanRadius    = Math.max(1, Math.min(8, d.scanRadius));
            onlyWhenElytra = d.onlyWhenElytra;
        } catch (Exception e) {
            ElytraScanMod.LOGGER.error("ElytraScan: не удалось загрузить конфиг", e);
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_FILE.toFile())) {
            Data d = new Data();
            d.scanEnabled    = scanEnabled;
            d.targetBlocks   = new ArrayList<>(targetBlocks);
            d.scanRadius     = scanRadius;
            d.onlyWhenElytra = onlyWhenElytra;
            GSON.toJson(d, w);
        } catch (Exception e) {
            ElytraScanMod.LOGGER.error("ElytraScan: не удалось сохранить конфиг", e);
        }
    }

    // ── POJO для Gson ─────────────────────────────────────────
    private static class Data {
        boolean      scanEnabled    = false;
        List<String> targetBlocks   = new ArrayList<>();
        int          scanRadius     = 3;
        boolean      onlyWhenElytra = true;
    }
}

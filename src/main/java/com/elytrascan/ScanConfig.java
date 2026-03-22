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

    public static boolean scanEnabled       = false;
    public static List<String> targetBlocks = new ArrayList<>();
    public static int     scanRadius        = 3;
    public static boolean onlyWhenElytra    = true;
    public static boolean bypassAntiXray    = false;
    public static boolean prioritizeText    = false; // также искать текст у блока
    public static boolean onlyWithText      = false; // записывать ТОЛЬКО если у блока есть текст
    public static boolean onlyNearChest     = false; // записывать ТОЛЬКО если рядом сундук (30 блоков)

    public static void load() {
        File f = CONFIG_FILE.toFile();
        if (!f.exists()) {
            targetBlocks.add("minecraft:chest");
            save();
            return;
        }
        try (Reader r = new FileReader(f)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;
            scanEnabled    = d.scanEnabled;
            if (d.targetBlocks != null) targetBlocks = new ArrayList<>(d.targetBlocks);
            scanRadius     = Math.max(1, Math.min(8, d.scanRadius));
            onlyWhenElytra = d.onlyWhenElytra;
            bypassAntiXray = d.bypassAntiXray;
            prioritizeText = d.prioritizeText;
            onlyWithText   = d.onlyWithText;
            onlyNearChest  = d.onlyNearChest;
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
            d.bypassAntiXray = bypassAntiXray;
            d.prioritizeText = prioritizeText;
            d.onlyWithText   = onlyWithText;
            d.onlyNearChest  = onlyNearChest;
            GSON.toJson(d, w);
        } catch (Exception e) {
            ElytraScanMod.LOGGER.error("ElytraScan: не удалось сохранить конфиг", e);
        }
    }

    private static class Data {
        boolean      scanEnabled    = false;
        List<String> targetBlocks   = new ArrayList<>();
        int          scanRadius     = 3;
        boolean      onlyWhenElytra = true;
        boolean      bypassAntiXray = false;
        boolean      prioritizeText = false;
        boolean      onlyWithText   = false;
        boolean      onlyNearChest  = false;
    }
}

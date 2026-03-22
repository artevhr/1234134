package com.elytrascan;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1923;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2561;
import net.minecraft.class_2586;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_7923;
import net.minecraft.class_8113;
import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BlockScanner {

    private static final Set<Long>       scannedChunks = new HashSet<>();
    private static final Set<Long>       scannedClose  = new HashSet<>();
    // Время (мс) когда чанк впервые попал в зону "вблизи" — для задержки антиксрея
    private static final Map<Long, Long>  chunkCloseTime = new HashMap<>();
    private static final Queue<class_1923> scanQueue     = new ArrayDeque<>();
    private static final Set<class_2338>   foundSet      = new HashSet<>();

    // Порог "вплотную" для обхода антиксрея — 2 чанка по XZ (32 блока).
    private static final int  BYPASS_CLOSE_CHUNKS = 2;
    // Задержка перед сканированием в режиме обхода (мс) — сервер успевает прислать настоящие блоки
    private static final long BYPASS_DELAY_MS     = 3000L;

    public static volatile int    totalFound    = 0;
    public static volatile int    chunksScanned = 0;
    public static volatile int    textFound     = 0;
    public static volatile String lastWorld     = "";

    private static String flashMsg   = "";
    private static long   flashUntil = 0L;

    private static final Path LOG_DIR =
            FabricLoader.getInstance().getGameDir().resolve("elytrascan_logs");
    private static PrintWriter logWriter      = null;
    private static String      currentLogName = "";

    // ── главный тик ──────────────────────────────────────────────────────────
    public static void tick(class_310 mc) {
        if (!ScanConfig.scanEnabled)               return;
        if (mc.field_1724 == null || mc.field_1687 == null) return;

        class_1657 player = mc.field_1724;
        if (ScanConfig.onlyWhenElytra && !player.method_6128()) return;
        if (ScanConfig.targetBlocks.isEmpty())     return;

        String worldName = resolveWorldName(mc);
        if (!worldName.equals(lastWorld)) resetForWorld(worldName);

        class_1923 pc = new class_1923(player.method_24515());
        int r = ScanConfig.scanRadius;

        for (int cx = pc.field_9181 - r; cx <= pc.field_9181 + r; cx++) {
            for (int cz = pc.field_9180 - r; cz <= pc.field_9180 + r; cz++) {
                if (!mc.field_1687.method_2935().method_12123(cx, cz)) continue;
                long key = class_1923.method_8331(cx, cz);

                if (ScanConfig.bypassAntiXray) {
                    int distX = Math.abs(cx - pc.field_9181);
                    int distZ = Math.abs(cz - pc.field_9180);
                    boolean isClose = distX <= BYPASS_CLOSE_CHUNKS
                                   && distZ <= BYPASS_CLOSE_CHUNKS;

                    if (isClose) {
                        if (!scannedClose.contains(key)) {
                            // Запоминаем время первого появления в зоне
                            chunkCloseTime.putIfAbsent(key, System.currentTimeMillis());
                            // Сканируем только если прошла задержка
                            long closeTime = chunkCloseTime.get(key);
                            if (System.currentTimeMillis() - closeTime >= BYPASS_DELAY_MS) {
                                scannedClose.add(key);
                                scannedChunks.add(key);
                                scanQueue.add(new class_1923(cx, cz));
                                chunkCloseTime.remove(key);
                            }
                        }
                    } else {
                        // Улетели — сброс, при следующем сближении заново отсчитаем задержку
                        scannedClose.remove(key);
                        chunkCloseTime.remove(key);
                    }
                } else {
                    if (!scannedChunks.contains(key)) {
                        scannedChunks.add(key);
                        scanQueue.add(new class_1923(cx, cz));
                    }
                }
            }
        }

        for (int i = 0; i < 2 && !scanQueue.isEmpty(); i++) {
            class_1923 cp = scanQueue.poll();
            if (cp != null) processChunk(mc.field_1687, cp);
        }
    }

    // ── сканирование чанка ───────────────────────────────────────────────────
    private static void processChunk(class_1937 world, class_1923 cp) {
        class_2818 chunk = world.method_8497(cp.field_9181, cp.field_9180);
        int baseX   = cp.method_8326();
        int baseZ   = cp.method_8328();
        int bottomY = world.method_31607();
        int topY    = world.method_31600();

        List<FoundBlock> newBlocks = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bottomY; y < topY; y++) {
                    class_2338 pos = new class_2338(baseX + x, y, baseZ + z);
                    if (foundSet.contains(pos)) continue;

                    class_2680 state = chunk.method_8320(pos);
                    if (state.method_26215()) continue;

                    String id = class_7923.field_41175.method_10221(state.method_26204()).toString();
                    if (!ScanConfig.targetBlocks.contains(id)) continue;

                    String label = findTextAtBlock(world, pos);

                    // Режим "только с текстом" — пропустить блоки без надписи
                    if (ScanConfig.prioritizeText && label == null) continue;

                    foundSet.add(pos.method_10062());
                    newBlocks.add(new FoundBlock(pos.method_10062(), id, label));
                    totalFound++;
                    if (label != null) textFound++;
                }
            }
        }

        chunksScanned++;

        if (!newBlocks.isEmpty()) {
            writeToLog(newBlocks, world);
            long withText = newBlocks.stream().filter(b -> b.label != null).count();
            flashMsg = "+" + newBlocks.size() + " блок(ов)"
                    + (withText > 0 ? "  §e(" + withText + " с текстом)" : "");
            flashUntil = System.currentTimeMillis() + 3500L;
        }
    }

    // ── поиск текста у/над блоком ─────────────────────────────────────────────
    private static String findTextAtBlock(class_1937 world, class_2338 pos) {
        // 1. CustomName блок-энтити через NBT (работает для любого блок-энтити,
        //    не зависит от маппингов Nameable)
        class_2586 be = world.method_8321(pos);
        if (be != null) {
            net.minecraft.class_2487 nbt = be.method_38244();
            if (nbt.method_10545("CustomName")) {
                try {
                    class_2561 t = class_2561.class_2562.method_10877(nbt.method_10558("CustomName"));
                    if (t != null) {
                        String s = strip(t.getString());
                        if (!s.isBlank()) return s;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. TextDisplay энтити над блоком (getDisplayName() — публичный метод Entity)
        class_238 box = new class_238(
                pos.method_10263() - 1, pos.method_10264() - 0.5, pos.method_10260() - 1,
                pos.method_10263() + 2, pos.method_10264() + 3.5,  pos.method_10260() + 2
        );
        List<class_1297> entities = world.method_8390(
                class_1297.class, box,
                e -> e instanceof class_8113.class_8123);
        if (!entities.isEmpty()) {
            String s = strip(entities.get(0).method_5476().getString());
            if (!s.isBlank()) return s;
        }

        return null;
    }

    private static String strip(String s) {
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    // ── запись в лог ─────────────────────────────────────────────────────────
    private static void writeToLog(List<FoundBlock> blocks, class_1937 world) {
        if (logWriter == null) return;

        class_310 mc = class_310.method_1551();
        String ts  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String dim = world.method_27983().method_29177().toString();
        String pXYZ = mc.field_1724 != null
                ? String.format("%.0f / %.0f / %.0f",
                  mc.field_1724.method_23317(), mc.field_1724.method_23318(), mc.field_1724.method_23321()) : "?";

        for (FoundBlock b : blocks) {
            logWriter.printf("[%s] [%s]  %-40s  X=%-6d  Y=%-4d  Z=%-6d  (игрок: %s)%n",
                    ts, dim, b.blockId,
                    b.pos.method_10263(), b.pos.method_10264(), b.pos.method_10260(), pXYZ);
            if (b.label != null) {
                logWriter.printf("         >>> Текст: \"%s\"%n", b.label);
            }
        }
        logWriter.flush();
    }

    private record FoundBlock(class_2338 pos, String blockId, String label) {}

    // ── сброс/открытие лога ──────────────────────────────────────────────────
    public static void resetForWorld(String worldName) {
        lastWorld = worldName;
        scannedChunks.clear();
        scannedClose.clear();
        chunkCloseTime.clear();
        scanQueue.clear();
        foundSet.clear();
        totalFound = chunksScanned = textFound = 0;

        if (logWriter != null) {
            logWriter.println("\n=== Сессия завершена ===");
            logWriter.close();
            logWriter = null;
        }
        openNewLog(worldName);
    }

    private static void openNewLog(String worldName) {
        try {
            LOG_DIR.toFile().mkdirs();
            String safe = worldName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            currentLogName = safe + "_" + ts + ".txt";
            logWriter = new PrintWriter(
                    new FileWriter(LOG_DIR.resolve(currentLogName).toFile(), true), true);

            logWriter.println("╔══════════════════════════════════════════════════════════════╗");
            logWriter.println("║                     ElytraScan  v1.0                        ║");
            logWriter.println("╚══════════════════════════════════════════════════════════════╝");
            logWriter.println("Мир:              " + worldName);
            logWriter.println("Начало:           " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            logWriter.println("Цели:             " + String.join(", ", ScanConfig.targetBlocks));
            logWriter.println("Обход антиксрея:  " + (ScanConfig.bypassAntiXray ? "ВКЛ" : "выкл"));
            logWriter.println("Только с текстом: " + (ScanConfig.prioritizeText ? "ВКЛ" : "выкл"));
            logWriter.println("─".repeat(70));
            logWriter.flush();
        } catch (Exception e) {
            ElytraScanMod.LOGGER.error("ElytraScan: не удалось открыть лог-файл", e);
        }
    }

    public static void restartSession() {
        class_310 mc = class_310.method_1551();
        if (mc.field_1687 != null) resetForWorld(resolveWorldName(mc));
    }

    public static String resolveWorldName(class_310 mc) {
        if (mc.method_1496() && mc.method_1576() != null)
            return mc.method_1576().method_27728().method_150();
        if (mc.method_1558() != null)
            return mc.method_1558().field_3761;
        return "unknown";
    }

    public static String getLogPath() {
        if (currentLogName.isEmpty()) return "—  (не активен)";
        return LOG_DIR.resolve(currentLogName).toAbsolutePath().toString();
    }

    // ── HUD ──────────────────────────────────────────────────────────────────
    public static void renderHud(class_332 ctx) {
        if (!ScanConfig.scanEnabled) return;
        class_310 mc = class_310.method_1551();
        if (mc.field_1690.field_1842 || mc.field_1755 != null) return;

        boolean active = mc.field_1724 != null
                && (!ScanConfig.onlyWhenElytra || mc.field_1724.method_6128());

        int sw = mc.method_22683().method_4486();
        int sh = mc.method_22683().method_4502();
        int x = 4, y = sh - 31;

        String statusLine = active
                ? "§a▶ §fСкан" + (scanQueue.isEmpty()
                    ? " §7(в ожидании)" : " §e(" + scanQueue.size() + " ч.)")
                : "§7⏸ §7Скан (нет элитр)";
        String foundLine = "§7Найдено: §b" + totalFound
                + (textFound > 0 ? " §7(текст: §e" + textFound + "§7)" : "")
                + "  §7Чанков: §e" + chunksScanned;
        String modeLine = (ScanConfig.bypassAntiXray ? "§b[обход ксрей]" : "")
                + (ScanConfig.prioritizeText ? " §e[только текст]" : "");

        int hudH = modeLine.isBlank() ? 20 : 29;
        ctx.method_25294(x - 2, y - 2, x + 215, y + hudH, 0x99000000);
        ctx.method_25303(mc.field_1772, statusLine, x, y,     0xFFFFFF);
        ctx.method_25303(mc.field_1772, foundLine,  x, y + 9, 0xFFFFFF);
        if (!modeLine.isBlank())
            ctx.method_25303(mc.field_1772, modeLine, x, y + 18, 0xFFFFFF);

        long now = System.currentTimeMillis();
        if (now < flashUntil) {
            long rem = flashUntil - now;
            float alpha = rem < 700 ? (rem / 700f) : 1f;
            int a = (int)(alpha * 0xBB);
            int fw = mc.field_1772.method_1727("§a✔ " + flashMsg) + 10;
            int fx = (sw - fw) / 2, fy = sh / 2 + 30;
            ctx.method_25294(fx - 4, fy - 2, fx + fw, fy + 11, (a << 24) | 0x002200);
            ctx.method_25300(mc.field_1772, "§a✔ " + flashMsg,
                    sw / 2, fy, 0x55FF55);
        }
    }
}

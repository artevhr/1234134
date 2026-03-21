package com.elytrascan;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Ядро сканера.
 * Логика: при каждом тике, если игрок летит на элитрах (или это отключено),
 * мод добавляет незасканированные загруженные чанки в очередь и
 * обрабатывает по 2 чанка за тик. Каждый чанк сканируется полностью
 * по всем Y (от дна мира до потолка), поэтому блоки под землёй
 * не пропускаются.
 */
public class BlockScanner {

    // ── состояние ────────────────────────────────────────────────────────────
    private static final Set<Long>       scannedChunks = new HashSet<>();
    private static final Queue<ChunkPos> scanQueue     = new ArrayDeque<>();
    private static final Set<BlockPos>   foundSet      = new HashSet<>();

    public static volatile int  totalFound    = 0;
    public static volatile int  chunksScanned = 0;
    public static volatile String lastWorld   = "";

    // ── flash-уведомление ────────────────────────────────────────────────────
    private static String flashMsg  = "";
    private static long   flashUntil = 0L;

    // ── лог-файл ─────────────────────────────────────────────────────────────
    private static final Path LOG_DIR =
            FabricLoader.getInstance().getGameDir().resolve("elytrascan_logs");
    private static PrintWriter logWriter     = null;
    private static String      currentLogName = "";

    // ── главный тик ──────────────────────────────────────────────────────────
    public static void tick(MinecraftClient mc) {
        if (!ScanConfig.scanEnabled)          return;
        if (mc.player == null || mc.world == null) return;

        PlayerEntity player = mc.player;
        if (ScanConfig.onlyWhenElytra && !player.isFallFlying()) return;
        if (ScanConfig.targetBlocks.isEmpty()) return;

        // Смена мира → сброс состояния
        String worldName = resolveWorldName(mc);
        if (!worldName.equals(lastWorld)) {
            resetForWorld(worldName);
        }

        // Добавляем новые чанки вокруг игрока
        ChunkPos pc = new ChunkPos(player.getBlockPos());
        int r = ScanConfig.scanRadius;
        for (int cx = pc.x - r; cx <= pc.x + r; cx++) {
            for (int cz = pc.z - r; cz <= pc.z + r; cz++) {
                long key = ChunkPos.toLong(cx, cz);
                if (!scannedChunks.contains(key)
                        && mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    scannedChunks.add(key);
                    scanQueue.add(new ChunkPos(cx, cz));
                }
            }
        }

        // Обрабатываем 2 чанка за тик (≈ 2 × 98 304 getBlockState)
        for (int i = 0; i < 2 && !scanQueue.isEmpty(); i++) {
            ChunkPos cp = scanQueue.poll();
            if (cp != null) processChunk(mc.world, cp);
        }
    }

    // ── сканирование одного чанка ────────────────────────────────────────────
    private static void processChunk(World world, ChunkPos cp) {
        WorldChunk chunk = world.getChunk(cp.x, cp.z);
        int baseX  = cp.getStartX();
        int baseZ  = cp.getStartZ();
        int bottomY = world.getBottomY();
        int topY    = world.getTopY();

        List<BlockPos> newPositions = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bottomY; y < topY; y++) {
                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                    if (foundSet.contains(pos)) continue;

                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;

                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (ScanConfig.targetBlocks.contains(id)) {
                        foundSet.add(pos.toImmutable());
                        newPositions.add(pos.toImmutable());
                        totalFound++;
                    }
                }
            }
        }

        chunksScanned++;

        if (!newPositions.isEmpty()) {
            writeToLog(newPositions, world);
            flashMsg   = "+" + newPositions.size() + " блок(ов) найдено!";
            flashUntil = System.currentTimeMillis() + 3500L;
        }
    }

    // ── запись в файл ────────────────────────────────────────────────────────
    private static void writeToLog(List<BlockPos> positions, World world) {
        if (logWriter == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        String ts  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String dim = world.getRegistryKey().getValue().toString();
        String playerXYZ = mc.player != null
                ? String.format("%.0f / %.0f / %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ())
                : "?";

        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            String id = Registries.BLOCK.getId(state.getBlock()).toString();
            logWriter.printf("[%s] [%s]  %-40s  X=%-6d  Y=%-4d  Z=%-6d  (игрок: %s)%n",
                    ts, dim, id,
                    pos.getX(), pos.getY(), pos.getZ(),
                    playerXYZ);
        }
        logWriter.flush();
    }

    // ── сброс при смене мира ─────────────────────────────────────────────────
    public static void resetForWorld(String worldName) {
        lastWorld = worldName;
        scannedChunks.clear();
        scanQueue.clear();
        foundSet.clear();
        totalFound    = 0;
        chunksScanned = 0;

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
            File file = LOG_DIR.resolve(currentLogName).toFile();
            logWriter = new PrintWriter(new FileWriter(file, true), true);

            logWriter.println("╔══════════════════════════════════════════════════════════════╗");
            logWriter.println("║                     ElytraScan  v1.0                        ║");
            logWriter.println("╚══════════════════════════════════════════════════════════════╝");
            logWriter.println("Мир:    " + worldName);
            logWriter.println("Начало: " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            logWriter.println("Цели:   " + String.join(", ", ScanConfig.targetBlocks));
            logWriter.println("─".repeat(70));
            logWriter.flush();
        } catch (Exception e) {
            ElytraScanMod.LOGGER.error("ElytraScan: не удалось открыть лог-файл", e);
        }
    }

    /** Вызывается из GUI при изменении настроек (новый список блоков и т.д.) */
    public static void restartSession() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            resetForWorld(resolveWorldName(mc));
        }
    }

    // ── утилиты ──────────────────────────────────────────────────────────────
    public static String resolveWorldName(MinecraftClient mc) {
        if (mc.isIntegratedServerRunning() && mc.getServer() != null) {
            return mc.getServer().getSaveProperties().getLevelName();
        }
        if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().address;
        }
        return "unknown";
    }

    public static String getLogPath() {
        if (currentLogName.isEmpty()) return "—  (не активен)";
        return LOG_DIR.resolve(currentLogName).toAbsolutePath().toString();
    }

    // ── HUD-рендер ────────────────────────────────────────────────────────────
    public static void renderHud(DrawContext ctx) {
        if (!ScanConfig.scanEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen != null) return;

        boolean active = mc.player != null
                && (!ScanConfig.onlyWhenElytra || mc.player.isFallFlying());

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // ── строка статуса (левый нижний угол) ──────────────────────────────
        int x = 4, y = sh - 22;
        String statusLine = active
                ? "§a▶ §fСкан" + (scanQueue.isEmpty() ? " §7(в ожидании)" : " §e(" + scanQueue.size() + " ч.)")
                : "§7⏸ §7Скан (нет элитр)";
        String foundLine = "§7Найдено: §b" + totalFound + "  §7Чанков: §e" + chunksScanned;

        ctx.fill(x - 2, y - 2, x + 170, y + 18, 0x99000000);
        ctx.drawTextWithShadow(mc.textRenderer, statusLine, x, y,     0xFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, foundLine,  x, y + 9, 0xFFFFFF);

        // ── flash-уведомление ────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (now < flashUntil) {
            long remaining = flashUntil - now;
            float alpha = remaining < 700 ? (remaining / 700f) : 1f;
            int a = (int)(alpha * 0xBB);
            int bg = (a << 24) | 0x002200;

            int fw = mc.textRenderer.getWidth("§a✔ " + flashMsg) + 10;
            int fx = (sw - fw) / 2;
            int fy = sh / 2 + 30;
            ctx.fill(fx - 4, fy - 2, fx + fw, fy + 11, bg);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "§a✔ " + flashMsg,
                    sw / 2, fy, 0x55FF55);
        }
    }
}

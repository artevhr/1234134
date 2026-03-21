package com.elytrascan;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BlockScanner {

    private static final Set<Long>       scannedChunks = new HashSet<>();
    private static final Set<Long>       scannedClose  = new HashSet<>();
    private static final Queue<ChunkPos> scanQueue     = new ArrayDeque<>();
    private static final Set<BlockPos>   foundSet      = new HashSet<>();

    // Для ESP: позиция → blockId (синхронизированная)
    public static final Map<BlockPos, String> foundPositions =
            Collections.synchronizedMap(new LinkedHashMap<>());

    // Порог "вплотную" для обхода антиксрея — 2 чанка по XZ (32 блока).
    // Большинство антиксрей-плагинов раскрывают блоки в этом радиусе.
    private static final int BYPASS_CLOSE_CHUNKS = 2;

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
    public static void tick(MinecraftClient mc) {
        if (!ScanConfig.scanEnabled)               return;
        if (mc.player == null || mc.world == null) return;

        PlayerEntity player = mc.player;
        if (ScanConfig.onlyWhenElytra && !player.isFallFlying()) return;
        if (ScanConfig.targetBlocks.isEmpty())     return;

        String worldName = resolveWorldName(mc);
        if (!worldName.equals(lastWorld)) resetForWorld(worldName);

        ChunkPos pc = new ChunkPos(player.getBlockPos());
        int r = ScanConfig.scanRadius;

        for (int cx = pc.x - r; cx <= pc.x + r; cx++) {
            for (int cz = pc.z - r; cz <= pc.z + r; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                long key = ChunkPos.toLong(cx, cz);

                if (ScanConfig.bypassAntiXray) {
                    // При полёте на элитрах игрок пролетает над каждым чанком
                    // на расстоянии ≤2 по XZ — антиксрей к этому моменту
                    // уже раскрыл блоки. Дальние чанки сбрасываем.
                    int distX = Math.abs(cx - pc.x);
                    int distZ = Math.abs(cz - pc.z);
                    boolean isClose = distX <= BYPASS_CLOSE_CHUNKS
                                   && distZ <= BYPASS_CLOSE_CHUNKS;

                    if (isClose && !scannedClose.contains(key)) {
                        scannedClose.add(key);
                        scannedChunks.add(key);
                        scanQueue.add(new ChunkPos(cx, cz));
                    } else if (!isClose) {
                        scannedClose.remove(key); // улетели — сброс
                    }
                } else {
                    if (!scannedChunks.contains(key)) {
                        scannedChunks.add(key);
                        scanQueue.add(new ChunkPos(cx, cz));
                    }
                }
            }
        }

        for (int i = 0; i < 2 && !scanQueue.isEmpty(); i++) {
            ChunkPos cp = scanQueue.poll();
            if (cp != null) processChunk(mc.world, cp);
        }
    }

    // ── сканирование чанка ───────────────────────────────────────────────────
    private static void processChunk(World world, ChunkPos cp) {
        WorldChunk chunk = world.getChunk(cp.x, cp.z);
        int baseX   = cp.getStartX();
        int baseZ   = cp.getStartZ();
        int bottomY = world.getBottomY();
        int topY    = world.getTopY();

        List<FoundBlock> newBlocks = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bottomY; y < topY; y++) {
                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                    if (foundSet.contains(pos)) continue;

                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;

                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (!ScanConfig.targetBlocks.contains(id)) continue;

                    String label = ScanConfig.prioritizeText
                            ? findTextAtBlock(world, pos)
                            : null;

                    // Режим "также с текстом" выключен — просто пишем блок без поиска текста
                    // Режим включён — ищем текст и пишем его рядом с блоком (блок пишется всегда)

                    foundSet.add(pos.toImmutable());
                    foundPositions.put(pos.toImmutable(), id);
                    newBlocks.add(new FoundBlock(pos.toImmutable(), id, label));
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
    private static String findTextAtBlock(World world, BlockPos pos) {
        // 1. CustomName блок-энтити через NBT (работает для любого блок-энтити,
        //    не зависит от маппингов Nameable)
        BlockEntity be = world.getBlockEntity(pos);
        if (be != null) {
            net.minecraft.nbt.NbtCompound nbt = be.createNbt();
            if (nbt.contains("CustomName")) {
                try {
                    Text t = Text.Serializer.fromJson(nbt.getString("CustomName"));
                    if (t != null) {
                        String s = strip(t.getString());
                        if (!s.isBlank()) return s;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. TextDisplay энтити над блоком (getDisplayName() — публичный метод Entity)
        Box box = new Box(
                pos.getX() - 1, pos.getY() - 0.5, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 3.5,  pos.getZ() + 2
        );
        List<Entity> entities = world.getEntitiesByClass(
                Entity.class, box,
                e -> e instanceof DisplayEntity.TextDisplayEntity);
        if (!entities.isEmpty()) {
            String s = strip(entities.get(0).getDisplayName().getString());
            if (!s.isBlank()) return s;
        }

        return null;
    }

    private static String strip(String s) {
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    // ── запись в лог ─────────────────────────────────────────────────────────
    private static void writeToLog(List<FoundBlock> blocks, World world) {
        if (logWriter == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        String ts  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String dim = world.getRegistryKey().getValue().toString();
        String pXYZ = mc.player != null
                ? String.format("%.0f / %.0f / %.0f",
                  mc.player.getX(), mc.player.getY(), mc.player.getZ()) : "?";

        for (FoundBlock b : blocks) {
            logWriter.printf("[%s] [%s]  %-40s  X=%-6d  Y=%-4d  Z=%-6d  (игрок: %s)%n",
                    ts, dim, b.blockId,
                    b.pos.getX(), b.pos.getY(), b.pos.getZ(), pXYZ);
            if (b.label != null) {
                logWriter.printf("         >>> Текст: \"%s\"%n", b.label);
            }
        }
        logWriter.flush();
    }

    private record FoundBlock(BlockPos pos, String blockId, String label) {}

    // ── сброс/открытие лога ──────────────────────────────────────────────────
    public static void resetForWorld(String worldName) {
        lastWorld = worldName;
        scannedChunks.clear();
        scannedClose.clear();
        scanQueue.clear();
        foundSet.clear();
        foundPositions.clear();
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) resetForWorld(resolveWorldName(mc));
    }

    public static String resolveWorldName(MinecraftClient mc) {
        if (mc.isIntegratedServerRunning() && mc.getServer() != null)
            return mc.getServer().getSaveProperties().getLevelName();
        if (mc.getCurrentServerEntry() != null)
            return mc.getCurrentServerEntry().address;
        return "unknown";
    }

    public static String getLogPath() {
        if (currentLogName.isEmpty()) return "—  (не активен)";
        return LOG_DIR.resolve(currentLogName).toAbsolutePath().toString();
    }

    // ── HUD ──────────────────────────────────────────────────────────────────
    public static void renderHud(DrawContext ctx) {
        if (!ScanConfig.scanEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen != null) return;

        boolean active = mc.player != null
                && (!ScanConfig.onlyWhenElytra || mc.player.isFallFlying());

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
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
        ctx.fill(x - 2, y - 2, x + 215, y + hudH, 0x99000000);
        ctx.drawTextWithShadow(mc.textRenderer, statusLine, x, y,     0xFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, foundLine,  x, y + 9, 0xFFFFFF);
        if (!modeLine.isBlank())
            ctx.drawTextWithShadow(mc.textRenderer, modeLine, x, y + 18, 0xFFFFFF);

        long now = System.currentTimeMillis();
        if (now < flashUntil) {
            long rem = flashUntil - now;
            float alpha = rem < 700 ? (rem / 700f) : 1f;
            int a = (int)(alpha * 0xBB);
            int fw = mc.textRenderer.getWidth("§a✔ " + flashMsg) + 10;
            int fx = (sw - fw) / 2, fy = sh / 2 + 30;
            ctx.fill(fx - 4, fy - 2, fx + fw, fy + 11, (a << 24) | 0x002200);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "§a✔ " + flashMsg,
                    sw / 2, fy, 0x55FF55);
        }
    }
}

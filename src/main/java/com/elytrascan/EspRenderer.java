package com.elytrascan;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.*;

/**
 * ESP — рисует цветные рамки вокруг найденных блоков сквозь стены.
 *
 * Рендерится через WorldRenderLastCallback (3D мир).
 * Глубина отключается → видно через любые блоки.
 * Дальность отображения ограничена чтобы не лагало.
 */
public class EspRenderer {

    // Максимальная дистанция от игрока для отображения ESP (блоков)
    public static final int MAX_DIST = 128;

    // Цвета по типу блока (ARGB без альфа-канала — альфа задаём отдельно)
    private static final Map<String, int[]> BLOCK_COLORS = new LinkedHashMap<>();

    static {
        // Формат: R, G, B (0-255)
        BLOCK_COLORS.put("minecraft:chest",           new int[]{70,  130, 255}); // синий
        BLOCK_COLORS.put("minecraft:barrel",           new int[]{70,  130, 255}); // синий
        BLOCK_COLORS.put("minecraft:ender_chest",      new int[]{140, 80,  255}); // фиолетовый
        BLOCK_COLORS.put("minecraft:shulker_box",      new int[]{180, 100, 255}); // фиолетовый
        BLOCK_COLORS.put("minecraft:spawner",          new int[]{255, 60,  60});  // красный
        BLOCK_COLORS.put("minecraft:ancient_debris",   new int[]{255, 160, 40});  // оранжевый
        BLOCK_COLORS.put("minecraft:diamond_ore",      new int[]{80,  220, 220}); // циан
        BLOCK_COLORS.put("minecraft:deepslate_diamond_ore", new int[]{80, 200, 200});
        BLOCK_COLORS.put("minecraft:emerald_ore",      new int[]{80,  255, 100}); // зелёный
        BLOCK_COLORS.put("minecraft:deepslate_emerald_ore", new int[]{80, 220, 100});
        BLOCK_COLORS.put("minecraft:nether_gold_ore",  new int[]{255, 200, 40});  // золотой
        BLOCK_COLORS.put("minecraft:nether_quartz_ore",new int[]{220, 220, 220}); // белый
        BLOCK_COLORS.put("minecraft:obsidian",         new int[]{150, 80,  255}); // фиолетовый
    }

    // Дефолтный цвет для неизвестных блоков
    private static final int[] DEFAULT_COLOR = {255, 255, 255};

    public static void render(MatrixStack ms, Camera camera) {
        if (!ScanConfig.scanEnabled || !ScanConfig.espEnabled) return;
        if (BlockScanner.foundPositions.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Vec3d camPos = camera.getPos();

        // Копируем список чтобы не было ConcurrentModificationException
        List<Map.Entry<BlockPos, String>> snapshot;
        synchronized (BlockScanner.foundPositions) {
            snapshot = new ArrayList<>(BlockScanner.foundPositions.entrySet());
        }

        // Настройка OpenGL
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();       // сквозь стены
        RenderSystem.disableCull();
        RenderSystem.lineWidth(1.5f);
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        ms.push();
        // Смещаем в позицию камеры
        ms.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = ms.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        for (Map.Entry<BlockPos, String> entry : snapshot) {
            BlockPos pos    = entry.getKey();
            String blockId  = entry.getValue();

            // Проверяем дистанцию
            double dx = pos.getX() + 0.5 - camPos.x;
            double dy = pos.getY() + 0.5 - camPos.y;
            double dz = pos.getZ() + 0.5 - camPos.z;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist > MAX_DIST) continue;

            // Альфа зависит от расстояния: близко = яркий, далеко = прозрачный
            float alpha = (float) Math.max(0.15, 1.0 - dist / MAX_DIST);

            int[] rgb = BLOCK_COLORS.getOrDefault(blockId, DEFAULT_COLOR);
            float r = rgb[0] / 255f;
            float g = rgb[1] / 255f;
            float b = rgb[2] / 255f;

            float x1 = pos.getX() + 0.01f;
            float y1 = pos.getY() + 0.01f;
            float z1 = pos.getZ() + 0.01f;
            float x2 = pos.getX() + 0.99f;
            float y2 = pos.getY() + 0.99f;
            float z2 = pos.getZ() + 0.99f;

            drawBox(buf, matrix, x1, y1, z1, x2, y2, z2, r, g, b, alpha);
        }

        tess.draw();

        ms.pop();

        // Восстанавливаем состояние
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    /**
     * Рисует 12 рёбер куба (wireframe box)
     */
    private static void drawBox(BufferBuilder buf, Matrix4f m,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        // Нижняя грань
        line(buf, m, x1,y1,z1, x2,y1,z1, r,g,b,a);
        line(buf, m, x2,y1,z1, x2,y1,z2, r,g,b,a);
        line(buf, m, x2,y1,z2, x1,y1,z2, r,g,b,a);
        line(buf, m, x1,y1,z2, x1,y1,z1, r,g,b,a);
        // Верхняя грань
        line(buf, m, x1,y2,z1, x2,y2,z1, r,g,b,a);
        line(buf, m, x2,y2,z1, x2,y2,z2, r,g,b,a);
        line(buf, m, x2,y2,z2, x1,y2,z2, r,g,b,a);
        line(buf, m, x1,y2,z2, x1,y2,z1, r,g,b,a);
        // Вертикальные рёбра
        line(buf, m, x1,y1,z1, x1,y2,z1, r,g,b,a);
        line(buf, m, x2,y1,z1, x2,y2,z1, r,g,b,a);
        line(buf, m, x2,y1,z2, x2,y2,z2, r,g,b,a);
        line(buf, m, x1,y1,z2, x1,y2,z2, r,g,b,a);
    }

    private static void line(BufferBuilder buf, Matrix4f m,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float r, float g, float b, float a) {
        // Нормаль для LINES формата
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len == 0) return;
        nx /= len; ny /= len; nz /= len;

        buf.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz).next();
        buf.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz).next();
    }
}

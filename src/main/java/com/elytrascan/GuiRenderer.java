package com.elytrascan;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * Утилита для рисования кастомных UI-элементов через Tessellator.
 * Поддерживает скруглённые прямоугольники, обводки, тени, пилюли.
 */
public class GuiRenderer {

    // ── цвета UI ─────────────────────────────────────────────────────────────
    public static final int COL_BG        = 0xFFFAFAFA; // фон панели
    public static final int COL_SURFACE   = 0xFFFFFFFF; // поверхности (список, поле)
    public static final int COL_BLACK     = 0xFF0D0D0D; // текст, кнопки, вкл
    public static final int COL_GRAY_DARK = 0xFF555555; // вторичный текст
    public static final int COL_GRAY_MID  = 0xFFAAAAAA; // неактивные элементы
    public static final int COL_GRAY_LITE = 0xFFE0E0E0; // разделители, рамки
    public static final int COL_HOVER     = 0xFF2A2A2A; // кнопка при наведении
    public static final int COL_SELECTED  = 0xFFEEEEEE; // выбранная строка списка
    public static final int COL_ACCENT    = 0xFF1A73E8; // акцент (мало где)
    public static final int COL_GREEN     = 0xFF1DB954; // активный статус
    public static final int COL_RED       = 0xFFE53935; // неактивный статус
    public static final int COL_SHADOW    = 0x22000000; // тень панели

    // ── основные методы ───────────────────────────────────────────────────────

    /** Залитый прямоугольник (ARGB) */
    public static void fillRect(MatrixStack ms, float x, float y, float w, float h, int color) {
        fillRoundedRect(ms, x, y, w, h, 0, color);
    }

    /** Залитый прямоугольник со скруглёнными углами */
    public static void fillRoundedRect(MatrixStack ms, float x, float y, float w, float h,
                                        float r, int color) {
        float a  = (color >> 24 & 0xFF) / 255f;
        float cr = (color >> 16 & 0xFF) / 255f;
        float cg = (color >> 8  & 0xFF) / 255f;
        float cb = (color       & 0xFF) / 255f;

        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // Центральный прямоугольник
        quad(buf, m, x + r, y,     w - 2*r, h,     cr, cg, cb, a);
        // Левый прямоугольник
        quad(buf, m, x,     y + r, r,       h-2*r, cr, cg, cb, a);
        // Правый прямоугольник
        quad(buf, m, x+w-r, y + r, r,       h-2*r, cr, cg, cb, a);

        // 4 угла
        if (r > 0) {
            arc(m, x+r,     y+r,   r, 180, 270, cr, cg, cb, a);
            arc(m, x+w-r,   y+r,   r, 270, 360, cr, cg, cb, a);
            arc(m, x+w-r,   y+h-r, r, 0,   90,  cr, cg, cb, a);
            arc(m, x+r,     y+h-r, r, 90,  180, cr, cg, cb, a);
        }
    }

    /** Обводка скруглённого прямоугольника */
    public static void drawRoundedRectOutline(MatrixStack ms, float x, float y, float w, float h,
                                               float r, float t, int color) {
        float a  = (color >> 24 & 0xFF) / 255f;
        float cr = (color >> 16 & 0xFF) / 255f;
        float cg = (color >> 8  & 0xFF) / 255f;
        float cb = (color       & 0xFF) / 255f;

        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // Стороны
        quad(Tessellator.getInstance().getBuffer(), m, x+r,   y,     w-2*r, t,     cr,cg,cb,a); // top
        quad(Tessellator.getInstance().getBuffer(), m, x+r,   y+h-t, w-2*r, t,     cr,cg,cb,a); // bottom
        quad(Tessellator.getInstance().getBuffer(), m, x,     y+r,   t,     h-2*r, cr,cg,cb,a); // left
        quad(Tessellator.getInstance().getBuffer(), m, x+w-t, y+r,   t,     h-2*r, cr,cg,cb,a); // right

        // Угловые дуги (внешняя - внутренняя)
        if (r > 0) {
            arcRing(m, x+r,   y+r,   r-t, r, 180, 270, cr,cg,cb,a);
            arcRing(m, x+w-r, y+r,   r-t, r, 270, 360, cr,cg,cb,a);
            arcRing(m, x+w-r, y+h-r, r-t, r, 0,   90,  cr,cg,cb,a);
            arcRing(m, x+r,   y+h-r, r-t, r, 90,  180, cr,cg,cb,a);
        }
    }

    /**
     * Тень под панелью (несколько слоёв с убывающей прозрачностью)
     */
    public static void drawShadow(MatrixStack ms, float x, float y, float w, float h, float r) {
        for (int i = 6; i >= 1; i--) {
            int alpha = (int)(0x18 * (1f - i / 7f));
            int col = (alpha << 24) | 0x000000;
            fillRoundedRect(ms, x - i, y - i/2f, w + i*2, h + i*2, r + i, col);
        }
    }

    // ── примитивы ─────────────────────────────────────────────────────────────

    private static void quad(BufferBuilder buf, Matrix4f m,
                              float x, float y, float w, float h,
                              float r, float g, float b, float a) {
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(m, x,   y,   0).color(r,g,b,a).next();
        buf.vertex(m, x,   y+h, 0).color(r,g,b,a).next();
        buf.vertex(m, x+w, y+h, 0).color(r,g,b,a).next();
        buf.vertex(m, x+w, y,   0).color(r,g,b,a).next();
        Tessellator.getInstance().draw();
    }

    private static void arc(Matrix4f m, float cx, float cy, float radius,
                              float startDeg, float endDeg,
                              float r, float g, float b, float a) {
        int segs = Math.max(6, (int)(Math.abs(endDeg - startDeg) / 10));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(m, cx, cy, 0).color(r,g,b,a).next();
        for (int i = 0; i <= segs; i++) {
            double angle = Math.toRadians(startDeg + (endDeg - startDeg) * i / segs);
            buf.vertex(m,
                    cx + (float)(Math.cos(angle) * radius),
                    cy + (float)(Math.sin(angle) * radius), 0)
               .color(r,g,b,a).next();
        }
        tess.draw();
    }

    private static void arcRing(Matrix4f m, float cx, float cy,
                                  float innerR, float outerR,
                                  float startDeg, float endDeg,
                                  float r, float g, float b, float a) {
        int segs = Math.max(6, (int)(Math.abs(endDeg - startDeg) / 10));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double angle = Math.toRadians(startDeg + (endDeg - startDeg) * i / segs);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buf.vertex(m, cx + cos * outerR, cy + sin * outerR, 0).color(r,g,b,a).next();
            buf.vertex(m, cx + cos * innerR, cy + sin * innerR, 0).color(r,g,b,a).next();
        }
        tess.draw();
    }

    // ── готовые компоненты ───────────────────────────────────────────────────

    /**
     * Кнопка: чёрная залитая с белым текстом / инвертированная при hover
     */
    public static void drawButton(net.minecraft.client.gui.DrawContext ctx,
                                   net.minecraft.client.font.TextRenderer tr,
                                   float x, float y, float w, float h,
                                   String label, boolean hovered, boolean danger) {
        int bg = danger ? (hovered ? 0xFFB71C1C : COL_RED)
                        : (hovered ? COL_HOVER   : COL_BLACK);
        fillRoundedRect(ctx.getMatrices(), x, y, w, h, 4, bg);
        int lw = tr.getWidth(label);
        ctx.drawTextWithShadow(tr, label,
                (int)(x + (w - lw) / 2f), (int)(y + (h - 8) / 2f), 0xFFFFFF);
    }

    /**
     * Кнопка-призрак: белая с чёрной рамкой
     */
    public static void drawGhostButton(net.minecraft.client.gui.DrawContext ctx,
                                        net.minecraft.client.font.TextRenderer tr,
                                        float x, float y, float w, float h,
                                        String label, boolean hovered) {
        int bg = hovered ? 0xFFEEEEEE : 0xFFFFFFFF;
        fillRoundedRect(ctx.getMatrices(), x, y, w, h, 4, bg);
        drawRoundedRectOutline(ctx.getMatrices(), x, y, w, h, 4, 1, COL_GRAY_LITE);
        int lw = tr.getWidth(label);
        ctx.drawText(tr, label,
                (int)(x + (w - lw) / 2f), (int)(y + (h - 8) / 2f), COL_BLACK, false);
    }

    /**
     * Переключатель (toggle-пилюля): ON = чёрная с белым кружком справа,
     * OFF = светло-серая с тёмным кружком слева
     */
    public static void drawToggle(MatrixStack ms, float x, float y, boolean on) {
        float tw = 34, th = 16, r = 8;
        int track = on ? COL_BLACK : COL_GRAY_MID;
        fillRoundedRect(ms, x, y, tw, th, r, track);
        float kx = on ? x + tw - th + 2 : x + 2;
        float ky = y + 2;
        fillRoundedRect(ms, kx, ky, th - 4, th - 4, (th - 4) / 2f, 0xFFFFFFFF);
    }

    /**
     * Маленький тег-пресет (pill-кнопка)
     */
    public static void drawPresetPill(net.minecraft.client.gui.DrawContext ctx,
                                       net.minecraft.client.font.TextRenderer tr,
                                       float x, float y, String label,
                                       boolean hovered, boolean active) {
        float pw = tr.getWidth(label) + 12, ph = 16;
        int bg = active  ? COL_BLACK :
                 hovered ? 0xFFE0E0E0 : 0xFFF0F0F0;
        int fg = active  ? 0xFFFFFFFF : COL_GRAY_DARK;
        fillRoundedRect(ctx.getMatrices(), x, y, pw, ph, 8, bg);
        ctx.drawText(tr, label, (int)(x + 6), (int)(y + 4), fg, false);
    }

    public static float presetPillWidth(net.minecraft.client.font.TextRenderer tr, String label) {
        return tr.getWidth(label) + 12;
    }
}

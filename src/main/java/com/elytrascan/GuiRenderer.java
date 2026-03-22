package com.elytrascan;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class GuiRenderer {

    public static final int COL_BG        = 0xFFFAFAFA;
    public static final int COL_SURFACE   = 0xFFFFFFFF;
    public static final int COL_BLACK     = 0xFF0D0D0D;
    public static final int COL_GRAY_DARK = 0xFF555555;
    public static final int COL_GRAY_MID  = 0xFFAAAAAA;
    public static final int COL_GRAY_LITE = 0xFFE0E0E0;
    public static final int COL_HOVER     = 0xFF2A2A2A;
    public static final int COL_SELECTED  = 0xFFEEEEEE;
    public static final int COL_SHADOW    = 0x22000000;

    public static void fillRect(MatrixStack ms, float x, float y, float w, float h, int color) {
        fillRoundedRect(ms, x, y, w, h, 0, color);
    }

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

        quad(buf, m, x + r, y,     w - 2*r, h,     cr, cg, cb, a);
        quad(buf, m, x,     y + r, r,       h-2*r, cr, cg, cb, a);
        quad(buf, m, x+w-r, y + r, r,       h-2*r, cr, cg, cb, a);

        if (r > 0) {
            arc(m, x+r,     y+r,   r, 180, 270, cr, cg, cb, a);
            arc(m, x+w-r,   y+r,   r, 270, 360, cr, cg, cb, a);
            arc(m, x+w-r,   y+h-r, r, 0,   90,  cr, cg, cb, a);
            arc(m, x+r,     y+h-r, r, 90,  180, cr, cg, cb, a);
        }
    }

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

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        quad(buf, m, x+r,   y,     w-2*r, t,     cr,cg,cb,a);
        quad(buf, m, x+r,   y+h-t, w-2*r, t,     cr,cg,cb,a);
        quad(buf, m, x,     y+r,   t,     h-2*r, cr,cg,cb,a);
        quad(buf, m, x+w-t, y+r,   t,     h-2*r, cr,cg,cb,a);

        if (r > 0) {
            arcRing(m, x+r,   y+r,   r-t, r, 180, 270, cr,cg,cb,a);
            arcRing(m, x+w-r, y+r,   r-t, r, 270, 360, cr,cg,cb,a);
            arcRing(m, x+w-r, y+h-r, r-t, r, 0,   90,  cr,cg,cb,a);
            arcRing(m, x+r,   y+h-r, r-t, r, 90,  180, cr,cg,cb,a);
        }
    }

    public static void drawShadow(MatrixStack ms, float x, float y, float w, float h, float r) {
        for (int i = 6; i >= 1; i--) {
            int alpha = (int)(0x18 * (1f - i / 7f));
            fillRoundedRect(ms, x - i, y - i/2f, w + i*2, h + i*2, r + i, (alpha << 24));
        }
    }

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

    public static void drawButton(net.minecraft.client.gui.DrawContext ctx,
                                   net.minecraft.client.font.TextRenderer tr,
                                   float x, float y, float w, float h,
                                   String label, boolean hovered, boolean danger) {
        int bg = danger ? (hovered ? 0xFFB71C1C : 0xFFE53935)
                        : (hovered ? COL_HOVER   : COL_BLACK);
        fillRoundedRect(ctx.getMatrices(), x, y, w, h, 4, bg);
        int lw = tr.getWidth(label);
        ctx.drawTextWithShadow(tr, label,
                (int)(x + (w - lw) / 2f), (int)(y + (h - 8) / 2f), 0xFFFFFF);
    }

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

    public static void drawToggle(MatrixStack ms, float x, float y, boolean on) {
        float tw = 34, th = 16, r = 8;
        fillRoundedRect(ms, x, y, tw, th, r, on ? COL_BLACK : COL_GRAY_MID);
        float kx = on ? x + tw - th + 2 : x + 2;
        fillRoundedRect(ms, kx, y + 2, th - 4, th - 4, (th - 4) / 2f, 0xFFFFFFFF);
    }

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

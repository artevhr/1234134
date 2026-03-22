package com.elytrascan;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ScannerScreen extends Screen {

    private static final int W   = 340;
    private static final int H   = 500;
    private static final int R   = 8;
    private static final int LIST_ROWS = 6;
    private static final int ROW_H     = 16;

    private static final int Y_SETTINGS_LBL = 64;
    private static final int Y_RADIUS        = 74;
    private static final int Y_TGL_ELYTRA    = 102;
    private static final int Y_TGL_BYPASS    = 126;
    private static final int Y_TGL_TEXT      = 150;
    private static final int Y_SEP1          = 176;
    private static final int Y_LIST_LBL      = 184;
    private static final int Y_LIST          = 196;
    private static final int Y_INPUT         = 302;
    private static final int Y_BTNS          = 324;
    private static final int Y_SEP2          = 348;
    private static final int Y_PRE1          = 358;
    private static final int Y_TOGGLE        = 384;
    private static final int Y_LOG           = 460;

    private int px, py;
    private int    selectedIdx = -1;
    private int    scrollOff   = 0;
    private TextFieldWidget blockInput;

    private record Btn(int x, int y, int w, int h, String id) {}
    private final List<Btn> buttons = new ArrayList<>();
    private double mouseX, mouseY;

    private static final String BTN_TOGGLE      = "toggle";
    private static final String BTN_MINUS       = "minus";
    private static final String BTN_PLUS        = "plus";
    private static final String BTN_ADD         = "add";
    private static final String BTN_DEL         = "del";
    private static final String BTN_RESET       = "reset";
    private static final String BTN_CLOSE       = "close";
    private static final String BTN_TGL_ELYTRA  = "tgl_elytra";
    private static final String BTN_TGL_BYPASS  = "tgl_bypass";
    private static final String BTN_TGL_TEXT    = "tgl_text";

    public ScannerScreen() { super(Text.literal("ElytraScan")); }

    @Override
    protected void init() {
        px = (width  - W) / 2;
        py = (height - H) / 2;
        buttons.clear();

        blockInput = new TextFieldWidget(textRenderer,
                px + 12, py + Y_INPUT, 200, 18, Text.literal("Block ID"));
        blockInput.setMaxLength(250);
        blockInput.setPlaceholder(Text.literal("minecraft:chest"));
        blockInput.setEditableColor(0x0D0D0D);
        addDrawableChild(blockInput);

        reg(BTN_MINUS,      px + 12,           py + Y_RADIUS,     24, 20);
        reg(BTN_PLUS,       px + 94,           py + Y_RADIUS,     24, 20);
        reg(BTN_TGL_ELYTRA, px + W - 12 - 34, py + Y_TGL_ELYTRA, 34, 16);
        reg(BTN_TGL_BYPASS, px + W - 12 - 34, py + Y_TGL_BYPASS, 34, 16);
        reg(BTN_TGL_TEXT,   px + W - 12 - 34, py + Y_TGL_TEXT,   34, 16);
        reg(BTN_ADD,        px + 216, py + Y_INPUT,  112, 18);
        reg(BTN_DEL,        px + 12,  py + Y_BTNS,   100, 18);
        reg(BTN_RESET,      px + 118, py + Y_BTNS,   105, 18);
        reg(BTN_CLOSE,      px + 229, py + Y_BTNS,    99, 18);
        reg(BTN_TOGGLE,     px + 12,  py + Y_TOGGLE, W - 24, 26);

        // Серверные пресеты
        int presX = px + 12;
        for (String label : new String[]{"FanTime", "KubWorld"}) {
            int pw = (int) GuiRenderer.presetPillWidth(textRenderer, label);
            reg("server_" + label, presX, py + Y_PRE1, pw, 16);
            presX += pw + 5;
        }
    }

    private void reg(String id, int x, int y, int w, int h) {
        buttons.add(new Btn(x, y, w, h, id));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        mouseX = mx; mouseY = my;
        MatrixStack ms = ctx.getMatrices();

        renderBackground(ctx);
        GuiRenderer.drawShadow(ms, px, py, W, H, R);
        GuiRenderer.fillRoundedRect(ms, px, py, W, H, R, GuiRenderer.COL_BG);

        // Шапка
        GuiRenderer.fillRoundedRect(ms, px, py, W, 52, R, GuiRenderer.COL_BLACK);
        GuiRenderer.fillRect(ms, px, py + R, W, 52 - R, GuiRenderer.COL_BLACK);

        String dot = ScanConfig.scanEnabled ? "§a●" : "§c●";
        ctx.drawTextWithShadow(textRenderer, dot, px + 14, py + 11, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "ElytraScan", px + 26, py + 11, 0xFFFFFF);

        String stats = "Найдено: " + BlockScanner.totalFound
                + "   Чанков: " + BlockScanner.chunksScanned
                + (BlockScanner.textFound > 0 ? "   Текст: " + BlockScanner.textFound : "");
        ctx.drawTextWithShadow(textRenderer, "§7" + stats, px + 14, py + 28, 0xAAAAAA);

        String world = BlockScanner.lastWorld.isEmpty() ? "—" : BlockScanner.lastWorld;
        if (world.length() > 36) world = world.substring(0, 34) + "…";
        ctx.drawTextWithShadow(textRenderer, "§8" + world, px + 14, py + 39, 0x777777);

        // Настройки
        ctx.drawText(textRenderer, "§8НАСТРОЙКИ", px + 14, py + Y_SETTINGS_LBL, 0xAAAAAA, false);

        ctx.drawText(textRenderer, "Радиус сканирования", px + 14, py + Y_RADIUS - 11, GuiRenderer.COL_GRAY_DARK, false);
        GuiRenderer.drawButton(ctx, textRenderer, px + 12, py + Y_RADIUS, 24, 20, "−", isHov(BTN_MINUS), false);
        String radVal = ScanConfig.scanRadius + " чанк  ≈ " + (ScanConfig.scanRadius * 16) + " бл.";
        ctx.drawText(textRenderer, radVal, px + 42, py + Y_RADIUS + 6, GuiRenderer.COL_BLACK, false);
        GuiRenderer.drawButton(ctx, textRenderer, px + 94, py + Y_RADIUS, 24, 20, "+", isHov(BTN_PLUS), false);

        ctx.drawText(textRenderer, "Только при полёте на элитрах",
                px + 14, py + Y_TGL_ELYTRA + 4, GuiRenderer.COL_BLACK, false);
        GuiRenderer.drawToggle(ms, px + W - 12 - 34, py + Y_TGL_ELYTRA, ScanConfig.onlyWhenElytra);

        ctx.drawText(textRenderer, "Обход замены блоков",
                px + 14, py + Y_TGL_BYPASS + 2, GuiRenderer.COL_BLACK, false);
        ctx.drawText(textRenderer, "§7сканировать только вблизи",
                px + 14, py + Y_TGL_BYPASS + 11, GuiRenderer.COL_GRAY_MID, false);
        GuiRenderer.drawToggle(ms, px + W - 12 - 34, py + Y_TGL_BYPASS, ScanConfig.bypassAntiXray);

        ctx.drawText(textRenderer, "Также искать текст у блока",
                px + 14, py + Y_TGL_TEXT + 4, GuiRenderer.COL_BLACK, false);
        GuiRenderer.drawToggle(ms, px + W - 12 - 34, py + Y_TGL_TEXT, ScanConfig.prioritizeText);

        GuiRenderer.fillRect(ms, px + 12, py + Y_SEP1, W - 24, 1, GuiRenderer.COL_GRAY_LITE);

        // Список
        ctx.drawText(textRenderer, "§8ЦЕЛЕВЫЕ БЛОКИ", px + 14, py + Y_LIST_LBL, 0xAAAAAA, false);
        int cnt = ScanConfig.targetBlocks.size();
        {
            int bx = px + 14 + textRenderer.getWidth("ЦЕЛЕВЫЕ БЛОКИ") + 8;
            String cs = String.valueOf(cnt);
            int bw = textRenderer.getWidth(cs) + 10;
            GuiRenderer.fillRoundedRect(ms, bx, py + Y_LIST_LBL - 2, bw, 13, 6,
                    cnt > 0 ? GuiRenderer.COL_BLACK : GuiRenderer.COL_GRAY_MID);
            ctx.drawText(textRenderer, cs,
                    bx + (bw - textRenderer.getWidth(cs)) / 2,
                    py + Y_LIST_LBL + 1, 0xFFFFFF, false);
        }

        int lx = px + 12, ly = py + Y_LIST;
        int lw = W - 24, lh = LIST_ROWS * ROW_H + 6;
        GuiRenderer.fillRoundedRect(ms, lx, ly, lw, lh, 4, GuiRenderer.COL_SURFACE);
        GuiRenderer.drawRoundedRectOutline(ms, lx, ly, lw, lh, 4, 1, GuiRenderer.COL_GRAY_LITE);

        List<String> blocks = ScanConfig.targetBlocks;
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + scrollOff;
            if (idx >= blocks.size()) break;
            int iy = ly + 3 + i * ROW_H;
            boolean rowHov = mx >= lx+2 && mx <= lx+lw-2 && my >= iy && my < iy+ROW_H;
            if (idx == selectedIdx)
                GuiRenderer.fillRoundedRect(ms, lx+2, iy, lw-4, ROW_H, 3, 0xFFEBF3FF);
            else if (rowHov)
                GuiRenderer.fillRoundedRect(ms, lx+2, iy, lw-4, ROW_H, 3, GuiRenderer.COL_SELECTED);
            ctx.drawText(textRenderer, "§8·", lx+7,  iy+4, 0x888888, false);
            ctx.drawText(textRenderer, blocks.get(idx), lx+16, iy+4, GuiRenderer.COL_BLACK, false);
        }
        if (blocks.size() > LIST_ROWS) {
            float sc = blocks.size() - LIST_ROWS;
            int th2 = Math.max(16, (int)((float)LIST_ROWS / blocks.size() * (lh-8)));
            int ty2 = ly + 4 + (int)((lh-8-th2) * scrollOff / sc);
            GuiRenderer.fillRoundedRect(ms, lx+lw-5, ly+4, 3, lh-8, 2, GuiRenderer.COL_GRAY_LITE);
            GuiRenderer.fillRoundedRect(ms, lx+lw-5, ty2,  3, th2,  2, GuiRenderer.COL_GRAY_MID);
        }
        if (blocks.isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Список пуст — добавьте блоки ниже",
                    lx + lw/2, ly + 38, 0xBBBBBB);

        // Поле ввода
        GuiRenderer.fillRoundedRect(ms, px+11, py+Y_INPUT-1, 202, 20, 4, GuiRenderer.COL_SURFACE);
        GuiRenderer.drawRoundedRectOutline(ms, px+11, py+Y_INPUT-1, 202, 20, 4, 1,
                blockInput.isFocused() ? GuiRenderer.COL_BLACK : GuiRenderer.COL_GRAY_LITE);
        GuiRenderer.drawButton(ctx, textRenderer, px+216, py+Y_INPUT, 112, 18,
                "+ Добавить", isHov(BTN_ADD), false);

        GuiRenderer.drawGhostButton(ctx, textRenderer, px+12,  py+Y_BTNS, 100, 18, "✕ Удалить",  isHov(BTN_DEL));
        GuiRenderer.drawGhostButton(ctx, textRenderer, px+118, py+Y_BTNS, 105, 18, "↺ Сбросить", isHov(BTN_RESET));
        GuiRenderer.drawGhostButton(ctx, textRenderer, px+229, py+Y_BTNS,  99, 18, "Закрыть",    isHov(BTN_CLOSE));

        // Серверные пресеты
        GuiRenderer.fillRect(ms, px+12, py+Y_SEP2, W-24, 1, GuiRenderer.COL_GRAY_LITE);
        ctx.drawText(textRenderer, "§8СЕРВЕРНЫЕ ПРЕСЕТЫ", px+14, py+Y_SEP2-10, 0xAAAAAA, false);

        for (Btn b : buttons) {
            if (!b.id().startsWith("server_")) continue;
            String label = b.id().substring(7);
            boolean active = isServerPresetActive(label);
            GuiRenderer.drawPresetPill(ctx, textRenderer,
                    b.x(), b.y(), label, isHovBtn(b), active);
        }

        // Главная кнопка
        int togBg = isHov(BTN_TOGGLE) ? GuiRenderer.COL_HOVER : GuiRenderer.COL_BLACK;
        GuiRenderer.fillRoundedRect(ms, px+12, py+Y_TOGGLE, W-24, 26, 5, togBg);
        String togLabel = ScanConfig.scanEnabled ? "◼  СКАНИРОВАНИЕ АКТИВНО" : "▶  ВКЛЮЧИТЬ СКАНИРОВАНИЕ";
        int togFg = ScanConfig.scanEnabled ? 0xFF88FF88 : 0xFFFFFFFF;
        int tlw = textRenderer.getWidth(togLabel);
        ctx.drawTextWithShadow(textRenderer, togLabel,
                px + 12 + (W-24-tlw)/2, py + Y_TOGGLE + 9, togFg);

        String logPath = shrink(BlockScanner.getLogPath(), W-28);
        ctx.drawText(textRenderer, "§8" + logPath, px+14, py+Y_LOG, 0x888888, false);

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int lx = px+12, ly = py+Y_LIST;
        int lw = W-24,  lh = LIST_ROWS*ROW_H+6;
        if (mx>=lx && mx<=lx+lw && my>=ly && my<=ly+lh) {
            int idx = (int)((my-ly-3)/ROW_H) + scrollOff;
            if (idx>=0 && idx<ScanConfig.targetBlocks.size()) { selectedIdx=idx; return true; }
        }
        for (Btn b : buttons) {
            if (isHovBtn(b)) { handleBtn(b.id()); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int max = Math.max(0, ScanConfig.targetBlocks.size()-LIST_ROWS);
        scrollOff = Math.max(0, Math.min(max, scrollOff-(int)Math.signum(amount)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int sc, int mod) {
        if (keyCode==261 && selectedIdx>=0) { removeBlock(); return true; }
        if (keyCode==257 && blockInput.isFocused()) { addBlock(); return true; }
        return super.keyPressed(keyCode, sc, mod);
    }

    @Override public boolean shouldPause() { return false; }

    private void handleBtn(String id) {
        switch (id) {
            case BTN_TOGGLE -> { ScanConfig.scanEnabled=!ScanConfig.scanEnabled; if(ScanConfig.scanEnabled) BlockScanner.restartSession(); ScanConfig.save(); }
            case BTN_MINUS  -> { ScanConfig.scanRadius=Math.max(1,ScanConfig.scanRadius-1); ScanConfig.save(); }
            case BTN_PLUS   -> { ScanConfig.scanRadius=Math.min(8,ScanConfig.scanRadius+1); ScanConfig.save(); }
            case BTN_ADD    -> addBlock();
            case BTN_DEL    -> removeBlock();
            case BTN_RESET  -> BlockScanner.restartSession();
            case BTN_CLOSE  -> close();
            case BTN_TGL_ELYTRA -> { ScanConfig.onlyWhenElytra=!ScanConfig.onlyWhenElytra; ScanConfig.save(); }
            case BTN_TGL_BYPASS -> { ScanConfig.bypassAntiXray=!ScanConfig.bypassAntiXray; ScanConfig.save(); BlockScanner.restartSession(); }
            case BTN_TGL_TEXT   -> { ScanConfig.prioritizeText=!ScanConfig.prioritizeText; ScanConfig.save(); BlockScanner.restartSession(); }
            default -> { if (id.startsWith("server_")) applyServerPreset(id.substring(7)); }
        }
    }

    private void addBlock() {
        String raw=blockInput.getText().trim().toLowerCase();
        if (raw.isEmpty()) return;
        if (!raw.contains(":")) raw="minecraft:"+raw;
        if (!ScanConfig.targetBlocks.contains(raw)) {
            ScanConfig.targetBlocks.add(raw); ScanConfig.save();
            scrollOff=Math.max(0,ScanConfig.targetBlocks.size()-LIST_ROWS);
            selectedIdx=ScanConfig.targetBlocks.size()-1;
        }
        blockInput.setText("");
    }

    private void removeBlock() {
        List<String> list=ScanConfig.targetBlocks;
        if (selectedIdx>=0 && selectedIdx<list.size()) {
            list.remove(selectedIdx); ScanConfig.save();
            selectedIdx=Math.min(selectedIdx,list.size()-1);
            scrollOff=Math.max(0,Math.min(scrollOff,Math.max(0,list.size()-LIST_ROWS)));
        }
    }

    // ── Серверные пресеты ────────────────────────────────────────────────────
    private static final List<String> FANTIME_BLOCKS = List.of(
            "minecraft:iron_block",
            "minecraft:gold_block",
            "minecraft:diamond_block",
            "minecraft:emerald_block",
            "minecraft:diamond_ore",
            "minecraft:emerald_ore"
    );
    private static final List<String> KUBWORLD_BLOCKS = List.of();

    private void applyServerPreset(String server) {
        List<String> preset = switch (server) {
            case "FanTime"  -> FANTIME_BLOCKS;
            case "KubWorld" -> KUBWORLD_BLOCKS;
            default -> List.of();
        };
        if (preset.isEmpty()) return;
        if (ScanConfig.targetBlocks.containsAll(preset)) {
            ScanConfig.targetBlocks.removeAll(preset);
        } else {
            for (String b : preset)
                if (!ScanConfig.targetBlocks.contains(b)) ScanConfig.targetBlocks.add(b);
        }
        ScanConfig.save();
        BlockScanner.restartSession();
    }

    private boolean isServerPresetActive(String server) {
        List<String> preset = switch (server) {
            case "FanTime"  -> FANTIME_BLOCKS;
            case "KubWorld" -> KUBWORLD_BLOCKS;
            default -> List.of();
        };
        return !preset.isEmpty() && ScanConfig.targetBlocks.containsAll(preset);
    }

    private boolean isHov(String id) { for (Btn b:buttons) if(b.id().equals(id)) return isHovBtn(b); return false; }
    private boolean isHovBtn(Btn b) { return mouseX>=b.x()&&mouseX<=b.x()+b.w()&&mouseY>=b.y()&&mouseY<=b.y()+b.h(); }
    private String shrink(String s, int maxW) { while(textRenderer.getWidth(s)>maxW&&s.length()>4) s="…"+s.substring(Math.min(s.length(),5)); return s; }
}

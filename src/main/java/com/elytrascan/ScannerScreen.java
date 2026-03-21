package com.elytrascan;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Кастомный GUI — открывается по Insert.
 * Полностью нарисован вручную через GuiRenderer (никаких стандартных кнопок MC).
 * Стиль: белый фон, чёрные кнопки, скруглённые углы, современный минималистичный вид.
 */
public class ScannerScreen extends Screen {

    // ── размеры панели ────────────────────────────────────────────────────────
    private static final int W = 340;
    private static final int H = 380;
    private static final int R = 8;   // скругление панели

    // ── список блоков ─────────────────────────────────────────────────────────
    private static final int LIST_ROWS = 6;
    private static final int ROW_H     = 15;

    private int px, py;  // левый верхний угол панели

    // ── состояние ─────────────────────────────────────────────────────────────
    private int      selectedIdx = -1;
    private int      scrollOff   = 0;
    private TextFieldWidget blockInput;

    // ── виртуальные кнопки ────────────────────────────────────────────────────
    // Каждая кнопка — просто запись: bounds + действие.
    // Hover-состояние вычисляется в render() по позиции мыши.
    private record Btn(int x, int y, int w, int h, String id) {}
    private final List<Btn> buttons = new ArrayList<>();
    private double mouseX, mouseY;

    // ID кнопок
    private static final String BTN_TOGGLE    = "toggle";
    private static final String BTN_MINUS     = "minus";
    private static final String BTN_PLUS      = "plus";
    private static final String BTN_ADD       = "add";
    private static final String BTN_DEL       = "del";
    private static final String BTN_RESET     = "reset";
    private static final String BTN_CLOSE     = "close";
    private static final String BTN_TGL_ELYTRA  = "tgl_elytra";
    private static final String BTN_TGL_BYPASS  = "tgl_bypass";
    private static final String BTN_TGL_TEXT    = "tgl_text";
    // пресеты: "preset_<blockId>"

    public ScannerScreen() {
        super(Text.literal("ElytraScan"));
    }

    // ── init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        px = (width  - W) / 2;
        py = (height - H) / 2;

        buttons.clear();

        // Текстовое поле (единственный стандартный виджет — для ввода текста)
        blockInput = new TextFieldWidget(
                textRenderer,
                px + 12, py + 258, 200, 18,
                Text.literal("Block ID"));
        blockInput.setMaxLength(250);
        blockInput.setPlaceholder(Text.literal("minecraft:chest"));

        // Стилизуем встроенное поле под наш UI
        blockInput.setEditableColor(0x0D0D0D);
        blockInput.setUneditableColor(0x888888);

        addDrawableChild(blockInput);

        // Регистрируем кнопки
        reg(BTN_MINUS,      px + 12,        py + 72, 24, 20);
        reg(BTN_PLUS,       px + 94,        py + 72, 24, 20);
        reg(BTN_TGL_ELYTRA, px + W - 12 - 34, py + 104, 34, 16);
        reg(BTN_TGL_BYPASS, px + W - 12 - 34, py + 128, 34, 16);
        reg(BTN_TGL_TEXT,   px + W - 12 - 34, py + 152, 34, 16);
        reg(BTN_ADD,        px + 216,       py + 258, 112, 18);
        reg(BTN_DEL,        px + 12,        py + 282, 100, 18);
        reg(BTN_RESET,      px + 118,       py + 282, 105, 18);
        reg(BTN_CLOSE,      px + 229,       py + 282, 99,  18);
        reg(BTN_TOGGLE,     px + 12,        py + H - 40, W - 24, 26);

        // Пресеты — строка 1
        int presX = px + 12, presY = py + 312;
        String[][] row1 = {
            {"Сундук","minecraft:chest"},{"Спавнер","minecraft:spawner"},
            {"Зашит.","minecraft:ender_chest"},{"Обломки","minecraft:ancient_debris"},
            {"Баррель","minecraft:barrel"}
        };
        for (String[] p : row1) {
            int pw = (int) GuiRenderer.presetPillWidth(textRenderer, p[0]);
            reg("preset_" + p[1], presX, presY, pw, 16);
            presX += pw + 5;
        }
        // Пресеты — строка 2
        presX = px + 12; presY += 22;
        String[][] row2 = {
            {"Алмаз","minecraft:diamond_ore"},{"Изумруд","minecraft:emerald_ore"},
            {"Нет.рuda","minecraft:nether_gold_ore"},{"Кварц","minecraft:nether_quartz_ore"},
            {"Шалкер","minecraft:shulker_box"}
        };
        for (String[] p : row2) {
            int pw = (int) GuiRenderer.presetPillWidth(textRenderer, p[0]);
            reg("preset_" + p[1], presX, presY, pw, 16);
            presX += pw + 5;
        }
    }

    private void reg(String id, int x, int y, int w, int h) {
        buttons.add(new Btn(x, y, w, h, id));
    }

    // ── render ────────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        mouseX = mx; mouseY = my;
        MatrixStack ms = ctx.getMatrices();

        // Затемнение фона
        renderBackground(ctx);

        // Тень панели
        GuiRenderer.drawShadow(ms, px, py, W, H, R);

        // Фон панели
        GuiRenderer.fillRoundedRect(ms, px, py, W, H, R, GuiRenderer.COL_BG);

        // ── Шапка ─────────────────────────────────────────────────────────────
        int headerColor = ScanConfig.scanEnabled ? 0xFF0D0D0D : 0xFF0D0D0D;
        GuiRenderer.fillRoundedRect(ms, px, py, W, 52, R, GuiRenderer.COL_BLACK);
        // скруглить только верх — перекрыть низ
        GuiRenderer.fillRect(ms, px, py + R, W, 52 - R, GuiRenderer.COL_BLACK);

        // Значок статуса
        String statusDot = ScanConfig.scanEnabled ? "§a●" : "§c●";
        ctx.drawTextWithShadow(textRenderer, statusDot, px + 14, py + 11, 0xFFFFFF);

        // Заголовок
        ctx.drawTextWithShadow(textRenderer, "ElytraScan", px + 26, py + 11, 0xFFFFFF);

        // Статистика в шапке
        String stats = "Найдено: " + BlockScanner.totalFound
                + "   Чанков: " + BlockScanner.chunksScanned;
        if (BlockScanner.textFound > 0) stats += "   Текст: " + BlockScanner.textFound;
        ctx.drawTextWithShadow(textRenderer, "§7" + stats, px + 14, py + 28, 0xAAAAAA);

        // Мир
        String world = BlockScanner.lastWorld.isEmpty() ? "—" : BlockScanner.lastWorld;
        if (world.length() > 32) world = world.substring(0, 30) + "…";
        ctx.drawTextWithShadow(textRenderer, "§8" + world, px + 14, py + 39, 0x777777);

        // ── Секция настроек ───────────────────────────────────────────────────
        int sy = py + 64;

        // Заголовок секции
        ctx.drawText(textRenderer, "§8НАСТРОЙКИ", px + 14, sy, 0xAAAAAA, false);

        // Радиус
        int ry = sy + 10;
        ctx.drawText(textRenderer, "Радиус сканирования", px + 14, ry + 7, GuiRenderer.COL_BLACK, false);
        // − кнопка
        boolean mHov = isHov(BTN_MINUS);
        GuiRenderer.drawButton(ms, textRenderer, px + 12, ry, 24, 20, "−", mHov, false);
        // значение
        String radVal = ScanConfig.scanRadius + " чанк";
        ctx.drawText(textRenderer, radVal, px + 42, ry + 7, GuiRenderer.COL_BLACK, false);
        // + кнопка
        boolean pHov = isHov(BTN_PLUS);
        GuiRenderer.drawButton(ms, textRenderer, px + 94, ry, 24, 20, "+", pHov, false);
        // пояснение
        String radHint = "≈ " + (ScanConfig.scanRadius * 16) + " блоков";
        ctx.drawText(textRenderer, "§7" + radHint, px + 124, ry + 7, GuiRenderer.COL_GRAY_MID, false);

        // ── Тоглы ─────────────────────────────────────────────────────────────
        int ty = py + 96;

        // Только элитры
        ctx.drawText(textRenderer, "Только при полёте на элитрах", px + 14, ty + 4, GuiRenderer.COL_BLACK, false);
        GuiRenderer.drawToggle(ms, px + W - 12 - 34, ty, ScanConfig.onlyWhenElytra);

        ty += 24;
        // Обход антиксрея
        ctx.drawText(textRenderer, "Обход замены блоков", px + 14, ty + 4, GuiRenderer.COL_BLACK, false);
        ctx.drawText(textRenderer, "§7сканировать только вблизи", px + 14, ty + 13, GuiRenderer.COL_GRAY_MID, false);
        GuiRenderer.drawToggle(ms, px + W - 12 - 34, ty, ScanConfig.bypassAntiXray);

        ty += 24;
        // Только с текстом
        ctx.drawText(textRenderer, "Только блоки с текстом/именем", px + 14, ty + 4, GuiRenderer.COL_BLACK, false);
        GuiRenderer.drawToggle(ms, px + W - 12 - 34, ty, ScanConfig.prioritizeText);

        // ── Разделитель ───────────────────────────────────────────────────────
        int sepY = py + 174;
        GuiRenderer.fillRect(ms, px + 12, sepY, W - 24, 1, GuiRenderer.COL_GRAY_LITE);

        // ── Список блоков ─────────────────────────────────────────────────────
        ctx.drawText(textRenderer, "§8ЦЕЛЕВЫЕ БЛОКИ", px + 14, sepY + 8, 0xAAAAAA, false);
        int cntBadgeX = px + 14 + textRenderer.getWidth("ЦЕЛЕВЫЕ БЛОКИ") + 14;
        int cnt = ScanConfig.targetBlocks.size();
        if (cnt > 0) {
            GuiRenderer.fillRoundedRect(ms, cntBadgeX - 2, sepY + 6, 20, 12, 6, GuiRenderer.COL_BLACK);
            ctx.drawText(textRenderer, String.valueOf(cnt),
                    cntBadgeX + (20 - 6 * String.valueOf(cnt).length()) / 2,
                    sepY + 9, 0xFFFFFF, false);
        }

        int lx = px + 12, ly = sepY + 22;
        int lw = W - 24, lh = LIST_ROWS * ROW_H + 6;

        // Фон списка
        GuiRenderer.fillRoundedRect(ms, lx, ly, lw, lh, 4, GuiRenderer.COL_SURFACE);
        GuiRenderer.drawRoundedRectOutline(ms, lx, ly, lw, lh, 4, 1, GuiRenderer.COL_GRAY_LITE);

        // Строки
        List<String> blocks = ScanConfig.targetBlocks;
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + scrollOff;
            if (idx >= blocks.size()) break;

            int iy = ly + 3 + i * ROW_H;
            boolean rowHov = mx >= lx + 2 && mx <= lx + lw - 2 && my >= iy && my < iy + ROW_H;

            if (idx == selectedIdx) {
                GuiRenderer.fillRoundedRect(ms, lx + 2, iy, lw - 4, ROW_H, 3, 0xFFEBF3FF);
            } else if (rowHov) {
                GuiRenderer.fillRoundedRect(ms, lx + 2, iy, lw - 4, ROW_H, 3, GuiRenderer.COL_SELECTED);
            }

            // Цветная точка
            String dot = "§8·";
            ctx.drawText(textRenderer, dot, lx + 7, iy + 3, 0x888888, false);
            ctx.drawText(textRenderer, blocks.get(idx), lx + 16, iy + 3, GuiRenderer.COL_BLACK, false);
        }

        // Скроллбар
        if (blocks.size() > LIST_ROWS) {
            float scrollable = blocks.size() - LIST_ROWS;
            int thumbH = Math.max(16, (int)((float)LIST_ROWS / blocks.size() * (lh - 8)));
            int thumbY = ly + 4 + (int)((lh - 8 - thumbH) * scrollOff / scrollable);
            GuiRenderer.fillRoundedRect(ms, lx + lw - 5, ly + 4, 3, lh - 8, 2, GuiRenderer.COL_GRAY_LITE);
            GuiRenderer.fillRoundedRect(ms, lx + lw - 5, thumbY, 3, thumbH, 2, GuiRenderer.COL_GRAY_MID);
        }

        if (blocks.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Список пуст — добавьте блоки ниже",
                    lx + lw / 2, ly + 30, 0xBBBBBB);
        }

        // ── Поле ввода + кнопки ───────────────────────────────────────────────
        // Рамка поля ввода
        GuiRenderer.fillRoundedRect(ms, px + 11, py + 257, 202, 20, 4, GuiRenderer.COL_SURFACE);
        GuiRenderer.drawRoundedRectOutline(ms, px + 11, py + 257, 202, 20, 4, 1,
                blockInput.isFocused() ? GuiRenderer.COL_BLACK : GuiRenderer.COL_GRAY_LITE);

        GuiRenderer.drawButton(ms, textRenderer, px + 216, py + 258, 112, 18,
                "+ Добавить", isHov(BTN_ADD), false);

        GuiRenderer.drawGhostButton(ms, textRenderer, px + 12,  py + 282, 100, 18,
                "✕ Удалить", isHov(BTN_DEL));
        GuiRenderer.drawGhostButton(ms, textRenderer, px + 118, py + 282, 105, 18,
                "↺ Сбросить", isHov(BTN_RESET));
        GuiRenderer.drawGhostButton(ms, textRenderer, px + 229, py + 282, 99,  18,
                "Закрыть", isHov(BTN_CLOSE));

        // ── Разделитель ───────────────────────────────────────────────────────
        GuiRenderer.fillRect(ms, px + 12, py + 306, W - 24, 1, GuiRenderer.COL_GRAY_LITE);

        // ── Пресеты ───────────────────────────────────────────────────────────
        for (Btn b : buttons) {
            if (!b.id().startsWith("preset_")) continue;
            String blockId = b.id().substring(7);
            // Ищем label по id
            String label = presetLabel(blockId);
            boolean active = ScanConfig.targetBlocks.contains(blockId);
            GuiRenderer.drawPresetPill(ms, textRenderer, b.x(), b.y(), label,
                    isHovBtn(b), active);
        }

        // ── Главная кнопка ────────────────────────────────────────────────────
        GuiRenderer.fillRoundedRect(ms, px + 12, py + H - 40, W - 24, 26, 5,
                ScanConfig.scanEnabled
                        ? (isHov(BTN_TOGGLE) ? GuiRenderer.COL_HOVER : GuiRenderer.COL_BLACK)
                        : (isHov(BTN_TOGGLE) ? 0xFF333333 : GuiRenderer.COL_BLACK));

        String toggleLabel = ScanConfig.scanEnabled
                ? "◼  СКАНИРОВАНИЕ АКТИВНО"
                : "▶  ВКЛЮЧИТЬ СКАНИРОВАНИЕ";
        int toggleFg = ScanConfig.scanEnabled ? 0xFF88FF88 : 0xFFFFFFFF;
        int tlw = textRenderer.getWidth(toggleLabel);
        ctx.drawTextWithShadow(textRenderer, toggleLabel,
                px + 12 + (W - 24 - tlw) / 2, py + H - 40 + 9, toggleFg);

        // Лог-путь
        String logPath = shrink(BlockScanner.getLogPath(), W - 28);
        ctx.drawText(textRenderer, "§8" + logPath, px + 14, py + H - 10, 0x888888, false);

        super.render(ctx, mx, my, delta);
    }

    // ── события мыши ─────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Клик по списку
        int lx = px + 12, ly = py + 196;
        int lw = W - 24, lh = LIST_ROWS * ROW_H + 6;
        if (mx >= lx && mx <= lx + lw && my >= ly && my <= ly + lh) {
            int idx = (int)((my - ly - 3) / ROW_H) + scrollOff;
            if (idx >= 0 && idx < ScanConfig.targetBlocks.size()) {
                selectedIdx = idx;
                return true;
            }
        }

        // Кнопки
        for (Btn b : buttons) {
            if (isHovBtn(b)) {
                handleBtn(b.id());
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int max = Math.max(0, ScanConfig.targetBlocks.size() - LIST_ROWS);
        scrollOff = Math.max(0, Math.min(max, scrollOff - (int)Math.signum(amount)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int sc, int mod) {
        if (keyCode == 261 && selectedIdx >= 0) { removeBlock(); return true; }
        if (keyCode == 257 && blockInput.isFocused()) { addBlock(); return true; }
        return super.keyPressed(keyCode, sc, mod);
    }

    @Override
    public boolean shouldPause() { return false; }

    // ── действия ─────────────────────────────────────────────────────────────
    private void handleBtn(String id) {
        switch (id) {
            case BTN_TOGGLE -> {
                ScanConfig.scanEnabled = !ScanConfig.scanEnabled;
                if (ScanConfig.scanEnabled) BlockScanner.restartSession();
                ScanConfig.save();
            }
            case BTN_MINUS  -> { ScanConfig.scanRadius = Math.max(1, ScanConfig.scanRadius - 1); ScanConfig.save(); }
            case BTN_PLUS   -> { ScanConfig.scanRadius = Math.min(8, ScanConfig.scanRadius + 1); ScanConfig.save(); }
            case BTN_ADD    -> addBlock();
            case BTN_DEL    -> removeBlock();
            case BTN_RESET  -> BlockScanner.restartSession();
            case BTN_CLOSE  -> close();
            case BTN_TGL_ELYTRA -> { ScanConfig.onlyWhenElytra  = !ScanConfig.onlyWhenElytra;  ScanConfig.save(); }
            case BTN_TGL_BYPASS -> { ScanConfig.bypassAntiXray  = !ScanConfig.bypassAntiXray;  ScanConfig.save(); BlockScanner.restartSession(); }
            case BTN_TGL_TEXT   -> { ScanConfig.prioritizeText  = !ScanConfig.prioritizeText;   ScanConfig.save(); BlockScanner.restartSession(); }
            default -> {
                if (id.startsWith("preset_")) {
                    String blockId = id.substring(7);
                    if (!ScanConfig.targetBlocks.contains(blockId)) {
                        ScanConfig.targetBlocks.add(blockId);
                        ScanConfig.save();
                    } else {
                        ScanConfig.targetBlocks.remove(blockId);
                        ScanConfig.save();
                    }
                }
            }
        }
    }

    private void addBlock() {
        String raw = blockInput.getText().trim().toLowerCase();
        if (raw.isEmpty()) return;
        if (!raw.contains(":")) raw = "minecraft:" + raw;
        if (!ScanConfig.targetBlocks.contains(raw)) {
            ScanConfig.targetBlocks.add(raw);
            ScanConfig.save();
            scrollOff = Math.max(0, ScanConfig.targetBlocks.size() - LIST_ROWS);
            selectedIdx = ScanConfig.targetBlocks.size() - 1;
        }
        blockInput.setText("");
    }

    private void removeBlock() {
        List<String> list = ScanConfig.targetBlocks;
        if (selectedIdx >= 0 && selectedIdx < list.size()) {
            list.remove(selectedIdx);
            ScanConfig.save();
            selectedIdx = Math.min(selectedIdx, list.size() - 1);
            scrollOff   = Math.max(0, Math.min(scrollOff, Math.max(0, list.size() - LIST_ROWS)));
        }
    }

    // ── утилиты ──────────────────────────────────────────────────────────────
    private boolean isHov(String id) {
        for (Btn b : buttons) if (b.id().equals(id)) return isHovBtn(b);
        return false;
    }
    private boolean isHovBtn(Btn b) {
        return mouseX >= b.x() && mouseX <= b.x() + b.w()
            && mouseY >= b.y() && mouseY <= b.y() + b.h();
    }

    private String shrink(String s, int maxW) {
        while (textRenderer.getWidth(s) > maxW && s.length() > 4)
            s = "…" + s.substring(Math.min(s.length(), 5));
        return s;
    }

    private static String presetLabel(String blockId) {
        return switch (blockId) {
            case "minecraft:chest"              -> "Сундук";
            case "minecraft:spawner"            -> "Спавнер";
            case "minecraft:ender_chest"        -> "Зашит.";
            case "minecraft:ancient_debris"     -> "Обломки";
            case "minecraft:barrel"             -> "Баррель";
            case "minecraft:diamond_ore"        -> "Алмаз";
            case "minecraft:emerald_ore"        -> "Изумруд";
            case "minecraft:nether_gold_ore"    -> "Нет.руда";
            case "minecraft:nether_quartz_ore"  -> "Кварц";
            case "minecraft:shulker_box"        -> "Шалкер";
            default -> blockId.contains(":") ? blockId.split(":")[1] : blockId;
        };
    }
}

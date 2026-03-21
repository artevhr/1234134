package com.elytrascan;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Главный GUI — открывается по нажатию Insert.
 *
 * Раскладка (320 × 300 пикселей по центру экрана):
 * ┌─────────────────────────────────┐
 * │  ⛵ ElytraScan           [ESC]  │
 * │  Найдено: X  |  Чанков: Y       │
 * ├─────────────────────────────────┤
 * │  Радиус: [−][3 чанка][+]        │
 * │  [●ТОЛЬКО ЭЛИТРЫ]               │
 * │─────────────────────────────────│
 * │  Целевые блоки:                 │
 * │  ┌────────────────────────┐     │
 * │  │ minecraft:chest        │     │
 * │  │ minecraft:spawner      │     │  ← скролл
 * │  │ ...                    │     │
 * │  └────────────────────────┘     │
 * │  [  + Добавить  ] [текст.поле]  │
 * │  [  ✕ Удалить   ] [↺ Сброс]    │
 * │─────────────────────────────────│
 * │  Пресеты: [Сундук][Спавнер]...  │
 * ├─────────────────────────────────┤
 * │  [██ ВКЛЮЧИТЬ СКАНИРОВАНИЕ ██]  │
 * └─────────────────────────────────┘
 */
public class ScannerScreen extends Screen {

    // Размеры панели
    private static final int W = 330;
    private static final int H = 310;

    // Список — видимые строки
    private static final int LIST_ROWS    = 6;
    private static final int ROW_H        = 13;

    private int px, py; // левый верхний угол панели

    // Виджеты
    private TextFieldWidget blockInput;
    private int selectedIdx = -1;
    private int scrollOff   = 0;

    // Кнопки (объявим поля, чтобы обновлять текст)
    private ButtonWidget btnToggle;
    private ButtonWidget btnElytra;

    public ScannerScreen() {
        super(Text.literal("ElytraScan"));
    }

    // ── init ─────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        px = (width  - W) / 2;
        py = (height - H) / 2;

        // Поле ввода блока
        blockInput = new TextFieldWidget(
                textRenderer,
                px + 10, py + 174, 198, 16,
                Text.literal("Block ID"));
        blockInput.setMaxLength(250);
        blockInput.setPlaceholder(Text.literal("minecraft:chest"));
        addDrawableChild(blockInput);

        // Кнопка добавить
        addBtn(px + 212, py + 174, 108, 16, "+ Добавить", btn -> addBlock());

        // Кнопка удалить
        addBtn(px + 10, py + 194, 100, 16, "✕ Удалить", btn -> removeBlock());

        // Сброс карты
        addBtn(px + 116, py + 194, 105, 16, "↺ Сбросить карту", btn -> {
            BlockScanner.restartSession();
        });

        // ESC / Закрыть
        addBtn(px + 225, py + 194, 95, 16, "Закрыть [ESC]", btn -> close());

        // ── Пресеты ─────────────────────────────────────────────────────────
        int presY = py + 216;
        addPreset(px + 10,  presY, "Сундук",    "minecraft:chest");
        addPreset(px + 65,  presY, "Спавнер",   "minecraft:spawner");
        addPreset(px + 125, presY, "Зашитый",   "minecraft:ender_chest");
        addPreset(px + 186, presY, "Обломки",   "minecraft:ancient_debris");
        addPreset(px + 257, presY, "Баррель",   "minecraft:barrel");

        int presY2 = py + 233;
        addPreset(px + 10,  presY2, "Алмаз",   "minecraft:diamond_ore");
        addPreset(px + 58,  presY2, "Изумруд",  "minecraft:emerald_ore");
        addPreset(px + 112, presY2, "Нет.Руда", "minecraft:nether_gold_ore");
        addPreset(px + 175, presY2, "Кварц",    "minecraft:nether_quartz_ore");
        addPreset(px + 231, presY2, "Шалкер",   "minecraft:shulker_box");

        // ── Радиус – / + ────────────────────────────────────────────────────
        addBtn(px + 10, py + 45, 20, 14, "−", btn -> changeRadius(-1));
        addBtn(px + 88, py + 45, 20, 14, "+", btn -> changeRadius(+1));

        // ── Только элитры ───────────────────────────────────────────────────
        btnElytra = addBtn(px + 120, py + 45, 200, 14,
                elytraText(), btn -> {
                    ScanConfig.onlyWhenElytra = !ScanConfig.onlyWhenElytra;
                    ScanConfig.save();
                    btn.setMessage(Text.literal(elytraText()));
                });

        // ── Главная кнопка вкл/выкл ─────────────────────────────────────────
        btnToggle = addBtn(px + 10, py + 256, W - 20, 22,
                toggleText(), btn -> {
                    ScanConfig.scanEnabled = !ScanConfig.scanEnabled;
                    if (ScanConfig.scanEnabled) BlockScanner.restartSession();
                    ScanConfig.save();
                    btn.setMessage(Text.literal(toggleText()));
                });
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private ButtonWidget addBtn(int x, int y, int w, int h, String label,
                                ButtonWidget.PressAction action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, w, h).build();
        addDrawableChild(b);
        return b;
    }

    private void addPreset(int x, int y, String label, String blockId) {
        addBtn(x, y, textRenderer.getWidth(label) + 8, 14, "§7" + label,
                btn -> quickAdd(blockId));
    }

    private void addBlock() {
        String raw = blockInput.getText().trim().toLowerCase();
        if (raw.isEmpty()) return;
        if (!raw.contains(":")) raw = "minecraft:" + raw;
        if (!ScanConfig.targetBlocks.contains(raw)) {
            ScanConfig.targetBlocks.add(raw);
            ScanConfig.save();
            scrollOff = Math.max(0, ScanConfig.targetBlocks.size() - LIST_ROWS);
        }
        blockInput.setText("");
        selectedIdx = ScanConfig.targetBlocks.size() - 1;
    }

    private void quickAdd(String blockId) {
        if (!ScanConfig.targetBlocks.contains(blockId)) {
            ScanConfig.targetBlocks.add(blockId);
            ScanConfig.save();
        }
    }

    private void removeBlock() {
        List<String> list = ScanConfig.targetBlocks;
        if (selectedIdx >= 0 && selectedIdx < list.size()) {
            list.remove(selectedIdx);
            ScanConfig.save();
            selectedIdx = Math.min(selectedIdx, list.size() - 1);
            scrollOff   = Math.max(0, Math.min(scrollOff,
                    Math.max(0, list.size() - LIST_ROWS)));
        }
    }

    private void changeRadius(int delta) {
        ScanConfig.scanRadius = Math.max(1, Math.min(8, ScanConfig.scanRadius + delta));
        ScanConfig.save();
    }

    private String toggleText() {
        return ScanConfig.scanEnabled
                ? "§a█  СКАНИРОВАНИЕ  ВКЛЮЧЕНО  █"
                : "§c█  СКАНИРОВАНИЕ  ВЫКЛЮЧЕНО  █";
    }

    private String elytraText() {
        return ScanConfig.onlyWhenElytra
                ? "§e🪂 Только элитры: §aВКЛ"
                : "§7🪂 Только элитры: §cВЫКЛ";
    }

    // ── рендер ────────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);

        // Панель фон
        ctx.fill(px, py, px + W, py + H, 0xEC0D0D0D);

        // Цветная рамка
        int bc = ScanConfig.scanEnabled ? 0xFF22BB55 : 0xFF555566;
        ctx.fill(px,         py,         px + W,     py + 1,     bc);  // top
        ctx.fill(px,         py + H - 1, px + W,     py + H,     bc);  // bottom
        ctx.fill(px,         py,         px + 1,     py + H,     bc);  // left
        ctx.fill(px + W - 1, py,         px + W,     py + H,     bc);  // right

        // ── Заголовок ─────────────────────────────────────────────────────
        ctx.fill(px, py, px + W, py + 30, 0xFF141420);
        String title = ScanConfig.scanEnabled
                ? "§b⛵ §fElytraScan  §a[АКТИВЕН]"
                : "§b⛵ §fElytraScan  §7[выключен]";
        ctx.drawCenteredTextWithShadow(textRenderer, title, px + W / 2, py + 6, 0xFFFFFF);

        // Статистика
        String stats = String.format("§7Найдено: §b%-5d §7 Чанков просмотрено: §e%d",
                BlockScanner.totalFound, BlockScanner.chunksScanned);
        ctx.drawCenteredTextWithShadow(textRenderer, stats, px + W / 2, py + 18, 0xFFFFFF);

        // Разделитель
        ctx.fill(px + 5, py + 30, px + W - 5, py + 31, 0xFF333355);

        // ── Радиус ────────────────────────────────────────────────────────
        ctx.drawTextWithShadow(textRenderer, "§7Радиус:", px + 10, py + 36, 0xFFFFFF);
        String radStr = "§f" + ScanConfig.scanRadius
                + " §7чанк(а)  §8≈ " + ScanConfig.scanRadius * 16 + " блоков";
        ctx.drawTextWithShadow(textRenderer, radStr, px + 34, py + 47, 0xFFFFFF);

        // ── Разделитель ───────────────────────────────────────────────────
        ctx.fill(px + 5, py + 63, px + W - 5, py + 64, 0xFF333355);

        // ── Заголовок списка ──────────────────────────────────────────────
        ctx.drawTextWithShadow(textRenderer, "§fЦелевые блоки:", px + 10, py + 67, 0xFFFFFF);
        int cnt = ScanConfig.targetBlocks.size();
        ctx.drawTextWithShadow(textRenderer, "§8(" + cnt + " шт.)", px + 105, py + 67, 0xAAAAAA);

        // ── Список блоков ─────────────────────────────────────────────────
        int lx = px + 10, ly = py + 78;
        int lw = W - 20,  lh = LIST_ROWS * ROW_H + 4;
        ctx.fill(lx, ly, lx + lw, ly + lh, 0xFF0A0A14);
        ctx.fill(lx, ly, lx + lw, ly + 1,  0xFF334466);  // top border

        List<String> blocks = ScanConfig.targetBlocks;
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = i + scrollOff;
            if (idx >= blocks.size()) break;

            int iy = ly + 2 + i * ROW_H;

            // Выбранный элемент
            if (idx == selectedIdx) {
                ctx.fill(lx + 1, iy, lx + lw - 1, iy + ROW_H, 0xAA1A3A6A);
            }
            // Hover
            if (mx >= lx && mx <= lx + lw && my >= iy && my < iy + ROW_H) {
                ctx.fill(lx + 1, iy, lx + lw - 1, iy + ROW_H, 0x44FFFFFF);
            }

            String label = "§7" + (idx + 1) + ". §f" + blocks.get(idx);
            ctx.drawTextWithShadow(textRenderer, label, lx + 4, iy + 2, 0xFFFFFF);
        }

        // Скролл-индикатор справа
        if (blocks.size() > LIST_ROWS) {
            int totalH = lh - 4;
            int thumbH  = Math.max(10, totalH * LIST_ROWS / blocks.size());
            int thumbY  = ly + 2 + (int)((totalH - thumbH) * (float) scrollOff
                    / Math.max(1, blocks.size() - LIST_ROWS));
            ctx.fill(lx + lw - 4, ly + 2, lx + lw - 2, ly + lh - 2, 0xFF222233);
            ctx.fill(lx + lw - 4, thumbY, lx + lw - 2, thumbY + thumbH, 0xFF6688BB);
        }

        // Пустой список — подсказка
        if (blocks.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Список пуст — добавьте блоки ниже", lx + lw / 2, ly + 28, 0x666666);
        }

        // ── Разделитель ───────────────────────────────────────────────────
        ctx.fill(px + 5, py + 212, px + W - 5, py + 213, 0xFF333355);

        // Пресеты — метка
        ctx.drawTextWithShadow(textRenderer, "§7Быстрые пресеты:", px + 10, py + 205, 0x888888);

        // ── Лог-файл ──────────────────────────────────────────────────────
        ctx.fill(px + 5, py + 250, px + W - 5, py + 251, 0xFF333355);
        String logPath = BlockScanner.getLogPath();
        // Обрезаем до ширины панели
        String displayPath = logPath;
        while (textRenderer.getWidth("§8" + displayPath) > W - 20 && displayPath.length() > 6) {
            displayPath = "…" + displayPath.substring(Math.min(displayPath.length(), 4));
        }
        ctx.drawTextWithShadow(textRenderer, "§8Лог: " + displayPath, px + 10, py + 253, 0x666666);

        super.render(ctx, mx, my, delta);
    }

    // ── mouse ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Клик по списку
        int lx = px + 10, ly = py + 78;
        int lw = W - 20,  lh = LIST_ROWS * ROW_H + 4;
        if (mx >= lx && mx <= lx + lw && my >= ly && my <= ly + lh) {
            int idx = (int)((my - ly - 2) / ROW_H) + scrollOff;
            if (idx >= 0 && idx < ScanConfig.targetBlocks.size()) {
                selectedIdx = idx;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int max = Math.max(0, ScanConfig.targetBlocks.size() - LIST_ROWS);
        scrollOff = Math.max(0, Math.min(max, scrollOff - (int) Math.signum(amount)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Delete → удалить выбранный блок
        if (keyCode == 261 /* GLFW_KEY_DELETE */ && selectedIdx >= 0) {
            removeBlock();
            return true;
        }
        // Enter → добавить
        if (keyCode == 257 /* GLFW_KEY_ENTER */ && blockInput.isFocused()) {
            addBlock();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}

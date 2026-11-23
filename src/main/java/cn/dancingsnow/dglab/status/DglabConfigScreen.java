package cn.dancingsnow.dglab.status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * In-game menu for the damage feedback rules and the device output.
 * Everything the command tree can change is reachable here, and closing the screen saves.
 */
final class DglabConfigScreen extends Screen {
    private static final int ROW = 18;
    private static final int PITCH = 22;
    private static final int MAX_SOURCE_ROWS = 5;
    private static final int TEXT = 0xFFE6E8EC;
    private static final int DIM = 0xFF9AA3AD;
    private static final int ACCENT = 0xFF94D9FF;

    private record Line(Supplier<Component> text, int x, int y, int width, int color) {}

    private final DamageFeedback feedback;
    private final DeviceLink device;
    private final HudOptions hud;
    private final List<Line> lines = new ArrayList<>();
    private List<String> sources = List.of();
    private boolean sourcesPage;
    private boolean rebuildQueued;
    private int page;
    private int rowsPerPage = MAX_SOURCE_ROWS;
    private int panelLeft, panelTop, panelWidth, panelBottom;

    DglabConfigScreen(DamageFeedback feedback, DeviceLink device, HudOptions hud) {
        super(Component.translatable("dglab.menu.title"));
        this.feedback = feedback;
        this.device = device;
        this.hud = hud;
    }

    private DamageOptions options() { return feedback.options; }

    @Override protected void init() {
        lines.clear();
        sources = collectSources();
        panelWidth = Math.min(width - 10, 420);
        panelLeft = (width - panelWidth) / 2;
        panelTop = 8;
        int bottomRow = height - 26;
        panelBottom = bottomRow + ROW + 8;

        int column = (panelWidth - 16) / 2;
        int ax = panelLeft + 6;
        int bx = panelLeft + 10 + column;
        int top = panelTop + 26;

        addRenderableWidget(Button.builder(Component.translatable(sourcesPage ? "dglab.menu.tab_back" : "dglab.menu.tab_sources"),
                        b -> {
                            sourcesPage = !sourcesPage;
                            rebuildQueued = true;
                        })
                .bounds(panelLeft + panelWidth - 82, panelTop + 4, 76, ROW).build());

        if (sourcesPage) sourcePage(panelLeft + 6, top, panelWidth - 12);
        else settingsPage(ax, bx, top, column);

        addRenderableWidget(Button.builder(Component.translatable("dglab.menu.save"), b -> onClose())
                .bounds(panelLeft + 6, bottomRow, 110, ROW).build());
        addRenderableWidget(Button.builder(Component.translatable("dglab.menu.reset"), b -> {
            options().defaults();
            page = 0;
            device.stop();
            rebuildQueued = true;
        }).bounds(panelLeft + panelWidth - 100, bottomRow, 94, ROW).build());
    }

    private void settingsPage(int ax, int bx, int top, int column) {
        section(ax, top - 12, column, "dglab.menu.feedback");
        toggleRow(ax, top, column, "dglab.menu.enabled", () -> options().enabled, value -> options().enabled = value, null);
        toggleRow(ax, top + PITCH, column, "dglab.menu.all_sources", () -> options().allSources,
                value -> options().allSources = value, null);
        numberRow(ax, top + PITCH * 2, column, "dglab.menu.base", () -> options().base, 0, 100, 5,
                value -> options().base = value, null);
        numberRow(ax, top + PITCH * 3, column, "dglab.menu.step", () -> options().step, -100, 100, 4,
                value -> options().step = value, null);
        numberRow(ax, top + PITCH * 4, column, "dglab.menu.decay", () -> options().decay, 0, 100, 1,
                value -> options().decay = value, null);
        waveRow(ax, top + PITCH * 5, column, "dglab.menu.wave", () -> options().wave, value -> options().wave = value, null);

        section(bx, top - 12, column, "dglab.menu.device");
        toggleRow(bx, top, column, "dglab.menu.device_enabled", () -> options().device, value -> {
            options().device = value;
            if (!value) device.stop();
        }, "dglab.menu.device_tip");
        channelRow(bx, top + PITCH, column);
        numberRow(bx, top + PITCH * 2, column, "dglab.menu.frames", () -> options().deviceFrames, 1, 3, 1,
                value -> { options().deviceFrames = value; device.stop(); }, "dglab.menu.frames_tip");
        numberRow(bx, top + PITCH * 3, column, "dglab.menu.queue", () -> options().queueSeconds, 0, Integer.MAX_VALUE, 1,
                value -> options().queueSeconds = value, "dglab.menu.queue_tip");
        toggleRow(bx, top + PITCH * 4, column, "dglab.menu.idle", () -> options().deviceClearIdle,
                value -> options().deviceClearIdle = value, "dglab.menu.idle_tip");
        addRenderableWidget(Button.builder(Component.translatable("dglab.menu.preview"), b -> {
            var player = Minecraft.getInstance().player;
            if (player != null) feedback.preview(player, options().wave);
        }).bounds(bx, top + PITCH * 5, column, ROW).build());
    }

    private void sourcePage(int x, int top, int width) {
        int available = (height - 30) - top;
        rowsPerPage = Math.clamp(available / PITCH - 2, 1, MAX_SOURCE_ROWS);
        section(x, top - 12, width, "dglab.menu.sources");
        deathRow(x, top, width);
        int shown = Math.min(rowsPerPage, Math.max(0, sources.size() - page * rowsPerPage));
        if (sources.isEmpty()) {
            line(x + 2, top + PITCH + 5, width, () -> Component.translatable("dglab.menu.no_sources"), DIM);
        }
        for (int row = 0; row < shown; row++)
            sourceRow(x, top + (row + 1) * PITCH, width, sources.get(page * rowsPerPage + row));
        if (shown == 0) return;
        int pagerY = top + (shown + 1) * PITCH;
        Button prev = addRenderableWidget(Button.builder(Component.translatable("dglab.menu.prev"), b -> {
            page = Math.max(0, page - 1);
            rebuildQueued = true;
        }).bounds(x, pagerY, 52, ROW).build());
        prev.active = page > 0;
        line(x + width / 2 - 40, pagerY + 5, 80, () -> Component.translatable("dglab.menu.page",
                page + 1, Math.max(1, (sources.size() + rowsPerPage - 1) / rowsPerPage)), DIM);
        Button next = addRenderableWidget(Button.builder(Component.translatable("dglab.menu.next"), b -> {
            page = Math.min(Math.max(0, (sources.size() - 1) / rowsPerPage), page + 1);
            rebuildQueued = true;
        }).bounds(x + width - 52, pagerY, 52, ROW).build());
        next.active = (page + 1) * rowsPerPage < sources.size();
    }

    @Override public void tick() {
        if (!rebuildQueued) return;
        rebuildQueued = false;
        rebuildWidgets();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x99070A0C);
        g.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xE61E2026);
        g.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, ACCENT);
        g.centeredText(font, title, panelLeft + (panelWidth - 40) / 2, panelTop + 5, 0xFFFFFFFF);
        for (Line line : lines) {
            String value = line.text().get().getString();
            g.text(font, font.plainSubstrByWidth(value, Math.max(0, line.width())), line.x(), line.y(), line.color());
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() {
        boolean saved = options().save();
        hud.save();
        if (!options().device) device.stop();
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(
                Component.translatable(saved ? "dglab.feedback.saved" : "dglab.feedback.temporary"));
        super.onClose();
    }

    private void section(int x, int y, int width, String key) {
        line(x + 2, y + 3, width, () -> Component.translatable(key), ACCENT);
    }

    private void line(int x, int y, int width, Supplier<Component> text, int color) {
        lines.add(new Line(text, x, y, width, color));
    }

    private void toggleRow(int x, int y, int width, String key, Supplier<Boolean> value,
                           Consumer<Boolean> set, String tipKey) {
        line(x + 2, y + 5, width - 48, () -> Component.translatable(key), TEXT);
        CycleButton<Boolean> button = addRenderableWidget(CycleButton.<Boolean>builder(
                        state -> Component.translatable("dglab.menu." + (state ? "on" : "off")), value.get())
                .withValues(true, false)
                .displayOnlyValue()
                .create(x + width - 44, y, 44, ROW, Component.translatable(key), (widget, state) -> set.accept(state)));
        if (tipKey != null) button.setTooltip(Tooltip.create(Component.translatable(tipKey)));
    }

    private void channelRow(int x, int y, int width) {
        line(x + 2, y + 5, width - 60, () -> Component.translatable("dglab.menu.channel"), TEXT);
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(switch (value) {
                    case 2 -> "B";
                    case 3 -> "A+B";
                    default -> "A";
                }), options().deviceChannel)
                .withValues(1, 2, 3)
                .displayOnlyValue()
                .create(x + width - 56, y, 56, ROW, Component.translatable("dglab.menu.channel"), (widget, value) -> {
                    options().deviceChannel = value;
                    device.stop();
                }));
    }

    private void waveRow(int x, int y, int width, String key, Supplier<String> value,
                         Consumer<String> set, String tipKey) {
        line(x + 2, y + 5, width - 88, () -> Component.translatable(key), TEXT);
        CycleButton<String> button = addRenderableWidget(CycleButton.<String>builder(Component::literal, value.get())
                .withValues(WavePreviews.ALL.keySet())
                .displayOnlyValue()
                .create(x + width - 84, y, 84, ROW, Component.translatable(key), (widget, name) -> set.accept(name)));
        if (tipKey != null) button.setTooltip(Tooltip.create(Component.translatable(tipKey)));
    }

    private void numberRow(int x, int y, int width, String key, Supplier<Integer> value, int min, int max,
                            int delta, IntConsumer set, String tipKey) {
        line(x + 2, y + 5, width - 84, () -> Component.translatable(key), TEXT);
        NumberRow row = new NumberRow(x + width - 58, y, key, min, max, value, set, tipKey);
        Button minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> row.adjust(-delta))
                .bounds(x + width - 78, y, ROW, ROW).build());
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> row.adjust(delta))
                .bounds(x + width - 18, y, ROW, ROW).build());
        addRenderableWidget(row.box);
        if (tipKey != null) {
            Tooltip tip = Tooltip.create(Component.translatable(tipKey));
            minus.setTooltip(tip);
            plus.setTooltip(tip);
        }
    }

    private void deathRow(int x, int y, int width) {
        ruleRow(x, y, width, Component.translatable("dglab.menu.death"),
                () -> options().deathEnabled, value -> options().deathEnabled = value,
                () -> options().deathStep, value -> options().deathStep = value,
                () -> options().deathWave, value -> options().deathWave = value);
    }

    private void sourceRow(int x, int y, int width, String id) {
        ruleRow(x, y, width, SourceNames.of(id),
                () -> options().includes(id), value -> options().edit(id).enabled = value,
                () -> options().stepFor(id), value -> options().edit(id).step = value,
                () -> options().waveFor(id), value -> options().edit(id).wave = value);
    }

    /** One editable event: on/off, hit step and wave preset. Death and each damage type share this layout. */
    private void ruleRow(int x, int y, int width, Component name,
                         Supplier<Boolean> enabled, Consumer<Boolean> enabledSet,
                         Supplier<Integer> step, IntConsumer stepSet,
                         Supplier<String> wave, Consumer<String> waveSet) {
        line(x + 2, y + 5, width - 196, () -> name, TEXT);
        addRenderableWidget(CycleButton.<Boolean>builder(
                        state -> Component.translatable("dglab.menu." + (state ? "on" : "off")), enabled.get())
                .withValues(true, false)
                .displayOnlyValue()
                .create(x + width - 192, y, 44, ROW, name, (widget, state) -> enabledSet.accept(state)));
        NumberRow row = new NumberRow(x + width - 122, y, "dglab.menu.step", -100, 100, step, stepSet, null);
        addRenderableWidget(Button.builder(Component.literal("-"), b -> row.adjust(-4))
                .bounds(x + width - 144, y, ROW, ROW).build());
        addRenderableWidget(row.box);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> row.adjust(4))
                .bounds(x + width - 82, y, ROW, ROW).build());
        addRenderableWidget(CycleButton.<String>builder(Component::literal, wave.get())
                .withValues(WavePreviews.ALL.keySet())
                .displayOnlyValue()
                .create(x + width - 60, y, 60, ROW, Component.translatable("dglab.menu.wave"),
                        (widget, value) -> waveSet.accept(value)));
    }

    /** A number with a real text field next to the coarse -/+ buttons. */
    private final class NumberRow {
        private final EditBox box;
        private final Supplier<Integer> value;
        private final int min;
        private final int max;
        private final IntConsumer set;

        NumberRow(int x, int y, String key, int min, int max, Supplier<Integer> value, IntConsumer set, String tipKey) {
            this.value = value;
            this.min = min;
            this.max = max;
            this.set = set;
            this.box = new EditBox(font, x, y, 38, ROW, Component.translatable(key));
            box.setMaxLength(9);
            box.setCentered(true);
            box.setValue(Integer.toString(value.get()));
            // Set the responder afterwards so building the screen never rewrites the saved options.
            box.setResponder(text -> {
                try {
                    set.accept(Math.clamp(Integer.parseInt(text.strip()), min, max));
                } catch (NumberFormatException ignored) {
                    // Keep the previous value while the player is still typing a sign or clearing the field.
                }
            });
            if (tipKey != null) box.setTooltip(Tooltip.create(Component.translatable(tipKey)));
        }

        void adjust(int delta) {
            set.accept((int) Math.clamp((long) value.get() + delta, (long) min, (long) max));
            box.setValue(Integer.toString(value.get()));
        }
    }


    private static List<String> collectSources() {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).keySet().stream()
                .map(Identifier::toString).sorted().toList();
    }
}

package cn.dancingsnow.dglab.status;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** A local visual meter. Its state is independent of any paired app. */
final class DamageFeedback {
    final DamageOptions options;
    private LocalPlayer owner;
    private String lastSource, lastWave, previewWave;
    private int points, visibleTicks, decayTicks;

    DamageFeedback(DamageOptions options) {
        this.options = options;
        reset();
    }

    void reset() {
        points = options.base;
        visibleTicks = 0;
        decayTicks = 0;
        lastSource = null;
        lastWave = options.wave;
        previewWave = null;
    }

    void player(LocalPlayer current) {
        if (owner != current) { owner = current; reset(); }
    }

    void hit(LocalPlayer current, String id) {
        player(current);
        if (!options.enabled || !options.includes(id)) return;
        points = Math.clamp(points + options.stepFor(id), 0, 100);
        lastSource = id;
        lastWave = options.waveFor(id);
        visibleTicks = 100;
        decayTicks = 0;
        previewWave = null;
    }

    void preview(LocalPlayer current, String wave) {
        player(current);
        previewWave = wave;
        visibleTicks = 100;
    }

    void tick(LocalPlayer current) {
        player(current);
        if (current == null) return;
        if (visibleTicks > 0) visibleTicks--;
        if (++decayTicks < 20) return;
        decayTicks = 0;
        int gap = options.base - points;
        points += Integer.signum(gap) * Math.min(Math.abs(gap), options.decay);
    }

    Component state() {
        return text("state", points, lastSource == null ? "—" : lastSource,
                lastSource == null ? options.wave : lastWave);
    }

    void draw(GuiGraphicsExtractor g, Font font, boolean right, int y) {
        if (visibleTicks == 0 || g.guiHeight() < y + 47) return;
        boolean preview = previewWave != null;
        String activeWave = preview ? previewWave : lastWave;
        int width = Math.min(166, g.guiWidth() - 12);
        if (width < 92) return;
        int x = right ? g.guiWidth() - width - 6 : 6;
        int color = 0xFF94D9FF;
        g.fill(x, y, x + width, y + 45, 0xE3282A30);
        g.fill(x, y, x + 2, y + 45, color);
        String count = points + "/100";
        int room = width - 20 - font.width(count);
        g.text(font, font.plainSubstrByWidth(text(preview ? "preview_title" : "title").getString(), room),
                x + 6, y + 3, 0xFFF7F7F7);
        g.text(font, count, x + width - 6 - font.width(count), y + 3, color);
        String label = preview ? activeWave : lastSource + " · " + activeWave;
        g.text(font, font.plainSubstrByWidth(label, width - 12), x + 6, y + 15, 0xFFE0E2E6);
        var columns = WavePreviews.ALL.get(activeWave);
        int chartWidth = width - 12;
        int bottom = y + 39;
        g.fill(x + 6, bottom, x + width - 6, bottom + 1, 0xFF717A85);
        for (int i = 0; i < columns.size(); i++) {
            int left = x + 6 + i * chartWidth / columns.size();
            int rightEdge = x + 6 + (i + 1) * chartWidth / columns.size() - 1;
            int height = 1 + columns.get(i) * 2;
            g.fill(left, bottom - height, rightEdge, bottom, color);
        }
        g.fill(x + 6, y + 42, x + width - 6, y + 44, 0xFF49505A);
        g.fill(x + 6, y + 42, x + 6 + chartWidth * points / 100, y + 44, color);
    }

    static Component text(String key, Object... args) {
        return Component.translatable("dglab.feedback." + key, args);
    }
}

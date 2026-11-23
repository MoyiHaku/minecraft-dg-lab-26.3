package com.lumoren.dglabcraft.hud;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/*
 * Ported from DGLab Craft 1.1.4's DGLabCraftHUD, OverlayLayout and
 * WaveformPreview.pulseBars (bilbillm / Lumoren). 2026-11-21 changes:
 * use health history, four anchored corners, and the 26.3 extraction API.
 * SPDX-License-Identifier: GPL-3.0-only
 */
final class HudPanel {
    private static final int BACKGROUND = 0xD9101014;
    private static final int GOLD = 0xFFE8D57A;
    private static final int TEXT = 0xFFEDE6C8;
    private static final int RED = 0xFFFF9492;
    private static final DecimalFormat AMOUNT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    static Component label(String key) {
        return Component.translatable("hud.dglabcraft_hud." + key);
    }

    static void draw(GuiGraphicsExtractor g, Font font, FeedbackState state,
                     HudPreferences prefs, String environment, boolean preview) {
        boolean showEnvironment = prefs.environment && !environment.isEmpty();
        if (!preview && (prefs.mode == HudPreferences.Mode.OFF || (prefs.mode == HudPreferences.Mode.EVENTS
                && !state.hasHit() && !state.isLow() && !showEnvironment))) return;
        Component name = preview ? label("preview") : state.hasHit() ? label(state.cause())
                : state.isLow() ? label("low") : label(showEnvironment ? environment : "normal");
        Component detail = state.hasHit() && !preview
                ? Component.translatable("hud.dglabcraft_hud.loss", AMOUNT.format(state.heartsLost()))
                : Component.empty();
        Component foot = preview ? label("preview_hint") : state.isLow() && state.hasHit() ? label("low")
                : showEnvironment && (state.hasHit() || state.isLow()) ? label(environment) : Component.empty();
        int width = Math.min(g.guiWidth() - 10, Math.max(112,
                Math.max(font.width(name) + font.width(detail) + 20, font.width(foot) + 12)));
        int height = foot.getString().isEmpty() ? 29 : 40;
        boolean right = prefs.corner == HudPreferences.Corner.TOP_RIGHT || prefs.corner == HudPreferences.Corner.BOTTOM_RIGHT;
        boolean bottom = prefs.corner == HudPreferences.Corner.BOTTOM_LEFT || prefs.corner == HudPreferences.Corner.BOTTOM_RIGHT;
        int x = right ? g.guiWidth() - width - 5 : 5;
        int y = bottom ? Math.max(5, g.guiHeight() - height - 49) : 5;
        int accent = !preview && (state.hasHit() || state.isLow()) ? RED : GOLD;
        g.fill(x, y, x + width, y + height, BACKGROUND);
        g.fill(x, y, x + 2, y + height, accent);
        g.text(font, name, x + 6, y + 4, TEXT, false);
        g.text(font, detail, x + width - 6 - font.width(detail), y + 4, RED, false);
        int base = y + 24;
        g.fill(x + 6, base, x + width - 6, base + 1, 0xFF726C58);
        int count = Math.min(FeedbackState.HISTORY, (width - 12) / 2);
        for (int i = 0; i < count; i++) {
            int a = preview ? previewBar(i) : state.sampleFromEnd(i);
            if (a == 0) continue;
            int barX = x + width - 7 - 2 * i;
            int barHeight = Math.clamp(a * 9 / 100, 2, 9);
            int alpha = 120 + a * 135 / 100;
            g.fill(barX, base - barHeight, barX + 1, base, (alpha << 24) | (accent & 0x00FFFFFF));
        }
        if (!foot.getString().isEmpty()) g.text(font, foot, x + 6, y + 28, TEXT, false);
    }

    private static int previewBar(int i) {
        return switch (i % 20) {
            case 3 -> 85;
            case 2, 8 -> 48;
            case 1, 7 -> 30;
            default -> 0;
        };
    }
}

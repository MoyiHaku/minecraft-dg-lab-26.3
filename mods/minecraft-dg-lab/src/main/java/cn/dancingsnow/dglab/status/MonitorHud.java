package cn.dancingsnow.dglab.status;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * Port of the upstream QrCodeHUD / QRCodeUtil and OverlayHUD concepts and two-channel layout.
 * Changed for Minecraft 26.3, native Fabric HUD extraction and local app readings on 2026-11-21.
 * Upstream: DancingSnow0517/Minecraft-DG-LAB. SPDX-License-Identifier: AGPL-3.0-only
 */
final class MonitorHud {
    private static final int GOLD = 0xFFFFE99D;
    private static final int TEXT = 0xFFF7F5EE;
    private static final int GREEN = 0xFF88E5A1;
    private static final int BACK = 0xE32A2A2A;
    private String qrText;
    private final List<Bar> bars = new ArrayList<>();
    private int cells;
    private record Bar(int x, int y, int end) {}

    void clearQr() {
        qrText = null;
        bars.clear();
        cells = 0;
    }

    static BitMatrix matrix(String text) throws WriterException {
        return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, Map.of(EncodeHintType.MARGIN, 4));
    }

    private void prepareQr(String text) {
        if (text.equals(qrText)) return;
        clearQr();
        try {
            BitMatrix matrix = matrix(text);
            cells = matrix.getWidth();
            for (int y = 0; y < cells; y++) {
                for (int x = 0; x < cells;) {
                    if (!matrix.get(x, y)) { x++; continue; }
                    int start = x++;
                    while (x < cells && matrix.get(x, y)) x++;
                    bars.add(new Bar(start, y, x));
                }
            }
            qrText = text;
        } catch (WriterException ex) {
            throw new IllegalStateException("Cannot encode local pairing link", ex);
        }
    }

    void draw(GuiGraphicsExtractor g, Font font, ReadOnlySession.View view, HudOptions prefs) {
        if (view.phase() == ReadOnlySession.Phase.PAIRING) {
            prepareQr(view.qrText());
            // Reserve the right half for Minecraft notification toasts and the lower area for chat.
            int available = Math.min((g.guiWidth() - 12) / 2, (g.guiHeight() - 12) * 3 / 5);
            int scale = Math.min(4, available / cells);
            if (scale < 1) return;
            int size = cells * scale;
            int x = 6, y = 6;
            g.fill(x, y, x + size, y + size, 0xFFFFFFFF);
            for (Bar b : bars) g.fill(x + b.x * scale, y + b.y * scale, x + b.end * scale,
                    y + (b.y + 1) * scale, 0xFF000000);
            int footer = y + size;
            g.fill(x, footer, x + size, footer + 24, BACK);
            g.centeredText(font, MonitorClient.tr("qr"), x + size / 2, footer + 2, TEXT);
            g.centeredText(font, view.address(), x + size / 2, footer + 13, GOLD);
            return;
        }
        if (qrText != null) clearQr();
        if (view.phase() != ReadOnlySession.Phase.CONNECTED || !prefs.visible) return;
        Strength s = view.strength();
        Component title = MonitorClient.tr("title");
        Component waiting = MonitorClient.tr("waiting");
        int width = Math.max(114, Math.max(font.width(title), s == null ? font.width(waiting) : 0) + 14);
        int x = prefs.right ? g.guiWidth() - width - 6 : 6;
        int y = 6, height = s == null ? 29 : 40;
        int border = s != null && ((s.aMax() > 0 && s.a() >= s.aMax())
                || (s.bMax() > 0 && s.b() >= s.bMax())) ? 0xFFDB5471 : GOLD;
        g.fill(x, y, x + width, y + height, BACK);
        g.fill(x, y, x + 2, y + height, border);
        g.centeredText(font, title, x + width / 2, y + 3, TEXT);
        if (s == null) {
            g.centeredText(font, waiting, x + width / 2, y + 16, GOLD);
        } else {
            int aX = x + width / 4 + 1, bX = x + width * 3 / 4;
            g.centeredText(font, "A", aX, y + 15, GREEN);
            g.centeredText(font, "B", bX, y + 15, GREEN);
            g.centeredText(font, value(s.a(), s.aMax(), prefs.showMax), aX, y + 27, TEXT);
            g.centeredText(font, value(s.b(), s.bMax(), prefs.showMax), bX, y + 27, TEXT);
        }
    }

    private static String value(int current, int maximum, boolean showMax) {
        return showMax ? current + " / " + maximum : Integer.toString(current);
    }
}

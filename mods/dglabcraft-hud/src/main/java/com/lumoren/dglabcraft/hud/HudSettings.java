package com.lumoren.dglabcraft.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class HudSettings extends Screen {
    private final HudPreferences prefs;
    private final Runnable preview;

    HudSettings(HudPreferences preferences, Runnable preview) {
        super(HudPanel.label("settings"));
        this.prefs = preferences;
        this.preview = preview;
    }

    @Override
    protected void init() {
        int first = height / 2 - 55;
        row(first, modeLabel(), button -> {
            var modes = HudPreferences.Mode.values();
            prefs.mode = modes[(prefs.mode.ordinal() + 1) % modes.length];
            prefs.save();
            button.setMessage(modeLabel());
        });
        row(first + 24, cornerLabel(), button -> {
            var corners = HudPreferences.Corner.values();
            prefs.corner = corners[(prefs.corner.ordinal() + 1) % corners.length];
            prefs.save();
            button.setMessage(cornerLabel());
        });
        row(first + 48, environmentLabel(), button -> {
            prefs.environment = !prefs.environment;
            prefs.save();
            button.setMessage(environmentLabel());
        });
        row(first + 72, HudPanel.label("show_preview"), button -> {
            preview.run();
            onClose();
        });
        row(first + 100, CommonComponents.GUI_DONE, button -> onClose());
    }

    private void row(int y, Component label, Button.OnPress action) {
        addRenderableWidget(Button.builder(label, action).bounds(width / 2 - 98, y, 196, 20).build());
    }

    private Component modeLabel() {
        return Component.translatable("hud.dglabcraft_hud.mode", HudPanel.label(HudPreferences.key(prefs.mode)));
    }
    private Component cornerLabel() {
        return Component.translatable("hud.dglabcraft_hud.corner", HudPanel.label(HudPreferences.key(prefs.corner)));
    }
    private Component environmentLabel() {
        return Component.translatable("hud.dglabcraft_hud.environment", prefs.environment ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, height / 2 - 77, 0xFFEDE6C8);
    }
}

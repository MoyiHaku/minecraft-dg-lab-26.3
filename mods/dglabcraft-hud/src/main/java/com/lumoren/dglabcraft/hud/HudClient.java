package com.lumoren.dglabcraft.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.LoggerFactory;

public final class HudClient implements ClientModInitializer {
    private final HudPreferences preferences = new HudPreferences();
    private final FeedbackState feedback = new FeedbackState();
    private LocalPlayer lastPlayer;
    private ClientLevel lastWorld;
    private KeyMapping settingsKey;
    private String environment = "";
    private int previewTicks;

    @Override
    public void onInitializeClient() {
        preferences.read();
        KeyMapping.Category category = KeyMapping.Category.register(id("main"));
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dglabcraft_hud.settings", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("indicators"), (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player == lastPlayer && !mc.player.isSpectator()
                    && mc.gui.screen() == null) {
                HudPanel.draw(graphics, mc.font, feedback, preferences, environment, previewTicks > 0);
            }
        });
        LoggerFactory.getLogger("DGLabCraft HUD").info("Loaded 26.3 client HUD");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("dglabcraft_hud", path);
    }

    private void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != lastPlayer || mc.level != lastWorld) {
            feedback.reset();
            environment = "";
            previewTicks = 0;
            lastPlayer = player;
            lastWorld = mc.level;
        }
        while (settingsKey.consumeClick()) {
            if (player != null && mc.gui.screen() == null) {
                mc.gui.setScreen(new HudSettings(preferences, () -> previewTicks = 65));
            }
        }
        if (player == null || mc.level == null || mc.isPaused()) return;
        if (previewTicks > 0) previewTicks--;
        environment = preferences.environment ? environment(player, mc.level) : "";
        feedback.tick(player.getHealth(), player.getMaxHealth(), damageLabel(player));
    }

    private static String environment(LocalPlayer player, ClientLevel level) {
        if (player.isInPowderSnow || player.isFreezing()) return "snow";
        if (player.getInBlockState().is(Blocks.NETHER_PORTAL)) return "portal";
        if (level.dimension().equals(Level.NETHER)) return "nether";
        if (level.dimension().equals(Level.END)) return "end";
        return "";
    }

    private static String damageLabel(LocalPlayer player) {
        DamageSource source = player.getLastDamageSource(2);
        if (source != null) {
            if (source.is(DamageTypeTags.IS_FALL)) return "fall";
            if (source.is(DamageTypeTags.IS_PROJECTILE)) return "projectile";
            if (source.is(DamageTypeTags.IS_FIRE)) return "fire";
            if (source.is(DamageTypeTags.IS_DROWNING)) return "drown";
            if (source.is(DamageTypeTags.IS_FREEZING)) return "freeze";
            if (source.is(DamageTypeTags.IS_EXPLOSION)) return "explosion";
        }
        return "hit";
    }
}

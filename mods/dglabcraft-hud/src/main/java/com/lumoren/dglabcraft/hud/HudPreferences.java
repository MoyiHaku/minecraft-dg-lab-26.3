package com.lumoren.dglabcraft.hud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

final class HudPreferences {
    enum Mode { EVENTS, ALWAYS, OFF }
    enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }
    Mode mode = Mode.EVENTS;
    Corner corner = Corner.TOP_LEFT;
    boolean environment = true;
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("dglabcraft-hud.properties");

    void read() {
        if (!Files.isRegularFile(path)) return;
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            props.load(input);
            mode = readEnum(Mode.class, props.getProperty("mode", ""), mode);
            corner = readEnum(Corner.class, props.getProperty("corner", ""), corner);
            environment = !"false".equalsIgnoreCase(props.getProperty("environment", "true"));
        } catch (IOException | IllegalArgumentException ex) {
            LoggerFactory.getLogger("DGLabCraft HUD").warn("Could not read HUD preferences", ex);
        }
    }

    void save() {
        Path tmp = null;
        try {
            Files.createDirectories(path.getParent());
            tmp = Files.createTempFile(path.getParent(), "dglabcraft-hud-", ".tmp");
            Properties props = new Properties();
            props.setProperty("mode", mode.name());
            props.setProperty("corner", corner.name());
            props.setProperty("environment", Boolean.toString(environment));
            try (OutputStream output = Files.newOutputStream(tmp)) {
                props.store(output, "DGLab Craft HUD for Minecraft 26.3");
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LoggerFactory.getLogger("DGLabCraft HUD").warn("Could not save HUD preferences", ex);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
        }
    }

    static String key(Enum<?> setting) {
        return setting.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type, String name, E fallback) {
        try { return Enum.valueOf(type, name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }
}

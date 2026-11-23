package cn.dancingsnow.dglab.status;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Properties;

final class HudOptions {
    boolean visible = true;
    boolean showMax = true;
    boolean right = true;
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("dglab-status-hud.properties");

    void load() {
        if (!Files.isRegularFile(file)) return;
        Properties settings = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            settings.load(stream);
            visible = !"false".equalsIgnoreCase(settings.getProperty("visible"));
            showMax = !"false".equalsIgnoreCase(settings.getProperty("showMax"));
            right = !"left".equalsIgnoreCase(settings.getProperty("corner"));
        } catch (IOException | IllegalArgumentException ex) {
            MonitorClient.LOG.warn("Could not read HUD settings", ex);
        }
    }

    boolean save() {
        Properties props = new Properties();
        props.setProperty("visible", Boolean.toString(visible));
        props.setProperty("showMax", Boolean.toString(showMax));
        props.setProperty("corner", right ? "right" : "left");
        Path tmp = null;
        try {
            Files.createDirectories(file.getParent());
            tmp = Files.createTempFile(file.getParent(), "dglab-status-", ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) { props.store(out, "Minecraft-DG-LAB / Fabric"); }
            try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException ex) {
            MonitorClient.LOG.warn("Could not save HUD settings", ex);
            return false;
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
        }
    }
}

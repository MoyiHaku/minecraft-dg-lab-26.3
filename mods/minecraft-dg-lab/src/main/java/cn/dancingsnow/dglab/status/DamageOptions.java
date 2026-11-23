package cn.dancingsnow.dglab.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Saved per game instance. A rule inherits every field that the player has not overridden. */
final class DamageOptions {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dglab-damage-hud.json");
    boolean enabled = true;
    boolean allSources = true;
    int base = 20;
    int step = 12;
    int decay = 4;
    String wave = WavePreviews.DEFAULT;
    Map<String, Rule> sources = new TreeMap<>();

    static final class Rule {
        Boolean enabled;
        Integer step;
        String wave;
        boolean empty() { return enabled == null && step == null && wave == null; }
    }

    static DamageOptions load() {
        if (Files.isRegularFile(FILE)) {
            try (var in = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                var options = JSON.fromJson(in, DamageOptions.class);
                if (options != null) { options.normalize(); return options; }
            } catch (IOException | JsonParseException error) { MonitorClient.LOG.warn("Could not load damage HUD settings", error); }
        }
        return new DamageOptions();
    }

    private void normalize() {
        base = Math.clamp(base, 0, 100);
        step = Math.clamp(step, -100, 100);
        decay = Math.clamp(decay, 0, 100);
        if (!WavePreviews.ALL.containsKey(wave)) wave = WavePreviews.DEFAULT;
        Map<String, Rule> fixed = new TreeMap<>();
        if (sources != null) sources.forEach((id, rule) -> {
            var key = Identifier.tryParse(id);
            if (key == null || rule == null) return;
            if (rule.step != null) rule.step = Math.clamp(rule.step, -100, 100);
            if (!WavePreviews.ALL.containsKey(rule.wave)) rule.wave = null;
            if (!rule.empty()) fixed.put(key.toString(), rule);
        });
        sources = fixed;
    }

    boolean includes(String id) {
        Rule rule = sources.get(id);
        return rule != null && rule.enabled != null ? rule.enabled : allSources;
    }

    int stepFor(String id) {
        Rule rule = sources.get(id);
        return rule != null && rule.step != null ? rule.step : step;
    }

    String waveFor(String id) {
        Rule rule = sources.get(id);
        return rule != null && rule.wave != null ? rule.wave : wave;
    }

    Rule edit(String id) { return sources.computeIfAbsent(id, k -> new Rule()); }

    void filter(boolean all) {
        allSources = all;
        sources.values().forEach(rule -> rule.enabled = null);
        sources.values().removeIf(Rule::empty);
    }

    boolean save() {
        Path tmp = null;
        try {
            Files.createDirectories(FILE.getParent());
            tmp = Files.createTempFile(FILE.getParent(), "dglab-damage-", ".tmp");
            Files.writeString(tmp, JSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
            try { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException ex) {
            MonitorClient.LOG.warn("Could not save damage HUD settings", ex);
            return false;
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }
}

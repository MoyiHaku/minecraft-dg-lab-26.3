package cn.dancingsnow.dglab.status;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pixel heights from the bundled set of named thumbnails, in upstream order. */
final class WavePreviews {
    static final Map<String, List<Integer>> ALL = load();
    static final String DEFAULT = ALL.keySet().iterator().next();

    private static Map<String, List<Integer>> load() {
        String file = "/assets/dglab/damage_wave_previews.json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(WavePreviews.class.getResourceAsStream(file)), StandardCharsets.UTF_8)) {
            var previews = new LinkedHashMap<String, List<Integer>>();
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
                var columns = entry.getValue().getAsJsonArray().asList().stream().map(v -> v.getAsInt()).toList();
                if (columns.size() != 18 || columns.stream().anyMatch(h -> h < 0 || h > 6))
                    throw new IllegalArgumentException("Invalid HUD thumbnail: " + entry.getKey());
                previews.put(entry.getKey(), columns);
            });
            if (previews.isEmpty()) throw new IllegalStateException("Empty HUD preset list");
            return Collections.unmodifiableMap(previews);
        } catch (IOException error) { throw new IllegalStateException("Could not read the HUD thumbnails", error); }
    }

    private WavePreviews() {}
}

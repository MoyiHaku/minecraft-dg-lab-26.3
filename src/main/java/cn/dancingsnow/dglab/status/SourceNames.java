package cn.dancingsnow.dglab.status;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Localized names for the damage types bundled with the game, falling back to the raw id. */
final class SourceNames {
    static Component of(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return Component.literal(id);
        if (!"minecraft".equals(key.getNamespace())) return Component.literal(key.toString());
        return Component.translatableWithFallback("dglab.source." + key.getPath(), key.getPath().replace('_', ' '));
    }

    static String text(String id) { return of(id).getString(); }

    private SourceNames() {}
}

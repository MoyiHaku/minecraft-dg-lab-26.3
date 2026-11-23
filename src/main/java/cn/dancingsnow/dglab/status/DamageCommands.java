package cn.dancingsnow.dglab.status;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

final class DamageCommands {
    private static final DynamicCommandExceptionType UNKNOWN_SOURCE =
            new DynamicCommandExceptionType(id -> DamageFeedback.text("unknown_source", id));
    private static final DynamicCommandExceptionType UNKNOWN_WAVE =
            new DynamicCommandExceptionType(wave -> DamageFeedback.text("unknown_wave", wave));
    private final DamageFeedback feedback;
    private final DamageOptions options;

    DamageCommands(DamageFeedback feedback) { this.feedback = feedback; this.options = feedback.options; }

    LiteralArgumentBuilder<FabricClientCommandSource> build() {
        var sources = literal("sources").executes(c -> summary(c.getSource()))
                .then(literal("all").executes(c -> { options.filter(true); return saved(c.getSource()); }))
                .then(literal("none").executes(c -> { options.filter(false); return saved(c.getSource()); }));
        var source = argument("id", IdentifierArgument.id())
                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                        c.getSource().getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).keySet(), b))
                .executes(c -> showSource(c.getSource(), id(c)))
                .then(literal("on").executes(c -> setRule(c, r -> r.enabled = true)))
                .then(literal("off").executes(c -> setRule(c, r -> r.enabled = false)))
                .then(literal("step").then(argument("n", IntegerArgumentType.integer(-100, 100))
                        .executes(c -> setRule(c, r -> r.step = IntegerArgumentType.getInteger(c, "n")))))
                .then(literal("wave").then(waveArg().executes(c -> {
                    String wave = wave(c); return setRule(c, r -> r.wave = wave);
                })))
                .then(literal("reset").executes(c -> {
                    String id = id(c); options.sources.remove(id);
                    return saveSource(c.getSource(), id);
                }));
        return literal("feedback").executes(c -> summary(c.getSource()))
                .then(literal("on").executes(c -> { options.enabled = true; return saved(c.getSource()); }))
                .then(literal("off").executes(c -> { options.enabled = false; feedback.reset(); return saved(c.getSource()); }))
                .then(literal("status").executes(c -> { c.getSource().sendFeedback(feedback.state()); return 1; }))
                .then(sources).then(literal("source").then(source))
                .then(literal("death")
                        .executes(c -> showDeath(c.getSource()))
                        .then(literal("on").executes(c -> { options.deathEnabled = true; return saved(c.getSource()); }))
                        .then(literal("off").executes(c -> { options.deathEnabled = false; return saved(c.getSource()); }))
                        .then(literal("step").then(argument("n", IntegerArgumentType.integer(-100, 100))
                                .executes(c -> {
                                    options.deathStep = IntegerArgumentType.getInteger(c, "n");
                                    return saved(c.getSource());
                                })))
                        .then(literal("wave").then(waveArg().executes(c -> {
                            options.deathWave = wave(c);
                            return saved(c.getSource());
                        })))
                        .then(literal("reset").executes(c -> {
                            options.deathEnabled = true;
                            options.deathStep = 100;
                            options.deathWave = WavePreviews.DEFAULT;
                            return saved(c.getSource());
                        })))
                .then(number("base", 0, v -> { options.base = v; feedback.reset(); }))
                .then(number("step", -100, v -> options.step = v))
                .then(number("decay", 0, v -> options.decay = v))
                .then(literal("waves").executes(c -> listWaves(c.getSource())))
                .then(literal("wave").then(waveArg().executes(c -> { options.wave = wave(c); return saved(c.getSource()); })))
                .then(literal("preview").executes(c -> preview(c.getSource(), options.wave))
                        .then(waveArg().executes(c -> preview(c.getSource(), wave(c)))));
    }

    private int summary(FabricClientCommandSource s) {
        s.sendFeedback(DamageFeedback.text("settings", DamageFeedback.text(options.enabled ? "on" : "off"),
                options.base, options.step, options.decay, options.wave, DamageFeedback.text(options.allSources ? "all" : "none")));
        if (!options.sources.isEmpty()) s.sendFeedback(DamageFeedback.text("overrides", String.join(", ", options.sources.keySet())));
        s.sendFeedback(DamageFeedback.text("help"));
        return 1;
    }

    private int saved(FabricClientCommandSource s) {
        boolean ok = options.save();
        s.sendFeedback(DamageFeedback.text(ok ? "saved" : "temporary"));
        return summary(s);
    }

    private String id(CommandContext<FabricClientCommandSource> c) throws CommandSyntaxException {
        var id = c.getArgument("id", Identifier.class);
        if (!c.getSource().getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).containsKey(id))
            throw UNKNOWN_SOURCE.create(id);
        return id.toString();
    }

    private int showSource(FabricClientCommandSource source, String id) {
        source.sendFeedback(DamageFeedback.text("source", SourceNames.text(id), DamageFeedback.text(options.includes(id) ? "on" : "off"),
                options.stepFor(id), options.waveFor(id)));
        return 1;
    }

    private int showDeath(FabricClientCommandSource source) {
        source.sendFeedback(DamageFeedback.text("death", DamageFeedback.text(options.deathEnabled ? "on" : "off"),
                options.deathStep, options.deathWave));
        return 1;
    }

    private int setRule(CommandContext<FabricClientCommandSource> c, Consumer<DamageOptions.Rule> edit) throws CommandSyntaxException {
        String id = id(c);
        edit.accept(options.edit(id));
        return saveSource(c.getSource(), id);
    }

    private int saveSource(FabricClientCommandSource s, String id) {
        if (!options.save()) s.sendFeedback(DamageFeedback.text("temporary"));
        return showSource(s, id);
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> number(String label, int min, IntConsumer set) {
        return literal(label).then(argument("n", IntegerArgumentType.integer(min, 100)).executes(c -> {
            set.accept(IntegerArgumentType.getInteger(c, "n"));
            return saved(c.getSource());
        }));
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> waveArg() {
        return argument("preset", StringArgumentType.greedyString()).suggests((c, b) ->
                SharedSuggestionProvider.suggest(WavePreviews.ALL.keySet(), b));
    }

    private static String wave(CommandContext<FabricClientCommandSource> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "preset").strip();
        if (!WavePreviews.ALL.containsKey(name)) throw UNKNOWN_WAVE.create(name);
        return name;
    }

    private int listWaves(FabricClientCommandSource source) {
        var choices = Component.empty();
        boolean first = true;
        for (String name : WavePreviews.ALL.keySet()) {
            if (!first) choices.append(" · ");
            first = false;
            choices.append(Component.literal(name).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.SuggestCommand("/dglab feedback wave " + name))));
        }
        source.sendFeedback(DamageFeedback.text("waves", WavePreviews.ALL.size(), choices));
        return 1;
    }

    private int preview(FabricClientCommandSource source, String wave) {
        feedback.preview(source.getPlayer(), wave);
        source.sendFeedback(DamageFeedback.text("preview", wave));
        return 1;
    }
}

package cn.dancingsnow.dglab.status;

import com.mojang.brigadier.CommandDispatcher;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.LoggerFactory;

public final class StatusServer implements ModInitializer {
    // Server-thread only. A new login gets a distinct listener, even when the UUID is reused.
    private final Map<ServerGamePacketListenerImpl, StatusUpload> latest = new IdentityHashMap<>();

    @Override public void onInitialize() {
        DamageNotice.register();
        PayloadTypeRegistry.serverboundPlay().register(StatusUpload.TYPE, StatusUpload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StatusUpload.TYPE, (upload, context) -> {
            var player = context.player();
            var server = context.server();
            server.execute(() -> {
                var current = server.getPlayerList().getPlayer(player.getUUID());
                if (current == null || current.connection != player.connection) return;
                if (upload.sharing()) latest.put(current.connection, upload);
                else latest.remove(current.connection);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> latest.remove(listener));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> latest.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> register(dispatcher));
        LoggerFactory.getLogger("Minecraft-DG-LAB Status").info("Registered server-side status sharing and /dglabserver");
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dglabserver")
            .executes(c -> reply(c.getSource(), msg("help", "DG-LAB 已接入。玩家用 /dglabserver status 查询自己在本服的状态。")))
            .then(Commands.literal("status")
                .executes(c -> {
                    var source = c.getSource();
                    return source.getPlayer() == null
                            ? reply(source, msg("console", "控制台可用 dglabserver list 或 dglabserver status <玩家名>。"))
                            : status(source, source.getPlayer());
                })
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(c -> status(c.getSource(), EntityArgument.getPlayer(c, "player")))))
            .then(Commands.literal("list")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> {
                    var source = c.getSource();
                    String names = source.getServer().getPlayerList().getPlayers().stream()
                            .filter(player -> latest.containsKey(player.connection))
                            .map(player -> player.getGameProfile().name())
                            .sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
                    return reply(source, names.isEmpty() ? msg("empty", "当前没有玩家共享 DG-LAB 读数。")
                            : msg("list", "正在共享的玩家：%s", names));
                })));
    }

    private int status(CommandSourceStack source, ServerPlayer player) {
        String name = player.getGameProfile().name();
        StatusUpload value = latest.get(player.connection);
        if (value == null) return reply(source, msg("private", "%s 当前没有向本服共享读数。", name));
        Strength s = value.reading();
        if (s == null) return reply(source, msg("waiting", "%s 已配对，正在等候 App 的首条读数。", name));
        return reply(source, msg("values", "%s　A %s / %s　B %s / %s（当前值 / 上限）",
                name, s.a(), s.aMax(), s.b(), s.bMax()));
    }

    private static Component msg(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("dglab.server." + key, fallback, args);
    }

    private static int reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
        return 1;
    }
}

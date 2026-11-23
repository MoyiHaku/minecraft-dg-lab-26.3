package cn.dancingsnow.dglab.status;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class MonitorClient implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("Minecraft-DG-LAB Status");
    private final MonitorHud hud = new MonitorHud();
    private final HudOptions options = new HudOptions();
    private DamageFeedback feedback;
    private ReadOnlySession session;
    private ReadOnlySession.Phase announced;
    private boolean sharing;
    private StatusUpload lastUpload;
    private int syncDelay;

    @Override public void onInitializeClient() {
        options.load();
        feedback = new DamageFeedback(DamageOptions.load());
        ClientPlayNetworking.registerGlobalReceiver(DamageNotice.TYPE, (packet, ctx) -> {
            var expected = ctx.player();
            ctx.client().execute(() -> {
                if (expected != null && ctx.client().player == expected)
                    feedback.hit(expected, packet.source().toString());
            });
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> commands(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            feedback.player(null);
            disconnect(false);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> disconnect(false));
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("dglab", "status"), (g, dt) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) return;
            var view = session == null ? null : session.view();
            if (view != null) hud.draw(g, mc.font, view, options);
            if (options.visible && (view == null || view.phase() != ReadOnlySession.Phase.PAIRING)) {
                boolean hasAppPanel = view != null && view.phase() == ReadOnlySession.Phase.CONNECTED;
                feedback.draw(g, mc.font, options.right, hasAppPanel ? 50 : 6);
            }
        });
        LOG.info("Loaded Minecraft-DG-LAB 26.3 read-only status port");
    }

    private void commands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("dglab").executes(c -> info(c.getSource(), "help"))
                .then(literal("help").executes(c -> info(c.getSource(), "help")))
                .then(literal("connect").executes(c -> connect(c.getSource(), null, 0))
                        .then(argument("ipv4", StringArgumentType.word())
                                .executes(c -> connect(c.getSource(), StringArgumentType.getString(c, "ipv4"), 0))
                                .then(argument("port", IntegerArgumentType.integer(1, 65535)).executes(c ->
                                        connect(c.getSource(), StringArgumentType.getString(c, "ipv4"),
                                                IntegerArgumentType.getInteger(c, "port"))))))
                .then(literal("disconnect").executes(c -> {
                    disconnect(true);
                    return info(c.getSource(), "closed");
                }))
                .then(literal("status").executes(c -> {
                    c.getSource().sendFeedback(status());
                    return 1;
                }))
                .then(new DamageCommands(feedback).build())
                .then(literal("share").executes(c -> info(c.getSource(), sharing ? "share_active" : "share_inactive"))
                        .then(literal("on").executes(c -> enableSharing(c.getSource())))
                        .then(literal("off").executes(c -> {
                            stopSharing(true);
                            return info(c.getSource(), "share_off");
                        })))
                .then(literal("hud").executes(c -> info(c.getSource(), "hud_help"))
                        .then(setting("on", o -> o.visible = true)).then(setting("off", o -> o.visible = false))
                        .then(literal("max").then(setting("on", o -> o.showMax = true)).then(setting("off", o -> o.showMax = false)))
                        .then(literal("position").then(setting("right", o -> o.right = true)).then(setting("left", o -> o.right = false)))));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> setting(String name, Consumer<HudOptions> change) {
        return literal(name).executes(c -> {
            change.accept(options);
            return info(c.getSource(), options.save() ? "saved" : "temporary");
        });
    }

    private int connect(FabricClientCommandSource source, String address, int port) {
        if (session != null) {
            var phase = session.view().phase();
            if (phase == ReadOnlySession.Phase.OPENING || phase == ReadOnlySession.Phase.PAIRING || phase == ReadOnlySession.Phase.CONNECTED) {
                source.sendFeedback(status());
                return 1;
            }
        }
        disconnect(true);
        session = new ReadOnlySession(address, port);
        announced = ReadOnlySession.Phase.OPENING;
        return info(source, "opening");
    }

    private int enableSharing(FabricClientCommandSource source) {
        if (!canUpload()) { source.sendError(tr("server_missing")); return 0; }
        if (session == null || terminal(session.view().phase())) connect(source, null, 0);
        sharing = true;
        syncDelay = 0;
        syncToServer(session.view());
        return info(source, "share_on");
    }

    private static boolean terminal(ReadOnlySession.Phase phase) {
        return phase == ReadOnlySession.Phase.CLOSED || phase == ReadOnlySession.Phase.EXPIRED
                || phase == ReadOnlySession.Phase.FAILED;
    }

    private static boolean canUpload() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(StatusUpload.TYPE);
    }

    private void syncToServer(ReadOnlySession.View view) {
        if (!sharing) return;
        if (terminal(view.phase()) || !canUpload()) {
            stopSharing(true);
            return;
        }
        if (view.phase() != ReadOnlySession.Phase.CONNECTED) return;
        if (syncDelay > 0) { syncDelay--; return; }
        syncDelay = 4;
        var upload = view.strength() == null ? StatusUpload.WAITING : new StatusUpload(true, view.strength());
        if (!upload.equals(lastUpload)) {
            ClientPlayNetworking.send(upload);
            lastUpload = upload;
        }
    }

    private void stopSharing(boolean notifyServer) {
        if (notifyServer && lastUpload != null && canUpload()) ClientPlayNetworking.send(StatusUpload.CLEAR);
        sharing = false;
        lastUpload = null;
        syncDelay = 0;
    }

    private void disconnect(boolean notifyServer) {
        stopSharing(notifyServer);
        if (session != null) session.close();
        session = null;
        announced = null;
        hud.clearQr();
    }

    private void tick(Minecraft mc) {
        feedback.tick(mc.player);
        if (session == null || mc.player == null) return;
        var view = session.view();
        syncToServer(view);
        if (view.phase() == announced) return;
        announced = view.phase();
        if (view.phase() != ReadOnlySession.Phase.PAIRING) hud.clearQr();
        mc.player.sendSystemMessage(status());
    }

    private Component status() {
        if (session == null) return tr("closed");
        var v = session.view();
        return switch (v.phase()) {
            case OPENING -> tr("opening");
            case PAIRING -> tr("scan", v.address());
            case EXPIRED -> tr("expired");
            case FAILED -> tr(v.error());
            case CLOSED -> tr("closed");
            case CONNECTED -> {
                var s = v.strength();
                yield s == null ? tr("paired") : tr("values", s.a(), s.aMax(), s.b(), s.bMax());
            }
        };
    }

    static Component tr(String key, Object... values) { return Component.translatable("dglab.status." + key, values); }
    private static int info(FabricClientCommandSource source, String key) { source.sendFeedback(tr(key)); return 1; }
}

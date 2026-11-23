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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class MonitorClient implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("Minecraft-DG-LAB-Connect");
    private final MonitorHud hud = new MonitorHud();
    private final HudOptions options = new HudOptions();
    private DeviceLink device;
    private DamageFeedback feedback;
    private ReadOnlySession session;
    private ReadOnlySession.Phase announced;
    private boolean sharing;
    private StatusUpload lastUpload;
    private int syncDelay;

    @Override public void onInitializeClient() {
        options.load();
        var damage = DamageOptions.load();
        device = new DeviceLink(damage);
        feedback = new DamageFeedback(damage, device);
        ClientPlayNetworking.registerGlobalReceiver(DamageNotice.TYPE, (packet, ctx) -> {
            var expected = ctx.player();
            ctx.client().execute(() -> {
                if (expected != null && ctx.client().player == expected)
                    feedback.hit(expected, packet.source().toString());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(DeathNotice.TYPE, (packet, ctx) -> {
            var expected = ctx.player();
            ctx.client().execute(() -> {
                if (expected != null && ctx.client().player == expected)
                    feedback.death(expected, packet.source().toString());
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
            // The damage panel mirrors what is sent to the device, so it only exists while paired.
            if (options.visible && feedback.options.enabled && view != null
                    && view.phase() == ReadOnlySession.Phase.CONNECTED) {
                feedback.draw(g, mc.font, options.right, 50);
            }
        });
        LOG.info("Loaded Minecraft-DG-LAB-Connect for Fabric 26.3");
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
                .then(literal("menu").executes(c -> {
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().setScreenAndShow(new DglabConfigScreen(feedback, device, options)));
                    return 1;
                }))
                .then(literal("device").executes(c -> info(c.getSource(), "device_help"))
                        .then(literal("on").executes(c -> {
                            feedback.options.device = true;
                            return savedDamage(c.getSource());
                        }))
                        .then(literal("off").executes(c -> {
                            feedback.options.device = false;
                            device.stop();
                            return savedDamage(c.getSource());
                        }))
                        .then(number2("channel", 1, 3, v -> { feedback.options.deviceChannel = v; device.stop(); }))
                        .then(number2("frames", 1, 3, v -> { feedback.options.deviceFrames = v; device.stop(); }))
                        .then(number2("queue", 0, Integer.MAX_VALUE, v -> feedback.options.queueSeconds = v))
                        .then(literal("idle")
                                .then(literal("on").executes(c -> idle(c.getSource(), true)))
                                .then(literal("off").executes(c -> idle(c.getSource(), false)))))
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

    private LiteralArgumentBuilder<FabricClientCommandSource> number2(String label, int min, int max, IntConsumer set) {
        return literal(label).then(argument("n", IntegerArgumentType.integer(min, max)).executes(c -> {
            set.accept(IntegerArgumentType.getInteger(c, "n"));
            return savedDamage(c.getSource());
        }));
    }

    private int savedDamage(FabricClientCommandSource source) {
        return info(source, feedback.options.save() ? "saved" : "temporary");
    }

    private int idle(FabricClientCommandSource source, boolean on) {
        feedback.options.deviceClearIdle = on;
        if (on) device.stop();
        return savedDamage(source);
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
        device.session(session);
        feedback.reset();
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
        device.session(null);
        feedback.reset();
        if (session != null) session.close();
        session = null;
        announced = null;
        hud.clearQr();
    }

    private void tick(Minecraft mc) {
        feedback.tick(mc.player);
        device.tick();
        if (session == null || mc.player == null) return;
        var view = session.view();
        if (terminal(view.phase())) {
            device.session(null);
            feedback.reset();
        }
        syncToServer(view);
        if (view.phase() != announced) {
            announced = view.phase();
            if (view.phase() != ReadOnlySession.Phase.PAIRING) hud.clearQr();
            Component message = status();
            if (view.phase() == ReadOnlySession.Phase.CONNECTED)
                message = message.copy().append(" ").append(Component.translatable("dglab.status.open_menu")
                        .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent.RunCommand("/dglab menu"))));
            mc.player.sendSystemMessage(message);
        }
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

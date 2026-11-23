package cn.dancingsnow.dglab.status;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.ScheduledFuture;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Private, one-phone telemetry receiver. Only the V3 pairing and keepalive replies leave this class. */
final class ReadOnlySession {
    enum Phase { OPENING, PAIRING, CONNECTED, CLOSED, EXPIRED, FAILED }
    record View(Phase phase, String address, String qrText, Strength strength, String error) {}
    private record Wire(String type, String clientId, String targetId, String message) {}
    private static final Gson JSON = new Gson();
    private static final String QR_PREFIX = "https://www.dungeon-lab.com/app-download.php#DGLAB-SOCKET#";
    private static final int MAX_CHILDREN = 6;

    private final String token = UUID.randomUUID().toString();
    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1,
            Thread.ofPlatform().daemon(true).name("dglab-status-", 0).factory(), NioIoHandler.newFactory());
    private final ChannelGroup channels = new DefaultChannelGroup(group.next(), true);
    private volatile View view = new View(Phase.OPENING, "", "", null, "");
    private Channel listener;
    private Channel boundPhone;
    private ScheduledFuture<?> deadline;
    private boolean stopped;

    ReadOnlySession(String requestedAddress, int port) {
        group.next().execute(() -> start(requestedAddress, port));
    }

    View view() { return view; }

    private void start(String requestedAddress, int port) {
        try {
            InetAddress ip = LanAddress.choose(requestedAddress);
            WebSocketServerProtocolConfig upgrade = WebSocketServerProtocolConfig.newBuilder().websocketPath("/")
                    .maxFramePayloadLength(2048).allowExtensions(false).handshakeTimeoutMillis(8000).build();
            ChannelFuture binding = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel ch) {
                            if (channels.size() >= MAX_CHILDREN + 1) { ch.close(); return; }
                            channels.add(ch);
                            ch.pipeline().addLast(new IdleStateHandler(100, 30, 0))
                                .addLast(new HttpServerCodec(256, 4096, 2048))
                                .addLast(new HttpObjectAggregator(2048))
                                .addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                    @Override protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
                                        String path = req.uri();
                                        // Some V3 apps remove the final token from the upgrade URL and present it at bind time.
                                        if (req.method() != HttpMethod.GET || !(path.equals("/") || path.equals("/" + token))) {
                                            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                    HttpResponseStatus.NOT_FOUND)).addListener(ChannelFutureListener.CLOSE);
                                            return;
                                        }
                                        req.setUri("/");
                                        ctx.fireChannelRead(req.retain());
                                    }
                                }).addLast(new WebSocketServerProtocolHandler(upgrade)).addLast(new Phone());
                        }
                    }).bind(ip, port);
            channels.add(binding.channel());
            binding.addListener((ChannelFutureListener) result -> {
                if (!result.isSuccess()) { finish(Phase.FAILED, "bind_failed"); return; }
                publishQr(result.channel(), ip);
            });
        } catch (IOException | RuntimeException ex) {
            finish(Phase.FAILED, "no_address");
        }
    }

    private synchronized void publishQr(Channel channel, InetAddress ip) {
        if (stopped) return;
        listener = channel;
        int port = ((InetSocketAddress) channel.localAddress()).getPort();
        String address = ip.getHostAddress() + ":" + port;
        view = new View(Phase.PAIRING, address, QR_PREFIX + "ws://" + address + "/" + token, null, "");
        deadline = group.next().schedule(() -> finish(Phase.EXPIRED, ""), 3, TimeUnit.MINUTES);
    }

    private synchronized boolean accept(Channel phone) {
        if (stopped || boundPhone != null || view.phase() != Phase.PAIRING) return false;
        boundPhone = phone;
        deadline.cancel(false);
        view = new View(Phase.CONNECTED, view.address(), "", null, "");
        listener.close();
        for (Channel other : channels) if (other != listener && other != phone) other.close();
        return true;
    }

    private synchronized void update(Channel phone, Strength strength) {
        if (!stopped && phone == boundPhone) view = new View(Phase.CONNECTED, view.address(), "", strength, "");
    }

    private synchronized void disconnected(Channel phone) {
        if (phone == boundPhone) finish(Phase.CLOSED, "");
    }

    void close() { finish(Phase.CLOSED, ""); }

    private synchronized void finish(Phase phase, String error) {
        if (stopped) return;
        stopped = true;
        if (deadline != null) deadline.cancel(false);
        boundPhone = null;
        view = new View(phase, "", "", null, error);
        channels.close();
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
    }

    private final class Phone extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        private final String phoneId = UUID.randomUUID().toString();
        private boolean paired;
        private ScheduledFuture<?> bindTimeout;

        @Override public void channelActive(ChannelHandlerContext ctx) throws Exception {
            bindTimeout = ctx.executor().schedule(() -> { if (!paired) ctx.close(); }, 12, TimeUnit.SECONDS);
            super.channelActive(ctx);
        }

        @Override public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
            if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                ctx.writeAndFlush(new TextWebSocketFrame(JSON.toJson(new Wire("bind", phoneId, "", "targetId"))));
            } else if (event instanceof IdleStateEvent idle) {
                if (idle.state() == IdleState.READER_IDLE) ctx.close();
                else if (paired && idle.state() == IdleState.WRITER_IDLE) {
                    ctx.write(new PingWebSocketFrame());
                    ctx.writeAndFlush(new TextWebSocketFrame(JSON.toJson(new Wire("heartbeat", token, phoneId, "200"))));
                }
            } else super.userEventTriggered(ctx, event);
        }

        @Override protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            Wire incoming;
            try { incoming = JSON.fromJson(frame.text(), Wire.class); }
            catch (JsonParseException ex) { return; }
            if (incoming == null || incoming.type() == null || incoming.message() == null) return;
            boolean matches = (token.equals(incoming.clientId()) && phoneId.equals(incoming.targetId()))
                    || (phoneId.equals(incoming.clientId()) && token.equals(incoming.targetId()));
            if (!paired) {
                if (!"bind".equals(incoming.type())) return;
                if (!matches || !("DGLAB".equals(incoming.message()) || "targetId".equals(incoming.message()))
                        || !accept(ctx.channel())) { ctx.close(); return; }
                paired = true;
                bindTimeout.cancel(false);
                ctx.writeAndFlush(new TextWebSocketFrame(JSON.toJson(new Wire("bind", incoming.clientId(), incoming.targetId(), "200"))));
                return;
            }
            if (!matches) return;
            if ("break".equals(incoming.type())) { ctx.close(); return; }
            if (!"msg".equals(incoming.type())) return;
            Strength reading = Strength.parse(incoming.message());
            if (reading != null) update(ctx.channel(), reading);
        }

        @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (bindTimeout != null) bindTimeout.cancel(false);
            disconnected(ctx.channel());
            super.channelInactive(ctx);
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { ctx.close(); }
    }
}

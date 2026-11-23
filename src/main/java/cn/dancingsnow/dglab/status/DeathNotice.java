package cn.dancingsnow.dglab.status;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The death event lives on its own channel so a client built before this feature simply never
 * receives it ({@code canSend} is false) instead of mis-parsing a longer payload.
 */
record DeathNotice(Identifier source) implements CustomPacketPayload {
    static final Type<DeathNotice> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dglab", "own_death_hud"));
    static final StreamCodec<ByteBuf, DeathNotice> CODEC = Identifier.STREAM_CODEC.map(DeathNotice::new, DeathNotice::source);

    @Override public Type<DeathNotice> type() { return TYPE; }
}

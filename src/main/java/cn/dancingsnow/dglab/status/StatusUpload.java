package cn.dancingsnow.dglab.status;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One client-to-server message. The authenticated game connection identifies the owner. */
record StatusUpload(boolean sharing, Strength reading) implements CustomPacketPayload {
    static final Type<StatusUpload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dglab", "status_v1"));
    static final StatusUpload CLEAR = new StatusUpload(false, null);
    static final StatusUpload WAITING = new StatusUpload(true, null);
    static final StreamCodec<ByteBuf, StatusUpload> CODEC = StreamCodec.of(StatusUpload::write, StatusUpload::read);

    private static void write(ByteBuf out, StatusUpload upload) {
        if (upload.reading == null) {
            out.writeByte(upload.sharing ? 1 : 0);
            return;
        }
        Strength s = upload.reading;
        out.writeByte(2);
        out.writeByte(s.a());
        out.writeByte(s.b());
        out.writeByte(s.aMax());
        out.writeByte(s.bMax());
    }

    private static StatusUpload read(ByteBuf in) {
        return switch (in.readUnsignedByte()) {
            case 0 -> CLEAR;
            case 1 -> WAITING;
            case 2 -> new StatusUpload(true, new Strength(value(in), value(in), value(in), value(in)));
            default -> throw new DecoderException("Unknown DG-LAB status message");
        };
    }

    private static int value(ByteBuf in) {
        int reading = in.readUnsignedByte();
        if (reading > 200) throw new DecoderException("Invalid DG-LAB channel reading");
        return reading;
    }

    @Override public Type<StatusUpload> type() { return TYPE; }
}

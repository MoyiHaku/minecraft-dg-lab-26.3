package cn.dancingsnow.dglab.status;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A game event addressed only to the player who took the hit. */
record DamageNotice(Identifier source) implements CustomPacketPayload {
    static final Type<DamageNotice> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dglab", "own_damage_hud"));
    private static final StreamCodec<ByteBuf, DamageNotice> CODEC = Identifier.STREAM_CODEC.map(DamageNotice::new, DamageNotice::source);

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, original, taken, blocked) -> {
            // Fabric reports the remaining damage after blocking. A partial block can still hurt the player.
            if (taken > 0) notifyPlayer(entity, source);
        });
        // AFTER_DAMAGE excludes fatal hits.
        ServerLivingEntityEvents.AFTER_DEATH.register(DamageNotice::notifyPlayer);
    }

    private static void notifyPlayer(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer player && ServerPlayNetworking.canSend(player, TYPE))
            source.typeHolder().unwrapKey().ifPresent(key -> ServerPlayNetworking.send(player, new DamageNotice(key.identifier())));
    }

    @Override public Type<DamageNotice> type() { return TYPE; }
}

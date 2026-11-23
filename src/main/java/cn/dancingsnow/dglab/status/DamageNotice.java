package cn.dancingsnow.dglab.status;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A game event addressed only to the player who took the hit. */
record DamageNotice(Identifier source) implements CustomPacketPayload {
    static final Type<DamageNotice> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dglab", "own_damage_hud"));
    private static final StreamCodec<ByteBuf, DamageNotice> CODEC =
            Identifier.STREAM_CODEC.map(DamageNotice::new, DamageNotice::source);

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeathNotice.TYPE, DeathNotice.CODEC);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, original, taken, blocked) -> {
            // Fabric reports the remaining damage after blocking. A partial block can still hurt the player.
            if (taken > 0) notifyPlayer(entity, source);
        });
        // AFTER_DAMAGE is skipped once the entity is dying, so deaths have their own channel.
        ServerLivingEntityEvents.AFTER_DEATH.register(DamageNotice::notifyDeath);
    }

    private static void notifyPlayer(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) return;
        Identifier id = id(source);
        if (ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, new DamageNotice(id));
    }

    private static void notifyDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) return;
        Identifier id = id(source);
        if (ServerPlayNetworking.canSend(player, DeathNotice.TYPE)) ServerPlayNetworking.send(player, new DeathNotice(id));
    }

    /** Every die() path lands here; a source without a registry key still produces an event. */
    private static Identifier id(DamageSource source) {
        return source.typeHolder().unwrapKey().map(ResourceKey::identifier)
                .orElse(Identifier.fromNamespaceAndPath("dglab", "unknown"));
    }

    @Override public Type<DamageNotice> type() { return TYPE; }
}

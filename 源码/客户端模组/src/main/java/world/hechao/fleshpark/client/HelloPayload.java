package world.hechao.fleshpark.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 告诉服务端光影开没开：开着的话手电在光影里算，服务端就不用摆光方块了。 */
record HelloPayload(boolean shader) implements CustomPacketPayload {
    static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fleshpark", "client"));
    static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of((buf, payload) -> buf.writeBoolean(payload.shader), buf -> new HelloPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

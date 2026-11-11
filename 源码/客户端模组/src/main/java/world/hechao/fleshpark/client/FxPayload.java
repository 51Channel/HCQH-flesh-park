package world.hechao.fleshpark.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/** 服务端发来的一条演出指令，内容就是一行字。插件那边用普通的 plugin message 发。 */
record FxPayload(String text) implements CustomPacketPayload {
    static final Type<FxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fleshpark", "fx"));
    static final StreamCodec<FriendlyByteBuf, FxPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.text.getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new FxPayload(new String(bytes, StandardCharsets.UTF_8));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

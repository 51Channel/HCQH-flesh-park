package world.hechao.fleshpark.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import world.hechao.fleshpark.client.LeanVertices;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 肉和人形单独一块缓冲，顶点按原版格式写（见 LeanVertices）。画面那一趟和光影自己的阴影那一趟都管。
 * 和 TextBatchMixin 一样，攒到这一段结束一起画。
 */
@Mixin(MultiBufferSource.BufferSource.class)
abstract class LeanVertexMixin {
    @Shadow
    @Final
    protected Map<RenderType, BufferBuilder> startedBuilders;

    @Shadow
    public abstract void endBatch(RenderType type);

    @Unique
    private final Map<RenderType, ByteBufferBuilder> fleshpark$leanBuffers = new LinkedHashMap<>();
    @Unique
    private final Set<BufferBuilder> fleshpark$leanBuilders = Collections.newSetFromMap(new IdentityHashMap<>());
    @Unique
    private int fleshpark$drawing = -1;

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void fleshpark$lean(RenderType type, CallbackInfoReturnable<VertexConsumer> info) {
        if (!LeanVertices.wants(type)) return;
        BufferBuilder builder = startedBuilders.get(type);
        if (builder == null) {
            ByteBufferBuilder bytes = fleshpark$leanBuffers.computeIfAbsent(type, t -> new ByteBufferBuilder(t.bufferSize()));
            builder = LeanVertices.builder(bytes, type);
            fleshpark$leanBuilders.add(builder);
            startedBuilders.put(type, builder);
        }
        info.setReturnValue(builder);
    }

    @Inject(method = "endLastBatch", at = @At("HEAD"))
    private void fleshpark$flushLean(CallbackInfo info) {
        if (fleshpark$leanBuffers.isEmpty()) return;
        for (RenderType type : fleshpark$leanBuffers.keySet()) endBatch(type);
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", at = @At("HEAD"))
    private void fleshpark$drawPlain(RenderType type, BufferBuilder builder, CallbackInfo info) {
        if (fleshpark$leanBuilders.remove(builder)) fleshpark$drawing = LeanVertices.plain() ? 1 : 0;
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", at = @At("RETURN"))
    private void fleshpark$drawn(RenderType type, BufferBuilder builder, CallbackInfo info) {
        if (fleshpark$drawing < 0) return;
        LeanVertices.restore(fleshpark$drawing == 1);
        fleshpark$drawing = -1;
    }
}

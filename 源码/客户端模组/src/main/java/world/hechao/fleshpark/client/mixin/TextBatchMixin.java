package world.hechao.fleshpark.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
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

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字攒成一批再画。
 * 原版里字的渲染类型不在固定缓冲里，和别的东西共用一块缓冲：画字的时候每换一张字形贴图（中文字分散在好几张上）、
 * 每画一块字底，就把上一段先画出去，一个字一个绘制调用。光影下每个绘制调用都要等显卡把上一个画完才能往同一块缓冲里写，
 * 身边几十块牌子、屏幕、评价机的字，一帧就是上千次等待。实测渲染线程九成的时间耗在这上面。
 * 这里给每种字的渲染类型单独一块缓冲，整段攒着，到这一段结束（endLastBatch / endBatch）一起画，每种只画一次。
 * 只管世界里用的那个主缓冲，界面和光影自己的阴影缓冲不动。
 */
@Mixin(MultiBufferSource.BufferSource.class)
abstract class TextBatchMixin {
    @Shadow
    @Final
    protected Map<RenderType, BufferBuilder> startedBuilders;

    @Unique
    private final Map<RenderType, ByteBufferBuilder> fleshpark$textBuffers = new LinkedHashMap<>();

    @Unique
    private static final Map<RenderType, Boolean> fleshpark$isText = new IdentityHashMap<>();

    @Shadow
    public abstract void endBatch(RenderType type);

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void fleshpark$batchText(RenderType type, CallbackInfoReturnable<VertexConsumer> info) {
        if (!fleshpark$text(type) || (Object) this != Minecraft.getInstance().renderBuffers().bufferSource()) return;
        BufferBuilder builder = startedBuilders.get(type);
        if (builder == null) {
            ByteBufferBuilder bytes = fleshpark$textBuffers.computeIfAbsent(type, t -> new ByteBufferBuilder(t.bufferSize()));
            builder = new BufferBuilder(bytes, type.mode(), type.format());
            startedBuilders.put(type, builder);
        }
        info.setReturnValue(builder);
    }

    @Inject(method = "endLastBatch", at = @At("HEAD"))
    private void fleshpark$flushText(CallbackInfo info) {
        if (fleshpark$textBuffers.isEmpty()) return;
        for (RenderType type : fleshpark$textBuffers.keySet()) endBatch(type);
    }

    @Unique
    private static boolean fleshpark$text(RenderType type) {
        Boolean known = fleshpark$isText.get(type);
        if (known == null) {
            known = ((RenderTypeAccess) type).fleshpark$name().startsWith("text");
            fleshpark$isText.put(type, known);
        }
        return known;
    }
}

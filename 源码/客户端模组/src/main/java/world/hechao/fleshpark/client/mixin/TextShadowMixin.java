package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import world.hechao.fleshpark.client.ShadowPass;

/**
 * 手电的阴影图里不画字。
 * 光影画阴影图时把身边所有实体再画一遍，字也在里面，用的是光影自己的缓冲，TextBatchMixin 管不到：
 * 每个字一个绘制调用、每次都等显卡，实测渲染线程八成的时间在这儿。字薄得像一张纸，投不出看得见的影子，不画也看不出差别。
 */
@Mixin(TextFeatureRenderer.class)
abstract class TextShadowMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fleshpark$skipInShadow(SubmitNodeCollection submits, MultiBufferSource.BufferSource buffers, CallbackInfo info) {
        if (ShadowPass.active()) info.cancel();
    }
}

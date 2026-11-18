package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 发疯最后那几秒：视野往外撑开，画面边上被拉长、往里吸。 */
@Mixin(GameRenderer.class)
abstract class FovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fleshpark$widen(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Float> info) {
        float k = world.hechao.fleshpark.client.CamAccess.fov();
        if (k != 1) info.setReturnValue(Math.min(165f, info.getReturnValueF() * k));
    }
}

package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 放入场片和收尾的时候，鼠标转不动视角：镜头是排好的。 */
@Mixin(MouseHandler.class)
abstract class CamMouseMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void fleshpark$lockDuringCam(double movement, CallbackInfo info) {
        if (world.hechao.fleshpark.client.CamAccess.active()) info.cancel();
    }
}

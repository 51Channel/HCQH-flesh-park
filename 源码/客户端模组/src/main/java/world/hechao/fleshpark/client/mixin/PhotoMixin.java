package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import world.hechao.fleshpark.client.Snaps;

/** 世界画完、界面还没画：缆车拍照点那张纪念照在这时候截，画面里没有快捷栏和字。 */
@Mixin(GameRenderer.class)
abstract class PhotoMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"), require = 0)
    private void fleshpark$photo(DeltaTracker delta, CallbackInfo info) {
        Snaps.afterWorld();
    }
}

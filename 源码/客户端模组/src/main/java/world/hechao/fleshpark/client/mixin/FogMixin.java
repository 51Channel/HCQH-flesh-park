package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import world.hechao.fleshpark.client.ParkFog;

/** 原版下界的雾离得太远、颜色太亮。这里按人在公园的哪一段换掉。 */
@Mixin(AtmosphericFogEnvironment.class)
abstract class FogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void fleshpark$distance(FogData data, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo info) {
        if (!ParkFog.applies(level)) return;
        ParkFog.update();
        data.environmentalStart = ParkFog.start();
        data.environmentalEnd = ParkFog.end();
    }

    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void fleshpark$color(ClientLevel level, Camera camera, int renderDistance, float partialTick, CallbackInfoReturnable<Integer> info) {
        if (ParkFog.applies(level)) info.setReturnValue(ParkFog.tint(info.getReturnValue()));
    }
}

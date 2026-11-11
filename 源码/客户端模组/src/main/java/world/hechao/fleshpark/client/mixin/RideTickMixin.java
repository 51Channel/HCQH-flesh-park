package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import world.hechao.fleshpark.client.CabinHook;

/** 坐缆车的时候，原版把人摆到座位上以后，再按车厢里走到的位置挪过去。 */
@Mixin(Entity.class)
abstract class RideTickMixin {
    @Inject(method = "rideTick", at = @At("TAIL"))
    private void fleshpark$walkInCabin(CallbackInfo info) {
        if ((Object) this instanceof LocalPlayer player) CabinHook.afterRide(player);
    }
}

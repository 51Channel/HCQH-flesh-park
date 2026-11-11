package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import world.hechao.fleshpark.client.ShadowPass;

/**
 * 手电的阴影图里只画光能照到的摆设。
 * 光影画阴影图时，把身边 48 格里所有实体都再画一遍：身后、旁边的几百块方块和物品展示也在内，光根本照不到它们，画了也白画。
 * 公园的摆设全是展示实体，一个场景上千个，这一趟能省一半以上。人、手、人形照旧都画。
 */
@Mixin(EntityRenderDispatcher.class)
abstract class ShadowCullMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void fleshpark$beamOnly(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> info) {
        if (entity instanceof Display && ShadowPass.active() && ShadowPass.outsideBeam(entity)) info.setReturnValue(false);
    }
}

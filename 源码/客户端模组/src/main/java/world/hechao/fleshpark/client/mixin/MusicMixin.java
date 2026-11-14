package world.hechao.fleshpark.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import world.hechao.fleshpark.client.ParkFog;

/** 公园里不放原版的背景音乐。公园自己的背景音乐跟“音乐”滑块走，录制前不用再把音乐关掉。 */
@Mixin(MusicManager.class)
abstract class MusicMixin {
    @Shadow
    private SoundInstance currentMusic;

    @Shadow
    public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fleshpark$quiet(CallbackInfo info) {
        if (!ParkFog.applies(Minecraft.getInstance().level)) return;
        if (currentMusic != null) stopPlaying();
        info.cancel();
    }
}

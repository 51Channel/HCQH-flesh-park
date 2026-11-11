package world.hechao.fleshpark.client;

import net.minecraft.client.player.LocalPlayer;

/** 给 mixin 包用的入口：Cabin 本身不公开。 */
public final class CabinHook {
    private CabinHook() {
    }

    public static void afterRide(LocalPlayer player) {
        Cabin.afterRide(player);
    }
}

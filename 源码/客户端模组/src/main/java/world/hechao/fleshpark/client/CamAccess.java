package world.hechao.fleshpark.client;

/** 给 mixin 包用的入口：Cam 是包内可见的。 */
public final class CamAccess {
    private CamAccess() {
    }

    public static boolean active() {
        return Cam.active();
    }

    /** 发疯最后视野往外撑开的倍数（平时是 1）。 */
    public static float fov() {
        return ClientFx.fov();
    }
}

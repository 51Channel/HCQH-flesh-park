package world.hechao.fleshpark.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** 给 mixin 包用：光影这会儿是不是在画手电的阴影图，一个东西在不在手电光里。 */
public final class ShadowPass {
    /** 测帧数时可以关掉（fx shadowcull off），比一比。 */
    static boolean cull = true;
    /**
     * 展示实体的样子可以离它的锚点好几格（车上的零件全挂在车根上，最远五格），只按锚点算会把还在光里的东西剔掉。
     * 所以四边都多让六格。
     */
    private static final double MARGIN = 6;
    /** 阴影图从手电看出去，半宽是距离的 0.8 倍，算上对角线 1.15 倍；光影里手电最远照 48 格。 */
    private static final double SPREAD = 1.15, REACH = 48;

    private ShadowPass() {
    }

    public static boolean active() {
        return ClientFx.shadowPass();
    }

    /** 这个东西在手电的阴影图里根本照不到：在身后、在光锥外头，或者太远。 */
    public static boolean outsideBeam(Entity entity) {
        if (!cull) return false;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return false;
        Vec3 eye = camera.position();
        Vector3fc forward = camera.forwardVector();
        double dx = entity.getX() - eye.x, dy = entity.getY() - eye.y, dz = entity.getZ() - eye.z;
        double along = dx * forward.x() + dy * forward.y() + dz * forward.z();
        if (along < -MARGIN || along > REACH + MARGIN) return true;
        double side = Math.sqrt(Math.max(0, dx * dx + dy * dy + dz * dz - along * along));
        return side - MARGIN > Math.max(along, 0) * SPREAD + 1;
    }
}

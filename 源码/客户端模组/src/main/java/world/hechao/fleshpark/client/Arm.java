package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 第一人称拿着东西的时候，把自己的方块手臂也画出来：原版拿东西是不画手的，镜头里只剩一个悬空的物件。
 * 手臂用玩家自己的皮肤（宽臂 4 像素、细臂 3 像素），外面再套一层袖子。坐标在物件自己的格子里，跟着物件一起晃。
 */
final class Arm {
    private Arm() {
    }

    /**
     * 第一人称时，身上那份第三人称的物件（模型手里拿着的手电）不画。
     * 玩家自己看不见它，但光影的手电投影会画进去：走路摆臂时它在灯旁边晃，地上就有一块跟着脚步变形的黑影。
     */
    static boolean hiddenOnSelf(net.minecraft.world.item.ItemDisplayContext context) {
        boolean third = context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        return third && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /**
     * fist：拳头中心（物件格子里的坐标）；toFist：从肩膀指向拳头的方向；px：一个皮肤像素在格子里多长。
     * left 为真画左臂。手背那一面朝 up。
     */
    static void submit(PoseStack stack, SubmitNodeCollector collector, boolean left, Vector3f fist, Vector3f toFist, Vector3f up, float px, int light, int overlay) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Identifier skin = player.getSkin().body().texturePath();
        int w = player.getSkin().model() == PlayerModelType.SLIM ? 3 : 4;
        // 模型里的轴：+x 是人的左边，+y 从肩膀往手，-z 是身体正面。换到物件格子里
        Vector3f a = new Vector3f(toFist).normalize();
        Vector3f f = new Vector3f(up).sub(new Vector3f(a).mul(up.dot(a))).normalize();
        Vector3f l = new Vector3f(f).cross(a).normalize();
        int baseU = left ? 32 : 40, baseV = left ? 48 : 16, sleeveU = left ? 48 : 40, sleeveV = left ? 48 : 32;
        collector.submitCustomGeometry(stack, RenderTypes.entitySolid(skin), (pose, out) -> box(pose, out, fist, l, a, f, px, w, 0f, baseU, baseV, light, overlay));
        collector.submitCustomGeometry(stack, RenderTypes.entityTranslucent(skin), (pose, out) -> box(pose, out, fist, l, a, f, px, w, 0.25f, sleeveU, sleeveV, light, overlay));
    }

    /** 一条 w×12×4 的方块，拳头那头的中心在 fist。贴图按原版方块人的展开方式取。 */
    private static void box(PoseStack.Pose pose, VertexConsumer out, Vector3f fist, Vector3f l, Vector3f a, Vector3f f, float px, int w, float grow,
                            int u0, int v0, int light, int overlay) {
        int d = 4, h = 12;
        float x0 = -w / 2f - grow, x1 = w / 2f + grow, y0 = -h - grow, y1 = grow, z0 = -d / 2f - grow, z1 = d / 2f + grow;
        float g = u0 + d, hh = u0 + d + w, i = u0 + d + 2 * w, j = u0 + 2 * d + w, k = u0 + 2 * d + 2 * w;
        float m = v0 + d, n = v0 + d + h;
        // 每一面：四个角（模型坐标）和对应的贴图坐标。顺序都是从外面看逆时针
        // 正面（-z）
        quad(pose, out, fist, l, a, f, px, light, overlay, new float[][]{{x0, y0, z0, g, m}, {x0, y1, z0, g, n}, {x1, y1, z0, hh, n}, {x1, y0, z0, hh, m}});
        // 背面（+z）
        quad(pose, out, fist, l, a, f, px, light, overlay, new float[][]{{x1, y0, z1, j, m}, {x1, y1, z1, j, n}, {x0, y1, z1, k, n}, {x0, y0, z1, k, m}});
        // 人的右侧（-x）
        quad(pose, out, fist, l, a, f, px, light, overlay, new float[][]{{x0, y0, z1, u0, m}, {x0, y1, z1, u0, n}, {x0, y1, z0, g, n}, {x0, y0, z0, g, m}});
        // 人的左侧（+x）
        quad(pose, out, fist, l, a, f, px, light, overlay, new float[][]{{x1, y0, z0, hh, m}, {x1, y1, z0, hh, n}, {x1, y1, z1, j, n}, {x1, y0, z1, j, m}});
        // 拳头那头（+y）
        quad(pose, out, fist, l, a, f, px, light, overlay, new float[][]{{x0, y1, z0, hh, v0}, {x0, y1, z1, hh, m}, {x1, y1, z1, i, m}, {x1, y1, z0, i, v0}});
        // 肩膀那头（-y），一般在画面外
        quad(pose, out, fist, l, a, f, px, light, overlay, new float[][]{{x0, y0, z1, g, m}, {x0, y0, z0, g, v0}, {x1, y0, z0, hh, v0}, {x1, y0, z1, hh, m}});
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, Vector3f fist, Vector3f l, Vector3f a, Vector3f f, float px, int light, int overlay, float[][] c) {
        Vector3f[] p = new Vector3f[4];
        for (int q = 0; q < 4; q++) {
            // 模型 +z 是身体背面，正面是 f
            p[q] = new Vector3f(fist).add(new Vector3f(l).mul(c[q][0] * px)).add(new Vector3f(a).mul(c[q][1] * px)).add(new Vector3f(f).mul(-c[q][2] * px));
        }
        Vector3f center = new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]).mul(0.25f);
        Vector3f normal = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0])).normalize();
        // 绕向和朝外的方向对不上就倒过来，免得被背面剔除
        Vector3f boxCenter = new Vector3f(fist).add(new Vector3f(a).mul(-6 * px));
        int[] order = normal.dot(new Vector3f(center).sub(boxCenter)) >= 0 ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
        if (order[0] == 3) normal.negate();
        Matrix4f mat = pose.pose();
        Matrix3f nm = pose.normal();
        Vector3f wn = new Vector3f(normal).mul(nm).normalize();
        for (int q : order) {
            Vector3f v = new Vector3f(p[q]).mulPosition(mat);
            out.addVertex(v.x, v.y, v.z, 0xFFFFFFFF, c[q][3] / 64f, c[q][4] / 64f, overlay, light, wn.x, wn.y, wn.z);
        }
    }
}

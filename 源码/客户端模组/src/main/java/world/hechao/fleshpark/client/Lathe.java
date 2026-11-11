package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** 车床车出来的东西：手电、管线。坐标都在 0 到 1 的格子里。 */
final class Lathe {
    /** 一段外形：沿轴从 (a0, r0) 到 (a1, r1)，color 是顶点颜色。 */
    record Band(float a0, float r0, float a1, float r1, int color, boolean lens) {
        Band(float a0, float r0, float a1, float r1, int color) {
            this(a0, r0, a1, r1, color, false);
        }
    }

    private Lathe() {
    }

    /** 每段自己一个法线方向，段和段之间是硬边，看着是车出来的金属件。alongX 决定轴是 X 还是 Z。 */
    static void band(PoseStack.Pose pose, VertexConsumer out, Band band, TextureAtlasSprite sprite, int sides, boolean alongX, int light, int overlay) {
        float da = band.a1 - band.a0, dr = band.r1 - band.r0, len = (float) Math.sqrt(da * da + dr * dr);
        float radial = Math.abs(da) / len, axial = (da < 0 ? dr : -dr) / len;
        if (da == 0) axial = dr > 0 ? -1 : 1;
        // 绕一圈的方向和沿轴的方向叉出来的面朝向，要和法线一致，不然被背面剔除。沿 X 和沿 Z 的绕向相反
        boolean flip = (da < 0 || (da == 0 && dr > 0)) ^ alongX;
        for (int s = 0; s < sides; s++) {
            int s0 = flip ? s + 1 : s, s1 = flip ? s : s + 1;
            vertex(pose, out, band, sprite, band.a0, band.r0, s0, sides, alongX, radial, axial, light, overlay);
            vertex(pose, out, band, sprite, band.a0, band.r0, s1, sides, alongX, radial, axial, light, overlay);
            vertex(pose, out, band, sprite, band.a1, band.r1, s1, sides, alongX, radial, axial, light, overlay);
            vertex(pose, out, band, sprite, band.a1, band.r1, s0, sides, alongX, radial, axial, light, overlay);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Band band, TextureAtlasSprite sprite, float a, float r, int side, int sides, boolean alongX, float radial, float axial, int light, int overlay) {
        double angle = Math.PI * 2 * (side % sides) / sides;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float px, py, pz, qx, qy, qz;
        if (alongX) {
            px = a; py = 0.5f + r * cos; pz = 0.5f + r * sin;
            qx = axial; qy = radial * cos; qz = radial * sin;
        } else {
            px = 0.5f + r * cos; py = 0.5f + r * sin; pz = a;
            qx = radial * cos; qy = radial * sin; qz = axial;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float nx = n.m00() * qx + n.m10() * qy + n.m20() * qz, ny = n.m01() * qx + n.m11() * qy + n.m21() * qz, nz = n.m02() * qx + n.m12() * qy + n.m22() * qz;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        out.addVertex(m.m00() * px + m.m10() * py + m.m20() * pz + m.m30(), m.m01() * px + m.m11() * py + m.m21() * pz + m.m31(), m.m02() * px + m.m12() * py + m.m22() * pz + m.m32(),
                band.color, sprite.getU((float) side / sides), sprite.getV(a), overlay, light, nx / length, ny / length, nz / length);
    }
}

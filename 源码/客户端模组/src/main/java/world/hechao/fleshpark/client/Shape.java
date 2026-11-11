package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;

/** 一堆光滑的曲面拼成的模型：灰色人形、灰色的手。每个顶点 8 个数：位置、法线、贴图坐标。 */
final class Shape {
    interface Surface {
        /** u 绕一圈，v 从头到尾，都是 0 到 1。输出位置。 */
        void at(double u, double v, double[] out);
    }

    private float[] data = new float[4096];
    private int size;

    /** 光滑曲面：法线按相邻点算，面朝向外（由 outward 决定方向的正负）。 */
    private double planar;

    /** 直接加一个四边形：顶点和光滑法线都给好了。绕向按法线摆正。贴图按位置平铺。 */
    Shape quad(double[][] p, double[][] n) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2], bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        double fx = ay * bz - az * by, fy = az * bx - ax * bz, fz = ax * by - ay * bx;
        double mx = n[0][0] + n[1][0] + n[2][0] + n[3][0], my = n[0][1] + n[1][1] + n[2][1] + n[3][1], mz = n[0][2] + n[1][2] + n[2][2] + n[3][2];
        boolean flip = fx * mx + fy * my + fz * mz < 0;
        for (int k = 0; k < 4; k++) {
            int q = flip ? 3 - k : k;
            put(p[q], n[q], (float) ((p[q][0] + p[q][2]) * 3 % 1 + 1) % 1, (float) ((p[q][1] * 3) % 1 + 1) % 1);
        }
        return this;
    }
    private double side;

    /** 车身立面：沿周长和高度都不拉伸的贴图坐标。 */
    Shape side(double scale) {
        side = scale;
        return this;
    }

    Shape planar(double scale) {
        planar = scale;
        return this;
    }

    Shape surface(Surface s, int sides, int rings, double cx, double cy, double cz) {
        return surface(s, sides, rings, cx, cy, cz, false);
    }

    /** 圆角方块：车里的座椅、控制箱。 */
    Shape roundBox(double cx, double cy, double cz, double sx, double sy, double sz, int detail) {
        double e = 0.28;
        return surface((u, v, out) -> {
            double polar = (v - 0.5) * Math.PI, a = u * Math.PI * 2;
            double cp = Math.cos(polar), sp = Math.sin(polar), ca = Math.cos(a), sa = Math.sin(a);
            out[0] = cx + sx * Math.signum(cp) * Math.pow(Math.abs(cp), e) * Math.signum(ca) * Math.pow(Math.abs(ca), e);
            out[1] = cy + sy * Math.signum(sp) * Math.pow(Math.abs(sp), e);
            out[2] = cz + sz * Math.signum(cp) * Math.pow(Math.abs(cp), e) * Math.signum(sa) * Math.pow(Math.abs(sa), e);
        }, detail * 2, detail, cx, cy, cz);
    }

    Shape surface(Surface s, int sides, int rings, double cx, double cy, double cz, boolean inward) {
        double[][][] p = new double[sides + 1][rings + 1][3];
        double[][][] n = new double[sides + 1][rings + 1][3];
        double du = 1.0 / sides / 4, dv = 1.0 / rings / 4;
        double[] a = new double[3], b = new double[3], c = new double[3], d = new double[3];
        for (int i = 0; i <= sides; i++) {
            for (int j = 0; j <= rings; j++) {
                double u = (double) i / sides, v = (double) j / rings;
                s.at(u, v, p[i][j]);
                s.at(u - du, v, a);
                s.at(u + du, v, b);
                s.at(u, Math.max(0.0005, v - dv), c);
                s.at(u, Math.min(0.9995, v + dv), d);
                double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - c[0], vy = d[1] - c[1], vz = d[2] - c[2];
                double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-10) {
                    nx = p[i][j][0] - cx;
                    ny = p[i][j][1] - cy;
                    nz = p[i][j][2] - cz;
                    len = Math.max(1e-6, Math.sqrt(nx * nx + ny * ny + nz * nz));
                }
                nx /= len;
                ny /= len;
                nz /= len;
                // 朝向不对就翻过来
                if ((nx * (p[i][j][0] - cx) + ny * (p[i][j][1] - cy) + nz * (p[i][j][2] - cz) < 0) != inward) {
                    nx = -nx;
                    ny = -ny;
                    nz = -nz;
                }
                n[i][j][0] = nx;
                n[i][j][1] = ny;
                n[i][j][2] = nz;
            }
        }
        for (int i = 0; i < sides; i++) {
            for (int j = 0; j < rings; j++) {
                int[][] quad = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                // 看第一个三角形的绕向和法线是否一致，不一致就反着出，不然被背面剔除
                double[] p0 = p[quad[0][0]][quad[0][1]], p1 = p[quad[1][0]][quad[1][1]], p2 = p[quad[2][0]][quad[2][1]], n0 = n[quad[0][0]][quad[0][1]];
                double ax = p1[0] - p0[0], ay = p1[1] - p0[1], az = p1[2] - p0[2], bx = p2[0] - p0[0], by = p2[1] - p0[1], bz = p2[2] - p0[2];
                double fx = ay * bz - az * by, fy = az * bx - ax * bz, fz = ax * by - ay * bx;
                boolean flip = fx * n0[0] + fy * n0[1] + fz * n0[2] < 0;
                for (int k = 0; k < 4; k++) {
                    int[] q = quad[flip ? 3 - k : k];
                    double[] at = p[q[0]][q[1]];
                    // 车顶、地板用俯视投影的贴图坐标，不然贴图都收到中间一个点上
                    if (planar > 0) put(at, n[q[0]][q[1]], (float) ((at[0] + 6) / planar), (float) ((at[2] + 2) / planar));
                    else if (side > 0) put(at, n[q[0]][q[1]], (float) ((at[0] + at[2] + 8) / side), (float) ((at[1] + 1) / side));
                    else put(at, n[q[0]][q[1]], (float) q[0] / sides, (float) q[1] / rings);
                }
            }
        }
        return this;
    }

    /** 两头圆的粗细渐变的棒子：四肢、手指。 */
    Shape capsule(double ax, double ay, double az, double ra, double bx, double by, double bz, double rb, int sides, int rings) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double[] w = {dx / len, dy / len, dz / len};
        double[] h = Math.abs(w[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] e1 = {w[1] * h[2] - w[2] * h[1], w[2] * h[0] - w[0] * h[2], w[0] * h[1] - w[1] * h[0]};
        double l1 = Math.sqrt(e1[0] * e1[0] + e1[1] * e1[1] + e1[2] * e1[2]);
        for (int i = 0; i < 3; i++) e1[i] /= l1;
        double[] e2 = {w[1] * e1[2] - w[2] * e1[1], w[2] * e1[0] - w[0] * e1[2], w[0] * e1[1] - w[1] * e1[0]};
        double mx = (ax + bx) / 2, my = (ay + by) / 2, mz = (az + bz) / 2;
        return surface((u, v, out) -> {
            // v 走一条从顶到底的轮廓：半球、身子、半球
            double t = (v * 2 - 1);
            double pole = 1.0 / 3;
            double along, rad;
            if (Math.abs(t) > 1 - pole) {
                double k = (Math.abs(t) - (1 - pole)) / pole;
                double angle = k * Math.PI / 2;
                double r = t < 0 ? ra : rb;
                along = Math.signum(t) * (len / 2 + Math.sin(angle) * r);
                rad = Math.cos(angle) * r;
            } else {
                double k = t / (1 - pole);
                along = k * len / 2;
                rad = ra + (rb - ra) * (k + 1) / 2;
            }
            double a = u * Math.PI * 2, cos = Math.cos(a), sin = Math.sin(a);
            for (int i = 0; i < 3; i++) {
                out[i] = new double[]{mx, my, mz}[i] + w[i] * along + (e1[i] * cos + e2[i] * sin) * rad;
            }
        }, sides, rings, mx, my, mz);
    }

    private void put(double[] p, double[] n, float u, float v) {
        if (size + 8 > data.length) data = Arrays.copyOf(data, data.length * 2);
        data[size++] = (float) p[0];
        data[size++] = (float) p[1];
        data[size++] = (float) p[2];
        data[size++] = (float) n[0];
        data[size++] = (float) n[1];
        data[size++] = (float) n[2];
        data[size++] = u;
        data[size++] = v;
    }

    /** 和光影包 lib/move.glsl 里的 TINTS 一一对应。 */
    private static final int[] TINTS = {0xFFFFFFFF, 0xFF8E9086, 0xFF4E6A64, 0xFF2E3234, 0xFF555656, 0xFF6A5444, 0xFF6E706C, 0xFF49655A, 0xFFB8A8A0, 0xFF000000, 0xFFFFFBF5, 0xFFD74735, 0xFF24211F, 0xFFB4B0AA, 0xFF2C7A74, 0xFFE8661E, 0xFFD8DAD2, 0xFF9A3A32, 0xFF2E3A52, 0xFF5C7C96, 0xFFB08A3A, 0xFF8A7A5A, 0xFFC8C4B8, 0xFF5A6040, 0xFFA8707A, 0xFF3E5068, 0xFFD8D4CA, 0xFFC8A030, 0xFFA08E62, 0xFF3E4A36, 0xFF2C3440, 0xFFD8A020, 0xFF7A4A6A, 0xFF4A6A3A, 0xFF3A2A22, 0xFF6A4A30, 0xFF8A8478, 0xFFB85A2A, 0xFF5A3A2A};

    /**
     * Iris 会把每个面的法线改成平的，模型就一格一格的。开着我们的光影时，光滑法线放进顶点色：
     * 透明度 253 是记号，R、G 是八面体编码的法线，B 是 TINTS 里的序号。不透明的颜色才能这样。
     */
    static int smoothColor(float nx, float ny, float nz, int color) {
        if (!ClientFx.shaderOn || (color >>> 24) != 0xFF) return color;
        int tint = 0;
        for (int i = 0; i < TINTS.length; i++) if (TINTS[i] == color) tint = i;
        float s = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        float ox = nx / s, oy = ny / s;
        if (nz < 0) {
            float wx = (1 - Math.abs(oy)) * (ox >= 0 ? 1 : -1), wy = (1 - Math.abs(ox)) * (oy >= 0 ? 1 : -1);
            ox = wx;
            oy = wy;
        }
        return 0xFD000000 | Math.round((ox * 0.5f + 0.5f) * 255) << 16 | Math.round((oy * 0.5f + 0.5f) * 255) << 8 | tint;
    }

    /** 肉的网格：贴图坐标放法线，贴图由光影按世界坐标贴。 */
    static float octU(float nx, float ny, float nz) {
        float s = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        float ox = nx / s, oy = ny / s;
        return (nz < 0 ? (1 - Math.abs(oy)) * (ox >= 0 ? 1 : -1) : ox) * 0.5f + 0.5f;
    }

    static float octV(float nx, float ny, float nz) {
        float s = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        float ox = nx / s, oy = ny / s;
        return (nz < 0 ? (1 - Math.abs(ox)) * (oy >= 0 ? 1 : -1) : oy) * 0.5f + 0.5f;
    }

    /** 离线预览用：顶点数据的拷贝。 */
    float[] raw() {
        return Arrays.copyOf(data, size);
    }

    /** 几个面：每个顶点 8 个数，每个面 4 个顶点。 */
    int quads() {
        return size / 32;
    }

    void emit(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite, int color, int light, int overlay) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] d = data;
        for (int i = 0; i < size; i += 8) {
            float x = d[i], y = d[i + 1], z = d[i + 2], a = d[i + 3], b = d[i + 4], c = d[i + 5];
            float nx = n.m00() * a + n.m10() * b + n.m20() * c, ny = n.m01() * a + n.m11() * b + n.m21() * c, nz = n.m02() * a + n.m12() * b + n.m22() * c;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-5f) len = 1;
            out.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(), m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
                    smoothColor(nx / len, ny / len, nz / len, color), sprite.getU(d[i + 6]), sprite.getV(d[i + 7]), overlay, light, nx / len, ny / len, nz / len);
        }
    }
}

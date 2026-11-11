package world.hechao.fleshpark.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用距离场雕一个整块的身体：胶囊和椭球平滑地融在一起，再用 Surface Nets 取表面。
 * 拼起来的圆球一眼就看得出是几个零件；融在一起才像一个活的东西。坐标单位是米，脚底 y=0，脸朝 +z。
 * 每个零件带一个“部位”（皮肤、上衣、帽子……）：取表面的时候每个面按离它最近的零件分到那个部位，各上各的颜色，
 * 衣服和皮肤是同一张曲面，不会叠两层闪。
 */
final class Sculpt {
    private interface Fn {
        double d(double x, double y, double z);
    }

    private static final class Part {
        final Fn fn;
        final double blend;
        final int slot;
        /** 外接盒：离它比融合距离还远的点不用算。 */
        final double x0, y0, z0, x1, y1, z1;

        Part(Fn fn, double blend, int slot, double x0, double y0, double z0, double x1, double y1, double z1) {
            this.fn = fn;
            this.blend = blend;
            this.slot = slot;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        double box(double x, double y, double z) {
            double dx = Math.max(Math.max(x0 - x, x - x1), 0), dy = Math.max(Math.max(y0 - y, y - y1), 0), dz = Math.max(Math.max(z0 - z, z - z1), 0);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    interface Paint {
        int slot(int slot, double x, double y, double z);
    }

    private final List<Part> parts = new ArrayList<>();
    private final List<Fn> carve = new ArrayList<>();
    /** 只保留这个高度以上：泡在池子里的半身。 */
    double cutBelow = -1;
    /** 驼背（从 leanY 往上越往前探）和歪头（脖子 neckY 以上绕着脖子转）：roll 往肩膀歪，yaw 扭头，pitch 低头。 */
    double lean, tilt, yaw, pitch;
    double leanY = 1.0, neckY = 1.86;
    /** 之后加的零件算哪个部位。 */
    int slot;
    /** 分完部位以后再改一道：反光背心上的反光条。 */
    Paint paint;

    Sculpt capsule(double ax, double ay, double az, double ra, double bx, double by, double bz, double rb, double blend) {
        double r = Math.max(ra, rb);
        parts.add(new Part((x, y, z) -> {
            double px = x - ax, py = y - ay, pz = z - az, dx = bx - ax, dy = by - ay, dz = bz - az;
            double t = Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) / (dx * dx + dy * dy + dz * dz)));
            double qx = px - dx * t, qy = py - dy * t, qz = pz - dz * t;
            return Math.sqrt(qx * qx + qy * qy + qz * qz) - (ra + (rb - ra) * t);
        }, blend, slot, Math.min(ax, bx) - r, Math.min(ay, by) - r, Math.min(az, bz) - r, Math.max(ax, bx) + r, Math.max(ay, by) + r, Math.max(az, bz) + r));
        return this;
    }

    Sculpt ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double blend) {
        parts.add(new Part((x, y, z) -> {
            double ex = (x - cx) / rx, ey = (y - cy) / ry, ez = (z - cz) / rz;
            double k = Math.sqrt(ex * ex + ey * ey + ez * ez);
            return (k - 1) * Math.min(rx, Math.min(ry, rz));
        }, blend, slot, cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz));
        return this;
    }

    /** 圆角方块：围裙、背包、相机。cx..sz 是中心和半边长，round 是圆角。 */
    Sculpt box(double cx, double cy, double cz, double sx, double sy, double sz, double round, double blend) {
        parts.add(new Part((x, y, z) -> {
            double qx = Math.abs(x - cx) - sx + round, qy = Math.abs(y - cy) - sy + round, qz = Math.abs(z - cz) - sz + round;
            double ox = Math.max(qx, 0), oy = Math.max(qy, 0), oz = Math.max(qz, 0);
            return Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0) - round;
        }, blend, slot, cx - sx, cy - sy, cz - sz, cx + sx, cy + sy, cz + sz));
        return this;
    }

    /** 竖着的圆台，上下是平的：裙子、长袍的下摆。底面中心可以往前挪（坐着的时候裙摆铺在腿上）。 */
    Sculpt cone(double x, double yTop, double zTop, double rTop, double yBottom, double zBottom, double rBottom, double blend) {
        double r = Math.max(rTop, rBottom);
        parts.add(new Part((px, py, pz) -> {
            double t = Math.max(0, Math.min(1, (yTop - py) / (yTop - yBottom)));
            double cz = zTop + (zBottom - zTop) * t, rr = rTop + (rBottom - rTop) * t;
            double radial = Math.hypot(px - x, pz - cz) - rr;
            // 斜着的侧面：按坡度把径向距离折算成到面的距离
            double slope = (rBottom - rTop) / (yTop - yBottom);
            radial /= Math.sqrt(1 + slope * slope);
            return Math.max(radial, Math.max(py - yTop, yBottom - py));
        }, blend, slot, x - r, yBottom, Math.min(zTop, zBottom) - r, x + r, yTop, Math.max(zTop, zBottom) + r));
        return this;
    }

    /** 挖掉一段胶囊：嘴。 */
    Sculpt groove(double ax, double ay, double az, double bx, double by, double bz, double r) {
        carve.add((x, y, z) -> {
            double px = x - ax, py = y - ay, pz = z - az, dx = bx - ax, dy = by - ay, dz = bz - az;
            double t = Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) / (dx * dx + dy * dy + dz * dz)));
            double qx = px - dx * t, qy = py - dy * t, qz = pz - dz * t;
            return Math.sqrt(qx * qx + qy * qy + qz * qz) - r;
        });
        return this;
    }

    /** 挖掉一个球：眼窝。 */
    Sculpt dent(double cx, double cy, double cz, double r) {
        carve.add((x, y, z) -> Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz)) - r);
        return this;
    }

    /** 脖子以上转多少（0 到 1）：过脖子 0.1 米才转满，脖子不会折断。 */
    private double neckBlend(double y) {
        return Math.max(0, Math.min(1, (y - neckY) / 0.1));
    }

    private boolean headTurns() {
        return tilt != 0 || yaw != 0 || pitch != 0;
    }

    /** 绕脖子转：sign 为 1 是雕好的位置换到摆好的位置，-1 反过来。 */
    private void turnHead(double[] p, double k, double sign) {
        double x = p[0], y = p[1] - neckY, z = p[2];
        // 低头：绕 x 轴
        double a = pitch * k * sign, c = Math.cos(a), s = Math.sin(a);
        double y1 = y * c - z * s, z1 = y * s + z * c;
        // 扭头：绕 y 轴
        a = yaw * k * sign;
        c = Math.cos(a);
        s = Math.sin(a);
        double x2 = x * c + z1 * s, z2 = -x * s + z1 * c;
        // 歪头：绕 z 轴
        a = tilt * k * sign;
        c = Math.cos(a);
        s = Math.sin(a);
        double x3 = x2 * c - y1 * s, y3 = x2 * s + y1 * c;
        if (sign < 0) {
            // 反着转要倒过来按 z、y、x 的顺序
            a = tilt * k * sign;
            c = Math.cos(a);
            s = Math.sin(a);
            x3 = x * c - y * s;
            y3 = x * s + y * c;
            a = yaw * k * sign;
            c = Math.cos(a);
            s = Math.sin(a);
            x2 = x3 * c + z * s;
            z2 = -x3 * s + z * c;
            a = pitch * k * sign;
            c = Math.cos(a);
            s = Math.sin(a);
            y1 = y3 * c - z2 * s;
            z1 = y3 * s + z2 * c;
            p[0] = x2;
            p[1] = y1 + neckY;
            p[2] = z1;
            return;
        }
        p[0] = x3;
        p[1] = y3 + neckY;
        p[2] = z2;
    }

    /** 雕好的位置换到摆好姿势以后的位置：先转头，再加回驼背。 */
    double[] place(double x, double y, double z) {
        double[] p = {x, y, z};
        if (y > neckY && headTurns()) turnHead(p, neckBlend(y), 1);
        if (p[1] > leanY) p[2] += (p[1] - leanY) * lean;
        return p;
    }

    /** 摆好的位置换回雕的位置。 */
    private void unplace(double[] p) {
        if (p[1] > leanY) p[2] -= (p[1] - leanY) * lean;
        if (p[1] > neckY && headTurns()) turnHead(p, neckBlend(p[1]), -1);
    }

    double field(double x, double y, double z) {
        double[] p = {x, y, z};
        unplace(p);
        x = p[0];
        y = p[1];
        z = p[2];
        double d = 1e9;
        for (Part part : parts) {
            if (part.box(x, y, z) - part.blend > d) continue;
            d = smin(d, part.fn.d(x, y, z), part.blend);
        }
        for (Fn c : carve) d = smax(d, -c.d(x, y, z), 0.02);
        if (cutBelow > 0) d = smax(d, cutBelow - y, 0.03);
        return d;
    }

    /** 这一点归哪个部位：离它最近的零件。 */
    int slotAt(double x, double y, double z) {
        double[] p = {x, y, z};
        unplace(p);
        double best = 1e9;
        int slot = 0;
        for (Part part : parts) {
            if (part.box(p[0], p[1], p[2]) > best) continue;
            double d = part.fn.d(p[0], p[1], p[2]);
            if (d < best) {
                best = d;
                slot = part.slot;
            }
        }
        return paint != null ? paint.slot(slot, p[0], p[1], p[2]) : slot;
    }

    /** 摆好姿势以后整个身子的外接盒：x0 x1 y0 y1 z0 z1，多留一点边。 */
    double[] bounds(double margin) {
        double[] b = {1e9, -1e9, 1e9, -1e9, 1e9, -1e9};
        for (Part part : parts) {
            for (int i = 0; i < 8; i++) {
                double[] q = place((i & 1) == 0 ? part.x0 : part.x1, (i & 2) == 0 ? part.y0 : part.y1, (i & 4) == 0 ? part.z0 : part.z1);
                b[0] = Math.min(b[0], q[0]);
                b[1] = Math.max(b[1], q[0]);
                b[2] = Math.min(b[2], q[1]);
                b[3] = Math.max(b[3], q[1]);
                b[4] = Math.min(b[4], q[2]);
                b[5] = Math.max(b[5], q[2]);
            }
        }
        b[0] -= margin;
        b[1] += margin;
        b[2] -= margin;
        b[3] += margin;
        b[4] -= margin;
        b[5] += margin;
        if (cutBelow > 0) b[2] = Math.max(b[2], cutBelow - margin);
        return b;
    }

    private static double smin(double a, double b, double k) {
        if (k <= 0) return Math.min(a, b);
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    private static double smax(double a, double b, double k) {
        return -smin(-a, -b, k);
    }

    /** 取表面，写进 shape。模型坐标：x、z 以 0.5 为中心，y 从 -0.5 开始。 */
    void into(Shape shape, double x0, double x1, double y0, double y1, double z0, double z1, double step) {
        into(shape, x0, x1, y0, y1, z0, z1, step, 0.5, -0.5, 0.5);
    }

    /** 雕的坐标加上 (ox, oy, oz) 就是模型坐标。 */
    void into(Shape shape, double x0, double x1, double y0, double y1, double z0, double z1, double step, double ox, double oy, double oz) {
        into(new Shape[]{shape}, false, x0, x1, y0, y1, z0, z1, step, ox, oy, oz);
    }

    /** 取表面，每个面按部位分进 shapes[部位]；没给的部位算进 shapes[0]。 */
    void intoSlots(Shape[] shapes, double x0, double x1, double y0, double y1, double z0, double z1, double step, double ox, double oy, double oz) {
        into(shapes, true, x0, x1, y0, y1, z0, z1, step, ox, oy, oz);
    }

    private void into(Shape[] shapes, boolean bySlot, double x0, double x1, double y0, double y1, double z0, double z1, double step, double ox, double oy, double oz) {
        int nx = (int) Math.ceil((x1 - x0) / step) + 1, ny = (int) Math.ceil((y1 - y0) / step) + 1, nz = (int) Math.ceil((z1 - z0) / step) + 1;
        float[] f = new float[nx * ny * nz];
        for (int k = 0; k < nz; k++) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            f[i + j * nx + k * nx * ny] = (float) field(x0 + i * step, y0 + j * step, z0 + k * step);
        }
        Map<Integer, Integer> index = new HashMap<>();
        List<double[]> verts = new ArrayList<>();
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        float[] c = new float[8];
        for (int k = 0; k < nz - 1; k++) for (int j = 0; j < ny - 1; j++) for (int i = 0; i < nx - 1; i++) {
            boolean in = false, out = false;
            for (int q = 0; q < 8; q++) {
                c[q] = f[(i + (q & 1)) + (j + (q >> 1 & 1)) * nx + (k + (q >> 2 & 1)) * nx * ny];
                if (c[q] < 0) in = true;
                else out = true;
            }
            if (!in || !out) continue;
            double sx = 0, sy = 0, sz = 0;
            int n = 0;
            for (int[] e : edges) {
                float a = c[e[0]], b = c[e[1]];
                if ((a < 0) == (b < 0)) continue;
                double t = a / (a - b);
                sx += (e[0] & 1) + ((e[1] & 1) - (e[0] & 1)) * t;
                sy += (e[0] >> 1 & 1) + ((e[1] >> 1 & 1) - (e[0] >> 1 & 1)) * t;
                sz += (e[0] >> 2 & 1) + ((e[1] >> 2 & 1) - (e[0] >> 2 & 1)) * t;
                n++;
            }
            index.put(i + j * nx + k * nx * ny, verts.size());
            verts.add(new double[]{x0 + (i + sx / n) * step, y0 + (j + sy / n) * step, z0 + (k + sz / n) * step});
        }
        double e = step * 0.5;
        Map<Integer, double[]> normalOf = new HashMap<>();
        for (int k = 0; k < nz - 1; k++) for (int j = 0; j < ny - 1; j++) for (int i = 0; i < nx - 1; i++) {
            float f0 = f[i + j * nx + k * nx * ny];
            int[][] dirs = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (int a = 0; a < 3; a++) {
                int ii = i + dirs[a][0], jj = j + dirs[a][1], kk = k + dirs[a][2];
                if (ii >= nx || jj >= ny || kk >= nz) continue;
                float f1 = f[ii + jj * nx + kk * nx * ny];
                if ((f0 < 0) == (f1 < 0)) continue;
                int[] u = a == 0 ? new int[]{0, 1, 0} : a == 1 ? new int[]{0, 0, 1} : new int[]{1, 0, 0};
                int[] v = a == 0 ? new int[]{0, 0, 1} : a == 1 ? new int[]{1, 0, 0} : new int[]{0, 1, 0};
                int[][] cells = {{i, j, k}, {i - u[0], j - u[1], k - u[2]}, {i - u[0] - v[0], j - u[1] - v[1], k - u[2] - v[2]}, {i - v[0], j - v[1], k - v[2]}};
                double[][] quad = new double[4][];
                int[] ids = new int[4];
                boolean ok = true;
                for (int q = 0; q < 4; q++) {
                    int[] cc = cells[q];
                    if (cc[0] < 0 || cc[1] < 0 || cc[2] < 0) {
                        ok = false;
                        break;
                    }
                    Integer id = index.get(cc[0] + cc[1] * nx + cc[2] * nx * ny);
                    if (id == null) {
                        ok = false;
                        break;
                    }
                    ids[q] = id;
                    quad[q] = verts.get(id);
                }
                if (!ok) continue;
                double[][] normals = new double[4][];
                for (int q = 0; q < 4; q++) {
                    double[] known = normalOf.get(ids[q]);
                    if (known == null) {
                        double[] p = quad[q];
                        double gx = field(p[0] + e, p[1], p[2]) - field(p[0] - e, p[1], p[2]);
                        double gy = field(p[0], p[1] + e, p[2]) - field(p[0], p[1] - e, p[2]);
                        double gz = field(p[0], p[1], p[2] + e) - field(p[0], p[1], p[2] - e);
                        double l = Math.max(1e-9, Math.sqrt(gx * gx + gy * gy + gz * gz));
                        known = new double[]{gx / l, gy / l, gz / l};
                        normalOf.put(ids[q], known);
                    }
                    normals[q] = known;
                }
                double[][] model = new double[4][];
                for (int q = 0; q < 4; q++) model[q] = new double[]{quad[q][0] + ox, quad[q][1] + oy, quad[q][2] + oz};
                Shape target = shapes[0];
                if (bySlot) {
                    double cx = (quad[0][0] + quad[1][0] + quad[2][0] + quad[3][0]) / 4, cy = (quad[0][1] + quad[1][1] + quad[2][1] + quad[3][1]) / 4, cz = (quad[0][2] + quad[1][2] + quad[2][2] + quad[3][2]) / 4;
                    int slot = slotAt(cx, cy, cz);
                    if (slot >= 0 && slot < shapes.length && shapes[slot] != null) target = shapes[slot];
                }
                target.quad(model, normals);
            }
        }
    }
}

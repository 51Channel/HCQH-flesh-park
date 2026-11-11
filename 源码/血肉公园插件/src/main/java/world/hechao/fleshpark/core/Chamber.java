package world.hechao.fleshpark.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 超大的肉腔模型：沿一条或几条路线掏出来的连续空间，四周全是肉。
 * 用一个函数描述：f 大于 0 是肉，小于 0 是空气。环带、深褶、垂下来的肉团都是这个函数的一部分，所以它们天然连在肉壁上，不会悬在半空。
 */
public final class Chamber {
    public enum Style {RIDE, FOREST, SURFACE, BURROW, WELL}

    private final Style style;
    private final double[] lx, ly, lz, ls;
    private final int[] breaks;
    private final double length, clear;
    /** 胶囊：ax ay az bx by bz ra rb。 */
    private final List<double[]> limbs = new ArrayList<>();
    /** 要留空的盒子：x0 y0 z0 x1 y1 z1。 */
    private final List<double[]> keepOut = new ArrayList<>();

    private Chamber(Style style, List<double[]> line, int[] breaks, double clear) {
        this.style = style;
        this.breaks = breaks;
        this.clear = clear;
        int n = line.size();
        lx = new double[n];
        ly = new double[n];
        lz = new double[n];
        ls = new double[n];
        double s = 0;
        for (int i = 0; i < n; i++) {
            double[] q = line.get(i);
            lx[i] = q[0];
            ly[i] = q[1];
            lz[i] = q[2];
            if (i > 0) s += Math.sqrt(sq(lx[i] - lx[i - 1]) + sq(ly[i] - ly[i - 1]) + sq(lz[i] - lz[i - 1]));
            ls[i] = s;
        }
        length = Math.max(1, s);
    }

    /** 缆车段：从食道出口到游客中心，整段是一个巨大的胃腔。 */
    static Chamber ride(Route route) {
        double from = route.distAtY(ParkPlan.THROAT_BOTTOM + 14), to = route.length;
        int n = (int) ((to - from) / 2.5) + 1;
        List<double[]> line = new ArrayList<>();
        double[] p = new double[3];
        for (int i = 0; i < n; i++) {
            route.at(from + (to - from) * i / (n - 1), p);
            line.add(new double[]{p[0] + Route.CABIN_CENTER_X, p[1] + 1.3, p[2]});
        }
        Chamber c = new Chamber(Style.RIDE, line, new int[0], 6.6);
        // 垂下来、顶上来的大肉团：根在肉壁里，尖离车几米，车贴着它们过去
        Random rnd = new Random(5);
        for (int i = 0; i < 64; i++) {
            int at = rnd.nextInt(n);
            double a = rnd.nextDouble() * Math.PI * 2;
            double dx = Math.cos(a) * 0.5, dy = Math.sin(a), dz = Math.cos(a) * (i % 2 == 0 ? 0.85 : -0.85);
            double reach = 8.5 + rnd.nextDouble() * 7, root = 26 + rnd.nextDouble() * 8;
            double rootR = 4.5 + rnd.nextDouble() * 5, tipR = 1.6 + rnd.nextDouble() * 2.4;
            c.limbs.add(new double[]{c.lx[at] + dx * root, c.ly[at] + dy * root, c.lz[at] + dz * root, c.lx[at] + dx * reach, c.ly[at] + dy * reach, c.lz[at] + dz * reach, rootR, tipR});
        }
        c.keepOut.add(new double[]{92, 27, -1, 112, 37, 10});
        // 大厅底下的花园层、隧道、更衣室和楼梯井：大空腔的肉壁不能从这些屋子里穿过去。
        // 不然在大厅里（这时候画大空腔）顺着敞开的楼梯井往下看，花园层地上铺着一片红亮的肉，像一池水；走下楼梯换成花园层，它又不画了，像水消失了
        c.keepOut.add(new double[]{114, 17, -14, 172, 30, 12});
        return c;
    }

    /** 地面站到肉口：荒废 19 年，肉从坑里长出来，把整个地面站罩进一个穹顶。抬头不再是虚空。 */
    static Chamber surface(Route route) {
        double to = route.distAtY(ParkPlan.THROAT_TOP + 4);
        int n = (int) (to / 2.5) + 1;
        List<double[]> line = new ArrayList<>();
        double[] p = new double[3];
        for (int i = 0; i < n; i++) {
            route.at(to * i / (n - 1), p);
            line.add(new double[]{p[0] + Route.CABIN_CENTER_X, p[1] + 1.3, p[2]});
        }
        Chamber c = new Chamber(Style.SURFACE, line, new int[0], 5.5);
        // 从穹顶垂下来的肉团，像钟乳石
        Random rnd = new Random(13);
        for (int i = 0; i < 46; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, k = Math.sqrt(rnd.nextDouble()) * 0.85;
            double x = DOME_X + Math.cos(a) * DOME_RX * k, z = Math.sin(a) * DOME_RZ * k;
            double top = DOME_Y + DOME_RY * Math.sqrt(Math.max(0, 1 - k * k)) + 4;
            double tip = top - 9 - rnd.nextDouble() * 15;
            c.limbs.add(new double[]{x, top, z, x + (rnd.nextDouble() - 0.5) * 4, tip, z + (rnd.nextDouble() - 0.5) * 4, 4 + rnd.nextDouble() * 4, 1.2 + rnd.nextDouble() * 1.8});
        }
        // 车站和锚臂的位置留空
        c.keepOut.add(new double[]{-62, 226, -10, 17, 242, 18});
        return c;
    }

    /** 腺泡：一团厚肉，中间一条刚够爬的管子，出口朝着护林站。 */
    static Chamber burrow() {
        List<double[]> line = new ArrayList<>();
        for (double x = Lower.BURROW_X0 - 2; x <= Lower.BURROW_X1 + 0.5; x += 0.5) line.add(new double[]{x, Lower.BURROW_Y + 0.42, Lower.BURROW_Z + 0.5});
        return new Chamber(Style.BURROW, line, new int[0], 0);
    }

    /**
     * 深井：一根竖着的肉管子，井口压着上面那座楼的底（一大把往下垂的肉根），井底是平的，真游客中心躺在上面。
     * 人走的平台、桁架、绳梯和真楼都留空。
     */
    static Chamber well() {
        List<double[]> line = new ArrayList<>();
        for (double y = Well.BOTTOM - 6; y <= Well.TOP + 6; y += 2.5) line.add(new double[]{Well.SX, y, Well.SZ});
        Chamber c = new Chamber(Style.WELL, line, new int[0], 0);
        Random rnd = new Random(31);
        // 肉根：从井口边上一路垂下来。中间留出来，抬头看得见上面那座楼的底：钢梁、楼板、楼板上的洞里漏下来的灯
        for (int i = 0; i < 20; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, k = 0.55 + rnd.nextDouble() * 0.4;
            double x = Well.SX + Math.cos(a) * 32 * k, z = Well.SZ + Math.sin(a) * 30 * k;
            double tip = Well.TOP - 8 - rnd.nextDouble() * (i < 6 ? 36 : 16);
            c.limbs.add(new double[]{x, Well.TOP + 3, z, x + (rnd.nextDouble() - 0.5) * 5, tip, z + (rnd.nextDouble() - 0.5) * 5, 1.8 + rnd.nextDouble() * 1.8, 0.6 + rnd.nextDouble() * 0.8});
        }
        // 几根细的从楼板的洞里垂下来
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, k = rnd.nextDouble() * 0.5;
            double x = Well.SX + Math.cos(a) * 30 * k, z = Well.SZ + Math.sin(a) * 30 * k;
            c.limbs.add(new double[]{x, Well.TOP + 2, z, x + (rnd.nextDouble() - 0.5) * 3, Well.TOP - 6 - rnd.nextDouble() * 10, z + (rnd.nextDouble() - 0.5) * 3, 0.9, 0.4});
        }
        // 人走的地方
        c.keepOut.add(new double[]{Well.CAB_X - 2, Well.CAB_Y - 2, Well.PATH_Z0 - 3, Well.LEDGE_X1 + 1, Well.CAB_Y + 5, Well.PATH_Z1 + 3});
        for (int i = 0; i < Well.STEPS; i += 4) {
            double x = Well.TRUSS_X0 + i, y = Well.stepY(i);
            c.keepOut.add(new double[]{x - 1, y - 5, Well.PATH_Z0 - 3, x + 5, y + 5, Well.PATH_Z1 + 3});
        }
        c.keepOut.add(new double[]{Well.FOOT_X0 - 1, Well.BAG_Y - 4, Well.PATH_Z0 - 9, Well.LADDER_X + 1.2, Well.FOOT_Y + 5, Well.PATH_Z1 + 3});
        // 车顺着断缆滑下去的那条：每隔一段一个盒子
        for (int k = 0; k <= 12; k++) {
            double t = k / 12.0;
            double x = Well.CAR_FROM[0] + (Well.CAR_TO[0] - Well.CAR_FROM[0]) * t, y = Well.CAR_FROM[1] + (Well.CAR_TO[1] - Well.CAR_FROM[1]) * t, z = Well.CAR_FROM[2] + (Well.CAR_TO[2] - Well.CAR_FROM[2]) * t;
            c.keepOut.add(new double[]{x - 8, y - 2, z - 4, x + 3, y + 6, z + 4});
        }
        // 真楼和井底的落脚处
        c.keepOut.add(new double[]{ParkPlan.DRUM_X + Well.DX - ParkPlan.DRUM_R - 16, Well.REAL_FLOOR, -ParkPlan.DRUM_R - 1, ParkPlan.DRUM_X + Well.DX + ParkPlan.DRUM_R + 1, Well.REAL_FLOOR + 24, ParkPlan.DRUM_R + 1});
        c.keepOut.add(new double[]{Well.LAND_X0 - 4, Well.REAL_FLOOR, Well.LAND_Z0 - 1, Well.LAND_X1 + 4, Well.REAL_FLOOR + 8, -20});
        c.keepOut.add(new double[]{108 + Well.DX, Well.REAL_GARDEN - 2, -16, Well.ROOM_X1 + 1, Well.REAL_FLOOR, 14});
        return c;
    }

    /** 地面穹顶：中心、半径。要罩住车站和六根锚臂。 */
    static final double DOME_X = 28, DOME_Y = 227, DOME_RX = 56, DOME_RY = 33, DOME_RZ = 48;

    /** 支气管森林：几条栈道穿过一片连在一起的巨大肉枝。 */
    static Chamber forest(List<List<double[]>> lines, List<double[]> keepOut) {
        List<double[]> line = new ArrayList<>();
        int[] breaks = new int[lines.size()];
        for (int k = 0; k < lines.size(); k++) {
            breaks[k] = line.size();
            List<double[]> one = lines.get(k);
            double walked = 99;
            double[] last = null;
            for (double[] q : one) {
                if (last != null) walked += Math.sqrt(sq(q[0] - last[0]) + sq(q[1] - last[1]) + sq(q[2] - last[2]));
                last = q;
                if (walked >= 2.5) {
                    line.add(new double[]{q[0], q[1] + 1.2, q[2]});
                    walked = 0;
                }
            }
        }
        Chamber c = new Chamber(Style.FOREST, line, breaks, 3.8);
        c.keepOut.addAll(keepOut);
        // 主支气管从上面长下来，一路分叉，枝干贴着栈道过去
        Random rnd = new Random(9);
        for (int i = 0; i < 26; i++) {
            double[] s = line.get(rnd.nextInt(line.size()));
            double a = rnd.nextDouble() * 6.283, off = 9 + rnd.nextDouble() * 15;
            double[] top = {s[0] + Math.cos(a) * off, s[1] + 34 + rnd.nextDouble() * 20, s[2] + Math.sin(a) * off};
            c.grow(top, new double[]{(rnd.nextDouble() - 0.5) * 0.5, -1, (rnd.nextDouble() - 0.5) * 0.5}, 5 + rnd.nextDouble() * 3.5, 15 + rnd.nextDouble() * 8, 0, rnd);
        }
        return c;
    }

    private void grow(double[] from, double[] dir, double radius, double len, int depth, Random rnd) {
        double dl = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        double[] to = {from[0] + dir[0] / dl * len, from[1] + dir[1] / dl * len, from[2] + dir[2] / dl * len};
        limbs.add(new double[]{from[0], from[1], from[2], to[0], to[1], to[2], radius, radius * 0.72});
        if (depth >= 3 || to[1] < 50) return;
        int kids = depth == 0 ? 3 : 2;
        for (int k = 0; k < kids; k++) {
            double a = rnd.nextDouble() * 6.283, spread = 0.45 + rnd.nextDouble() * 0.6;
            grow(to, new double[]{dir[0] / dl + Math.cos(a) * spread, dir[1] / dl * (rnd.nextDouble() < 0.2 ? -0.3 : 1), dir[2] / dl + Math.sin(a) * spread}, radius * 0.68, len * 0.78, depth + 1, rnd);
        }
    }

    /** 大于 0 是肉。 */
    public double f(double x, double y, double z) {
        // 离路线最近的点
        double best = 1e18;
        int at = 0;
        for (int i = 0; i < lx.length; i++) {
            double dd = sq(x - lx[i]) + sq(y - ly[i]) + sq(z - lz[i]);
            if (dd < best) {
                best = dd;
                at = i;
            }
        }
        double d = Math.sqrt(best), s = ls[at];
        if (style == Style.BURROW) {
            double cy = Lower.BURROW_Y + 0.42, cz = Lower.BURROW_Z + 0.5;
            double tx = Math.max(Lower.BURROW_X0 - 0.3, Math.min(Lower.BURROW_X1 + 7, x));
            double tunnel = Math.sqrt(sq(x - tx) + sq(y - cy) + sq(z - cz));
            double lumps = (Noise.fbm(x * 0.18, y * 0.18, z * 0.18, 3) - 0.5) * 3.5;
            double mass = 4.6 + lumps - d;
            return Math.min(mass, tunnel - 0.95);
        }
        double warp = Noise.fbm(x * 0.03 + 7, y * 0.03, z * 0.03, 2);
        double n1 = Noise.fbm(x * 0.02 + warp * 2, y * 0.02, z * 0.02 + 3, 3);
        double lump = Noise.fbm(x * 0.085 + warp * 3, y * 0.085, z * 0.085, 2) - 0.5;
        double r;
        if (style == Style.RIDE) {
            double u = s / length;
            double base = Noise.mix(Noise.mix(22, 19, Noise.smooth(0.02, 0.2, u)), 17, Noise.smooth(0.72, 1, u));
            r = base * (0.5 + 1.1 * n1);
            // 一道道环形的肌肉带：车从里面穿过去，这才有在往里走的感觉
            double wave = 0.5 + 0.5 * Math.cos(2 * Math.PI * s / 17 + warp * 5);
            r -= wave * wave * wave * (5 + 7 * Noise.fbm(x * 0.05, y * 0.05 + 9, z * 0.05, 2));
            // 顺着走向的大皱襞
            double phi = Math.atan2(z - lz[at], y - ly[at]);
            r += Math.cos(phi * 6 + s * 0.05 + warp * 6) * (2 + 0.12 * base);
            r -= lump * 9;
        } else if (style == Style.FOREST) {
            r = 13 * (0.55 + 1.1 * n1) - lump * 8;
        } else if (style == Style.WELL) {
            double ang = Math.atan2(z - Well.SZ, x - Well.SX);
            r = Well.radius(y) * (0.9 + 0.22 * n1) - lump * 5 + Math.cos(ang * 7 + y * 0.035 + warp * 5) * 1.4;
        } else {
            r = 0;
        }
        double v = d - r;
        if (style == Style.SURFACE) {
            double ex = (x - DOME_X) / DOME_RX, ey = (y - DOME_Y) / DOME_RY, ez = z / DOME_RZ;
            v = (Math.sqrt(ex * ex + ey * ey + ez * ez) - 1) * 30;
            v += (n1 - 0.5) * 16 + lump * 7;
            // 一道道拱肋从四周爬上穹顶
            double ang = Math.atan2(z, x - DOME_X);
            double rib = 0.5 + 0.5 * Math.cos(ang * 9 + warp * 5 + y * 0.04);
            v += rib * rib * rib * rib * 6 * Noise.smooth(232, 246, y);
            // 地面以下不长肉：看不见，也省得把肉口堵住
            v = Math.min(v, y - 226);
        }
        if (style == Style.WELL) {
            // 井口以上、井底以下都是肉
            v = Math.max(v, Math.max(Well.BOTTOM - y, y - Well.TOP));
        }
        // 深沟
        double groove = 1 - Math.abs(2 * Noise.fbm(x * 0.15 + warp * 4, y * 0.15, z * 0.15, 2) - 1);
        v -= Noise.smooth(0.72, 1, groove) * 0.9;
        // 肉团、肉枝
        for (double[] m : limbs) {
            double c = capsule(x, y, z, m);
            if (c < -40) continue;
            double wobble = (lump * 2.4 + (warp - 0.5) * 3);
            v = smax(v, c + wobble, 5);
        }
        // 车和缆绳、栈道要穿过去
        v = smin(v, d - clear, style == Style.FOREST ? 1.5 : 3);
        if (style == Style.RIDE) {
            if (x < ParkPlan.DRUM_X + 8) {
                double dr = Math.hypot(x - ParkPlan.DRUM_X, z) - (ParkPlan.DRUM_R + 3.5);
                v = smin(v, Math.max(dr, Math.abs(y - 36) - 15), 4);
            }
        }
        for (double[] b : keepOut) {
            double out = Math.max(Math.max(Math.max(b[0] - x, x - b[3]), Math.max(b[1] - y, y - b[4])), Math.max(b[2] - z, z - b[5]));
            v = smin(v, out, 2.5);
        }
        return v;
    }

    /** 胶囊里面是正的：离表面多深。 */
    private static double capsule(double x, double y, double z, double[] m) {
        double bx = m[3] - m[0], by = m[4] - m[1], bz = m[5] - m[2];
        double px = x - m[0], py = y - m[1], pz = z - m[2];
        double t = Math.max(0, Math.min(1, (px * bx + py * by + pz * bz) / (bx * bx + by * by + bz * bz)));
        double dist = Math.sqrt(sq(px - bx * t) + sq(py - by * t) + sq(pz - bz * t));
        return m[6] + (m[7] - m[6]) * t - dist;
    }

    /** 从路线往某个方向走，碰到肉的地方：吊架的斜撑拉到这里。 */
    public double[] hit(double x, double y, double z, double dx, double dy, double dz, double limit) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (double t = 2; t < limit; t += 1) {
            double px = x + dx / len * t, py = y + dy / len * t, pz = z + dz / len * t;
            if (f(px, py, pz) > 0.5) return new double[]{px, py, pz};
        }
        return new double[]{x + dx / len * limit, y + dy / len * limit, z + dz / len * limit};
    }

    private static double smin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    private static double smax(double a, double b, double k) {
        return -smin(-a, -b, k);
    }

    private static double sq(double v) {
        return v * v;
    }
}

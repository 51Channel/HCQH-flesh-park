package world.hechao.fleshpark.core;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 深井里看得见的钢结构和残骸：电梯门口的格栅平台、检修铁楼梯、横穿井心的工字钢梁、楼塌下来卡在井壁上的楼板、格栅走道、风管、灯箱。
 * 构建时按 Well.HOLDS 一处一处生成四边形网格，烘进客户端模组（steel.bin），插件按锚点表在每一处放一个锚点；另存一份 OBJ 方便拿出去看。
 * 脚下踩的是看不见的方块（Well.holds），模型的踩面和它们对齐；楼梯扶手外面有看不见的墙。
 */
public final class Rig {
    /** 材质，和客户端模组 textures/steel/ 下的贴图一一对应。 */
    public static final String[] MATERIALS = {"rust", "paint", "tread", "grate", "terrazzo", "concrete", "duct", "sign", "tread_old", "paint_old"};
    static final int RUST = 0, PAINT = 1, TREAD = 2, GRATE = 3, TERRAZZO = 4, CONCRETE = 5, DUCT = 6, SIGN = 7, TREAD_OLD = 8, PAINT_OLD = 9;
    /** 每种材质贴图一张盖多少格。 */
    private static final double[] TILE = {1.5, 1.0, 1.0, 1.0, 1.0, 2.0, 1.2, 1.0, 1.0, 1.0};

    /** 一段网格：锚点在 ox、oy、oz（落脚处正中、脚上一格的空气里，亮度按这里取）。 */
    public static final class Piece {
        public int id;
        public double ox, oy, oz;
        public float sx, sy, sz;
        /** 每种材质一串四边形：每个是 4 个顶点 × (x, y, z, u, v) 再加法线 (nx, ny, nz)，坐标是世界坐标。 */
        @SuppressWarnings("unchecked")
        final List<double[]>[] quads = new List[MATERIALS.length];

        Piece() {
            for (int i = 0; i < quads.length; i++) quads[i] = new ArrayList<>();
        }

        int count() {
            int n = 0;
            for (List<double[]> q : quads) n += q.size();
            return n;
        }

        public int count(int material) {
            return quads[material].size();
        }
    }

    public final List<Piece> pieces = new ArrayList<>();
    private Piece cur;
    private final Random rnd = new Random(9071);

    public Rig() {
        for (Well.Hold h : Well.HOLDS) {
            if (h.kind == 'P') continue;
            begin(h);
            switch (h.kind) {
                case 'L' -> landing(h);
                case 'S' -> stair(h);
                case 'B' -> beam(h);
                case 'D' -> {
                    switch (h.style) {
                        case Well.GRATING -> catwalk(h);
                        case Well.DUCT -> duct(h);
                        case Well.SIGN -> sign(h);
                        default -> slab(h);
                    }
                }
                default -> {
                }
            }
            finish();
        }
    }

    private void begin(Well.Hold h) {
        cur = new Piece();
        cur.id = pieces.size();
        double[] c = h.center();
        cur.ox = c[0];
        cur.oy = h.top() + 1.0;
        cur.oz = c[2];
    }

    private void finish() {
        double x0 = 1e9, y0 = 1e9, z0 = 1e9, x1 = -1e9, y1 = -1e9, z1 = -1e9;
        for (List<double[]> list : cur.quads) {
            for (double[] q : list) {
                for (int v = 0; v < 4; v++) {
                    x0 = Math.min(x0, q[v * 5]);
                    x1 = Math.max(x1, q[v * 5]);
                    y0 = Math.min(y0, q[v * 5 + 1]);
                    y1 = Math.max(y1, q[v * 5 + 1]);
                    z0 = Math.min(z0, q[v * 5 + 2]);
                    z1 = Math.max(z1, q[v * 5 + 2]);
                }
            }
        }
        cur.sx = (float) (x1 - x0);
        cur.sy = (float) (y1 - y0);
        cur.sz = (float) (z1 - z0);
        if (cur.count() > 0) pieces.add(cur);
    }

    // ---------- 落脚的地方 ----------

    /** 电梯门口那块旧员工平台：铁格栅，底下几道角钢，靠井心那一沿一道黄漆栏杆，往下跳的那头没有栏杆。 */
    private void landing(Well.Hold h) {
        double top = h.top(), r0 = h.rIn + 0.05, r1 = h.rOut, a = h.phiA, b = h.phiB;
        arcPlate(GRATE, GRATE, a, b, r0, r1, top, 0.05, true);
        arcBar(RUST, a, b, r0 + 0.04, top - 0.05, 0.08, 0.16);
        arcBar(RUST, a, b, r1 - 0.1, top - 0.05, 0.08, 0.16);
        for (double phi = a + 0.05; phi < b; phi += 0.7 / ((r0 + r1) / 2)) radialBar(RUST, phi, r0 + 0.05, r1 - 0.05, top - 0.07, 0.07, 0.12);
        double rail = r0 - 0.02, cut = b - 1.4 / rail;
        railing(a + 0.02, cut, rail, phi -> top, false, true);
    }

    /**
     * 一截断楼梯：检修用的铁楼梯，两道槽钢斜梁，花纹钢板踏步，一级一道立板；靠井心那一沿黄漆扶手和立杆，靠井壁那一侧隔一段一根斜撑扎进肉里。
     * 用了很多年、又被它撑歪了：整截往井心那边拧着歪（Hold.lean），断口那头往下耷拉；一级是几块长短不一的钢板拼的，各自翘一点、
     * 有的锈透了、有的换成了格栅、有的缺一角压瘪一角；立板有的掉了；扶手立杆东倒西歪，有的断了只剩一截，扶手塌下去一段，
     * 中间那道横杆缺一截；两头是断口：斜梁撕得一长一短，踏板翻下去一块，扶手拧弯了伸出去。
     */
    private void stair(Well.Hold h) {
        Random rr = new Random(4021L * h.index + 77);
        double r0 = h.rIn + 0.1, r1 = h.rOut - 0.02, rm = (r0 + r1) / 2;
        double[][] z = h.zones;
        // 这一截有多旧：越旧，锈透的踏板越多、扶手的漆掉得越光
        double age = rr.nextDouble();
        int railMat = age > 0.5 ? PAINT_OLD : PAINT;
        for (int k = 0; k < z.length; k++) {
            final double a = z[k][0], b = z[k][1], top = z[k][2], len = (b - a) * rm;
            // 一级拆成几块钢板，长短不一，接缝处空一条
            int n = Math.max(1, (int) Math.round(len / (0.7 + rr.nextDouble() * 0.6)));
            double[] cut = new double[n + 1];
            double[] wts = new double[n];
            double total = 0;
            for (int i = 0; i < n; i++) total += wts[i] = 0.5 + rr.nextDouble();
            cut[0] = a;
            for (int i = 0; i < n; i++) cut[i + 1] = cut[i] + (b - a) * wts[i] / total;
            for (int i = 0; i < n; i++) {
                double pa = cut[i] + (i > 0 ? 0.02 / rm : 0), pb = cut[i + 1] - (i < n - 1 ? 0.02 / rm : 0), pc = (pa + pb) / 2;
                double roll = (rr.nextDouble() - 0.5) * 0.07, pitch = (rr.nextDouble() - 0.5) * 0.05, lift = (rr.nextDouble() - 0.5) * 0.035;
                // 靠井心那一沿：有的短一截、缺一角
                double inset = rr.nextDouble() < 0.3 ? 0.08 + rr.nextDouble() * 0.25 : rr.nextDouble() * 0.05;
                double pr0 = r0 + inset, pr1 = r1 - rr.nextDouble() * 0.06;
                // 压瘪的一角（靠井心那边，前角或者后角）
                double dent = rr.nextDouble() < 0.3 ? 0.06 + rr.nextDouble() * 0.08 : 0;
                double dentPhi = rr.nextBoolean() ? pb : pa;
                double m = rr.nextDouble();
                int mat = m < 0.14 ? GRATE : m < 0.2 + age * 0.55 ? TREAD_OLD : TREAD;
                java.util.function.DoubleBinaryOperator f = (phi, r) -> top + h.warp(phi, r) + lift + roll * (r - h.rWalk) + pitch * (phi - pc) * rm
                        - dent * Math.max(0, 1 - Math.abs(phi - dentPhi) * rm / 0.45) * Math.max(0, 1 - (r - pr0) / 0.55);
                warpPlate(mat, mat == GRATE ? RUST : mat, pa, pb, pr0, pr1, f, mat == GRATE ? 0.05 : 0.06, dent > 0 ? 3 : 1, mat == GRATE);
                // 前沿（往下走的那一边）一道黄漆防滑条：有的磨没了，有的只剩一截
                if (i == n - 1 && rr.nextDouble() < 0.75 && mat != GRATE) {
                    double s0 = pr0, s1 = pr1;
                    if (rr.nextDouble() < 0.4) {
                        s0 = pr0 + (pr1 - pr0) * rr.nextDouble() * 0.5;
                        s1 = s0 + (pr1 - s0) * (0.3 + rr.nextDouble() * 0.5);
                    }
                    warpPlate(age > 0.6 ? PAINT_OLD : PAINT, PAINT, pb - 0.1 / rm, pb, s0, s1, (phi, r) -> f.applyAsDouble(phi, r) + 0.012, 0.014, 1, false);
                }
            }
            // 立板：上一级前沿到这一级，有的掉了，有的撕掉半边
            if (k > 0 && rr.nextDouble() < 0.65) {
                double up = z[k - 1][2], q0 = rr.nextDouble() < 0.3 ? r0 + (r1 - r0) * (0.3 + rr.nextDouble() * 0.4) : r0;
                double[] p0 = Well.at(a, q0, up + h.warp(a, q0) - 0.06), p1 = Well.at(a, r1, up + h.warp(a, r1) - 0.06);
                double[] p2 = Well.at(a, r1, top + h.warp(a, r1) - 0.06), p3 = Well.at(a, q0, top + h.warp(a, q0) - 0.06);
                quad(RUST, p0, p1, p2, p3, tangent(a));
            }
        }
        double start = z[0][0], end = z[z.length - 1][1];
        // 两道斜梁：踏步前沿连成的那条线底下，跟着楼梯一起歪；断口撕得一长一短，尖上往下拧
        for (double r : new double[]{r0 + 0.03, r1 - 0.06}) {
            double from = start - (0.05 + rr.nextDouble() * 0.3) / rm, to = end + (0.05 + rr.nextDouble() * 0.45) / rm;
            double[] last = null;
            for (double phi = from; ; phi += 0.06) {
                double p1 = Math.min(to, phi + 0.06);
                double[] a = Well.at(phi, r, h.nose(phi) + h.warp(phi, r) - 0.09), b = Well.at(p1, r, h.nose(p1) + h.warp(p1, r) - 0.09);
                channel(a, b, 0.07, 0.34, postDir(h, phi));
                last = b;
                if (p1 >= to) break;
            }
            double[] tip = normal(add(tangent(to), mul(UP, -1.1 - rr.nextDouble())));
            bar(RUST, add(last, mul(UP, -0.17)), add(add(last, mul(UP, -0.17)), mul(tip, 0.18 + rr.nextDouble() * 0.2)), 0.06, 0.3, null);
        }
        // 斜撑：从外侧斜梁底下扎进井壁，隔得有远有近，有一根断了只剩一截挂着
        for (double phi = start + (0.4 + rr.nextDouble() * 0.5) / rm; phi < end - 0.3 / rm; phi += (1.6 + rr.nextDouble() * 1.3) / rm) {
            double rs = r1 - 0.08, y = h.nose(phi) + h.warp(phi, rs) - 0.45;
            double[] s = Well.at(phi, rs, y);
            double[] e = Well.at(phi + (rr.nextDouble() - 0.5) * 0.2 / rm, r1 + 0.9 + rr.nextDouble() * 0.5, y - 1.0 - rr.nextDouble() * 0.6);
            if (rr.nextDouble() < 0.2) {
                double[] d = normal(sub(e, s));
                double[] m = add(s, mul(d, 0.3));
                bar(RUST, s, m, 0.09, 0.09, UP);
                bar(RUST, m, add(m, mul(normal(add(d, mul(UP, -2))), 0.3)), 0.09, 0.09, UP);
            } else {
                bar(RUST, s, e, 0.09, 0.09, UP);
            }
        }
        stairRailing(h, rr, start + 0.25 / rm, end - 0.1 / rm, railMat, age);
        // 断口：最后一块踏板翻下去挂着
        double tip = end, ty = z[z.length - 1][2] + h.warp(end, rm);
        double[] hinge = Well.at(tip, rm, ty - 0.04);
        double[] dir = tangent(tip), radial = radial(tip);
        double ang = Math.toRadians(50 + rr.nextInt(35)), len = 0.4 + rr.nextDouble() * 0.4;
        double[] down = {dir[0] * Math.cos(ang), -Math.sin(ang), dir[2] * Math.cos(ang)};
        double[] c = add(hinge, mul(down, len / 2));
        double wide = (r1 - r0) * (0.3 + rr.nextDouble() * 0.15);
        box(age > 0.4 ? TREAD_OLD : TREAD, add(c, mul(radial, (rr.nextDouble() - 0.5) * (r1 - r0) * 0.4)), mul(down, len / 2), mul(radial, wide), mul(normal(cross(down, radial)), 0.03));
    }

    /** 断楼梯扶手立杆的方向：跟着楼梯往井心那边歪。 */
    private static double[] postDir(Well.Hold h, double phi) {
        double[] a = h.rail(phi, 0), b = h.rail(phi, 1);
        return normal(sub(b, a));
    }

    /**
     * 断楼梯的扶手：立杆隔得不匀、东倒西歪，漆掉了的露锈，有一根断了只剩一截；上面那道扶手有一段塌下去（拧弯了），中间那道横杆缺一截。
     * 起头那截弯下去，断口那头伸出去拧弯。挂检修灯的那一段不塌（灯挂在 Well.lamps 里，按 Hold.rail 放）。
     */
    private void stairRailing(Well.Hold h, Random rr, double a, double b, int mat, double age) {
        double r = h.railR();
        List<Double> posts = new ArrayList<>();
        for (double phi = a; phi < b - 0.4 / r; phi += (0.9 + rr.nextDouble() * 0.4) / r) posts.add(phi);
        posts.add(b);
        int np = posts.size();
        // 每根立杆顶上歪出去多少
        double[][] wob = new double[np][];
        for (int i = 0; i < np; i++) {
            double k = 0.03 + age * 0.06;
            wob[i] = add(mul(tangent(posts.get(i)), (rr.nextDouble() - 0.5) * 2 * k), mul(radial(posts.get(i)), (rr.nextDouble() - 0.6) * 2 * k));
        }
        int broken = np > 3 && rr.nextDouble() < 0.5 ? 1 + rr.nextInt(np - 3) : -1;
        double lampPhi = h.zones[h.zones.length / 2][0] + 0.02;
        int sag = -1, gap = -1;
        if (np > 2 && rr.nextDouble() < 0.6) {
            sag = rr.nextInt(np - 1);
            if (lampPhi >= posts.get(sag) - 0.1 / r && lampPhi <= posts.get(sag + 1) + 0.1 / r) sag = -1;
        }
        if (np > 2 && rr.nextDouble() < 0.45) gap = rr.nextInt(np - 1);
        double sagDepth = 0.15 + rr.nextDouble() * 0.17;
        for (int i = 0; i < np - 1; i++) {
            double phi = posts.get(i);
            double[] base = h.rail(phi, 0), top = add(h.rail(phi, 1.02), wob[i]);
            int pm = rr.nextDouble() < 0.15 ? RUST : mat;
            if (i == broken) {
                // 断了只剩一截，断口往外撇
                double[] m = add(base, mul(sub(top, base), 0.22 + rr.nextDouble() * 0.15));
                bar(pm, base, m, 0.065, 0.065, null);
                bar(pm, m, add(m, mul(normal(add(sub(top, base), mul(radial(phi), -0.8))), 0.12)), 0.06, 0.06, null);
            } else {
                bar(pm, base, top, 0.065, 0.065, null);
            }
        }
        for (double hgt : new double[]{1.0, 0.5}) {
            for (int i = 0; i < np - 1; i++) {
                double p0 = posts.get(i), p1 = posts.get(i + 1);
                boolean missing = hgt < 0.7 && i == gap;
                int steps = Math.max(2, (int) Math.ceil((p1 - p0) * r / 0.3));
                double[] last = null;
                for (int s = 0; s <= steps; s++) {
                    double u = (double) s / steps, phi = p0 + (p1 - p0) * u;
                    double[] w = add(mul(wob[i], 1 - u), mul(wob[i + 1], u));
                    double[] q = add(h.rail(phi, hgt), mul(w, hgt / 1.02));
                    if (hgt > 0.7 && i == sag) q[1] -= sagDepth * (1 - Math.abs(2 * u - 1));
                    if (last != null) {
                        // 缺的那截：两头各剩一小截，往下耷拉
                        if (!missing) bar(mat, last, q, 0.055, 0.055, UP);
                        else if (s == 1) bar(mat, last, add(last, mul(normal(add(tangent(p0), mul(UP, -0.8))), 0.16)), 0.05, 0.05, UP);
                        else if (s == steps) bar(mat, q, add(q, mul(normal(add(mul(tangent(p1), -1), mul(UP, -0.8))), 0.16)), 0.05, 0.05, UP);
                    }
                    last = q;
                }
            }
            // 尾巴：顺着走向伸出去一截，往下、往井心拧弯
            double[] p = add(h.rail(b, hgt), mul(wob[np - 1], hgt / 1.02));
            double[] t = tangent(b), in = mul(radial(b), -1);
            double[] d1 = normal(add(add(t, mul(UP, -0.35 - rr.nextDouble() * 0.3)), mul(in, 0.25)));
            double[] q1 = add(p, mul(d1, 0.3 + rr.nextDouble() * 0.3));
            double[] d2 = normal(add(add(t, mul(UP, -1.2)), mul(in, 0.6)));
            bar(mat, p, q1, 0.055, 0.055, UP);
            bar(mat, q1, add(q1, mul(d2, hgt > 0.7 ? 0.5 : 0.3)), 0.055, 0.055, UP);
            // 起头那截弯下去
            double[] s0 = add(h.rail(a, hgt), mul(wob[0], hgt / 1.02));
            double[] d = normal(add(mul(tangent(a), -1), mul(UP, -0.9)));
            bar(mat, s0, add(s0, mul(d, 0.35)), 0.055, 0.055, UP);
        }
        // 扶手上挂着一截电线，往下垂
        if (rr.nextDouble() < 0.5) {
            double phi = a + (b - a) * (0.2 + rr.nextDouble() * 0.6);
            double[] q = h.rail(phi, 0.98);
            double drop = 0.5 + rr.nextDouble() * 0.9;
            bar(RUST, q, add(q, new double[]{(rr.nextDouble() - 0.5) * 0.15, -drop, (rr.nextDouble() - 0.5) * 0.15}), 0.025, 0.025, null);
        }
    }

    /** 顺着井壁弯的一块板，上面那一面按 f(φ, r) 高低起伏（歪的、翘的、压瘪的）：φ a..b、离井心 r0..r1，厚 thick，径向分 across 段。 */
    private void warpPlate(int topMat, int sideMat, double a, double b, double r0, double r1, java.util.function.DoubleBinaryOperator f, double thick, int across, boolean twoSided) {
        int n = Math.max(1, (int) Math.ceil((b - a) * (r0 + r1) / 2 / 0.45));
        for (int i = 0; i < n; i++) {
            double p0 = a + (b - a) * i / n, p1 = a + (b - a) * (i + 1) / n, mid = (p0 + p1) / 2;
            for (int j = 0; j < across; j++) {
                double q0 = r0 + (r1 - r0) * j / across, q1 = r0 + (r1 - r0) * (j + 1) / across;
                double[] t0 = Well.at(p0, q0, f.applyAsDouble(p0, q0)), t1 = Well.at(p1, q0, f.applyAsDouble(p1, q0));
                double[] t2 = Well.at(p1, q1, f.applyAsDouble(p1, q1)), t3 = Well.at(p0, q1, f.applyAsDouble(p0, q1));
                double[] b0 = down(t0, thick), b1 = down(t1, thick), b2 = down(t2, thick), b3 = down(t3, thick);
                quad(topMat, t0, t1, t2, t3, UP);
                quad(twoSided ? topMat : sideMat, b3, b2, b1, b0, mul(UP, -1));
                if (j == 0) quad(sideMat, t0, b0, b1, t1, mul(radial(mid), -1));
                if (j == across - 1) quad(sideMat, t3, t2, b2, b3, radial(mid));
                if (i == 0) quad(sideMat, t3, b3, b0, t0, mul(tangent(p0), -1));
                if (i == n - 1) quad(sideMat, t1, b1, b2, t2, tangent(p1));
            }
        }
    }

    /** 扶手立杆、上下两道扶手，按 y(φ) 走。bendEnd：尾巴伸出去拧弯；bendStart：起头那截弯下去。 */
    private void railing(double a, double b, double r, java.util.function.DoubleUnaryOperator y, boolean bendStart, boolean bendEnd) {
        double step = 1.1 / r;
        int posts = Math.max(2, (int) Math.round((b - a) / step) + 1);
        for (int i = 0; i < posts; i++) {
            double phi = a + (b - a) * i / (posts - 1);
            // 断口那头少一根立杆
            if (bendEnd && i == posts - 1) continue;
            double base = y.applyAsDouble(phi);
            bar(PAINT, Well.at(phi, r, base), Well.at(phi, r, base + 1.02), 0.065, 0.065, null);
        }
        for (double h : new double[]{1.0, 0.5}) {
            double last = a;
            for (double phi = a + 0.05; ; phi += 0.05) {
                double p1 = Math.min(b, phi);
                bar(PAINT, Well.at(last, r, y.applyAsDouble(last) + h), Well.at(p1, r, y.applyAsDouble(p1) + h), 0.055, 0.055, UP);
                last = p1;
                if (p1 >= b) break;
            }
            // 尾巴：顺着走向伸出去一截，往下、往井心拧弯
            if (bendEnd) {
                double[] p = Well.at(b, r, y.applyAsDouble(b) + h);
                double[] t = tangent(b), in = mul(radial(b), -1);
                double[] d1 = normal(add(add(t, mul(UP, -0.35)), mul(in, 0.25)));
                double[] p1 = add(p, mul(d1, 0.45));
                double[] d2 = normal(add(add(t, mul(UP, -1.2)), mul(in, 0.6)));
                bar(PAINT, p, p1, 0.055, 0.055, UP);
                bar(PAINT, p1, add(p1, mul(d2, h > 0.7 ? 0.5 : 0.3)), 0.055, 0.055, UP);
            }
            if (bendStart) {
                double[] p = Well.at(a, r, y.applyAsDouble(a) + h);
                double[] t = mul(tangent(a), -1);
                double[] d = normal(add(t, mul(UP, -0.9)));
                bar(PAINT, p, add(p, mul(d, 0.35)), 0.055, 0.055, UP);
            }
        }
    }

    /**
     * 一根工字钢梁：上翼缘就是踩的那一面（0.9 宽），腹板、下翼缘，隔一段一道加劲肋，隔几格一块拼接板和一圈螺栓。
     * 插进井壁的那头多伸进去一截；断口那头翼缘撕开、往下拧。
     */
    private void beam(Well.Hold h) {
        double lo = 1e9, hi = -1e9, top = h.top();
        for (double[] c : h.cells) {
            double along = h.axis == 0 ? c[0] : c[2];
            lo = Math.min(lo, along);
            hi = Math.max(hi, along + 1);
        }
        double[] first = h.cells.get(0);
        final double fixed = (h.axis == 0 ? first[2] : first[0]) + 0.5;
        double a = h.brokenLo ? lo + 0.12 : lo - 0.9, b = h.brokenHi ? hi - 0.12 : hi + 0.9;
        double[] axis = h.axis == 0 ? new double[]{1, 0, 0} : new double[]{0, 0, 1};
        double[] side = h.axis == 0 ? new double[]{0, 0, 1} : new double[]{1, 0, 0};
        java.util.function.BiFunction<Double, Double, double[]> p = (s, y) -> h.axis == 0 ? new double[]{s, y, fixed} : new double[]{fixed, y, s};
        double mid = (a + b) / 2, half = (b - a) / 2;
        // 上翼缘、腹板、下翼缘
        box(RUST, p.apply(mid, top - 0.035), mul(axis, half), mul(UP, 0.035), mul(side, 0.45));
        box(RUST, p.apply(mid, top - 0.4), mul(axis, half), mul(UP, 0.33), mul(side, 0.045));
        box(RUST, p.apply(mid, top - 0.765), mul(axis, half), mul(UP, 0.035), mul(side, 0.33));
        // 加劲肋
        for (double s = a + 0.75; s < b - 0.4; s += 1.5) {
            for (int k = -1; k <= 1; k += 2) {
                box(RUST, add(p.apply(s, top - 0.4), mul(side, k * 0.2)), mul(axis, 0.02), mul(UP, 0.33), mul(side, 0.15));
            }
        }
        // 拼接板和螺栓
        for (double s = a + 2.2 + rnd.nextDouble(); s < b - 1; s += 4 + rnd.nextDouble() * 2) {
            for (int k = -1; k <= 1; k += 2) {
                box(RUST, add(p.apply(s, top - 0.4), mul(side, k * 0.07)), mul(axis, 0.3), mul(UP, 0.26), mul(side, 0.02));
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j += 2) {
                        box(RUST, add(p.apply(s + i * 0.2, top - 0.4 + j * 0.15), mul(side, k * 0.1)), mul(axis, 0.03), mul(UP, 0.03), mul(side, 0.03));
                    }
                }
            }
        }
        // 断口：翼缘撕开往下拧
        for (int end = 0; end < 2; end++) {
            boolean broken = end == 0 ? h.brokenLo : h.brokenHi;
            if (!broken) continue;
            double s = end == 0 ? a : b, sign = end == 0 ? -1 : 1;
            double[] d = normal(add(mul(axis, sign), mul(UP, -0.7)));
            double[] base = p.apply(s, top - 0.035);
            box(RUST, add(add(base, mul(d, 0.2)), mul(side, 0.22)), mul(d, 0.2), mul(normal(cross(d, side)), 0.03), mul(side, 0.2));
            double[] base2 = p.apply(s, top - 0.765);
            double[] d2 = normal(add(mul(axis, sign), mul(UP, 0.5)));
            box(RUST, add(add(base2, mul(d2, 0.15)), mul(side, -0.15)), mul(d2, 0.15), mul(normal(cross(d2, side)), 0.03), mul(side, 0.15));
        }
        // 隔几根挂一截断了的链子
        if (h.index % 3 == 1) {
            double s = mid + (rnd.nextDouble() - 0.5) * half;
            double[] at = p.apply(s, top - 0.8);
            int links = 6 + rnd.nextInt(8);
            for (int i = 0; i < links; i++) {
                double[] c = add(at, new double[]{0, -0.12 - i * 0.2, 0});
                double[] w = i % 2 == 0 ? axis : side;
                box(RUST, c, mul(w, 0.06), mul(UP, 0.11), mul(normal(cross(w, UP)), 0.015));
            }
        }
    }

    // ---------- 楼塌下来的残骸 ----------

    /** 一块楼板：上面是大厅那种磨石子，断面是混凝土，靠井心那一沿碎成锯齿，钢筋戳出来。靠墙那头被肉吃进去。 */
    private void slab(Well.Hold h) {
        double top = h.top(), a = h.phiA + 0.04 / h.rIn, b = h.phiB - 0.04 / h.rIn;
        int n = 10;
        double[] rin = new double[n + 1];
        for (int i = 0; i <= n; i++) rin[i] = h.rIn + 0.12 + rnd.nextDouble() * 0.32;
        double r1 = h.rOut + 0.4, depth = 0.42 + rnd.nextDouble() * 0.12;
        for (int i = 0; i < n; i++) {
            double p0 = a + (b - a) * i / n, p1 = a + (b - a) * (i + 1) / n;
            double[] t0 = Well.at(p0, rin[i], top), t1 = Well.at(p1, rin[i + 1], top), t2 = Well.at(p1, r1, top), t3 = Well.at(p0, r1, top);
            quad(TERRAZZO, t0, t1, t2, t3, UP);
            double[] b0 = down(t0, depth), b1 = down(t1, depth), b2 = down(t2, depth), b3 = down(t3, depth);
            quad(CONCRETE, b3, b2, b1, b0, mul(UP, -1));
            quad(CONCRETE, t0, b0, b1, t1, mul(radial(p0 / 2 + p1 / 2), -1));
            if (i == 0) quad(CONCRETE, t3, b3, b0, t0, mul(tangent(p0), -1));
            if (i == n - 1) quad(CONCRETE, t1, b1, b2, t2, tangent(p1));
        }
        // 钢筋：从碎沿戳出来，有的拧弯了
        int bars = 3 + rnd.nextInt(3);
        for (int k = 0; k < bars; k++) {
            int i = 1 + rnd.nextInt(n - 1);
            double phi = a + (b - a) * i / n, y = top - 0.12 - rnd.nextDouble() * (depth - 0.24);
            double[] s = Well.at(phi, rin[i] + 0.05, y), in = mul(radial(phi), -1);
            double len = 0.35 + rnd.nextDouble() * 0.6;
            double[] e = add(s, mul(normal(add(in, mul(UP, -0.25 - rnd.nextDouble() * 0.5))), len));
            bar(RUST, s, e, 0.035, 0.035, UP);
            if (rnd.nextBoolean()) bar(RUST, e, add(e, mul(normal(add(in, mul(UP, -1.5))), 0.3)), 0.035, 0.035, UP);
        }
    }

    /** 一截格栅走道：楼里通设备层的那种，格栅、两道槽钢边梁、底下几根横撑，靠井心一道栏杆，一头拧下去。 */
    private void catwalk(Well.Hold h) {
        double top = h.top(), r0 = h.rIn + 0.12, r1 = h.rOut + 0.2, a = h.phiA + 0.04 / h.rIn, b = h.phiB - 0.04 / h.rIn;
        arcPlate(GRATE, GRATE, a, b, r0, r1, top, 0.05, true);
        arcBar(RUST, a, b, r0 + 0.03, top - 0.04, 0.07, 0.2);
        arcBar(RUST, a, b, r1 - 0.25, top - 0.04, 0.07, 0.2);
        double rm = (r0 + r1) / 2;
        for (double phi = a + 0.04; phi < b; phi += 0.75 / rm) radialBar(RUST, phi, r0, r1 - 0.2, top - 0.08, 0.06, 0.1);
        railing(a + 0.12 / rm, b - 0.12 / rm, r0 - 0.03, phi -> top, rnd.nextBoolean(), true);
    }

    /** 一截风管：大口径的镀锌风管横卡在井壁上，上面能踩；隔一段一道法兰，两头敞着口，里面是黑的。 */
    private void duct(Well.Hold h) {
        double top = h.top(), r0 = h.rIn + 0.1, r1 = h.rOut + 0.3, a = h.phiA + 0.03 / h.rIn, b = h.phiB - 0.03 / h.rIn, height = 1.05;
        int n = 8;
        for (int i = 0; i < n; i++) {
            double p0 = a + (b - a) * i / n, p1 = a + (b - a) * (i + 1) / n;
            double[] t0 = Well.at(p0, r0, top), t1 = Well.at(p1, r0, top), t2 = Well.at(p1, r1, top), t3 = Well.at(p0, r1, top);
            quad(DUCT, t0, t1, t2, t3, UP);
            double[] b0 = down(t0, height), b1 = down(t1, height), b2 = down(t2, height), b3 = down(t3, height);
            quad(DUCT, b3, b2, b1, b0, mul(UP, -1));
            quad(DUCT, t0, b0, b1, t1, mul(radial(p0 / 2 + p1 / 2), -1));
        }
        // 两头敞口：口沿一圈，里面黑的
        for (double phi : new double[]{a, b}) {
            double s = phi == a ? -1 : 1;
            double[] t = mul(tangent(phi), s);
            double[] c0 = Well.at(phi, r0, top), c1 = Well.at(phi, r1, top), c2 = down(c1, height), c3 = down(c0, height);
            double[] back = mul(t, -0.25);
            quad(RUST, add(c0, back), add(c3, back), add(c2, back), add(c1, back), t);
        }
        // 法兰
        for (double phi = a + 0.2 / r0; phi < b; phi += 1.2 / ((r0 + r1) / 2)) {
            double[] i0 = Well.at(phi, r0 - 0.04, top + 0.04), i1 = Well.at(phi, r0 - 0.04, top - height - 0.04);
            bar(DUCT, i0, i1, 0.08, 0.08, null);
            bar(DUCT, Well.at(phi, r0 - 0.04, top + 0.03), Well.at(phi, r1, top + 0.03), 0.08, 0.04, UP);
        }
    }

    /** 一块灯箱招牌：楼里挂的那种，翻倒了平躺着卡在井壁上，铁框，面板碎了，里面的灯管还有一点电，一闪一闪（客户端按这块材质让它自己发光）。 */
    private void sign(Well.Hold h) {
        double top = h.top(), r0 = h.rIn + 0.12, r1 = h.rOut + 0.1, a = h.phiA + 0.05 / h.rIn, b = h.phiB - 0.05 / h.rIn, thick = 0.32;
        int n = 8;
        double rm = (r0 + r1) / 2, frame = 0.12;
        double fa = a + frame / rm, fb = b - frame / rm;
        for (int i = 0; i < n; i++) {
            double p0 = fa + (fb - fa) * i / n, p1 = fa + (fb - fa) * (i + 1) / n;
            double[] t0 = Well.at(p0, r0 + frame, top - 0.02), t1 = Well.at(p1, r0 + frame, top - 0.02), t2 = Well.at(p1, r1 - frame, top - 0.02), t3 = Well.at(p0, r1 - frame, top - 0.02);
            double u0 = (double) i / n, u1 = (double) (i + 1) / n;
            quadUv(SIGN, t0, t1, t2, t3, UP, new double[]{u0, 1, u1, 1, u1, 0, u0, 0});
        }
        // 铁框：四边
        arcBar(DUCT, a, b, r0 + frame / 2, top, frame, thick);
        arcBar(DUCT, a, b, r1 - frame / 2, top, frame, thick);
        for (double phi : new double[]{a + frame / 2 / rm, b - frame / 2 / rm}) radialBar(DUCT, phi, r0, r1, top, frame, thick);
        // 底板
        arcPlate(DUCT, DUCT, a, b, r0, r1, top - thick + 0.03, 0.03, false);
    }

    // ---------- 小工具 ----------

    static final double[] UP = {0, 1, 0};

    private static double[] tangent(double phi) {
        double t = Math.PI - phi;
        return new double[]{Math.sin(t), 0, -Math.cos(t)};
    }

    private static double[] radial(double phi) {
        double t = Math.PI - phi;
        return new double[]{Math.cos(t), 0, Math.sin(t)};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] mul(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] normal(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-9 ? new double[]{0, 1, 0} : mul(a, 1 / l);
    }

    private static double[] down(double[] a, double d) {
        return new double[]{a[0], a[1] - d, a[2]};
    }

    /** 一个四边形，按 out 方向朝外排好顶点顺序；贴图按世界坐标从三个方向投。 */
    private void quad(int mat, double[] a, double[] b, double[] c, double[] d, double[] out) {
        double[] n = normal(cross(sub(b, a), sub(c, a)));
        if (dot(n, out) < 0) {
            double[] t = b;
            b = d;
            d = t;
            n = mul(n, -1);
        }
        double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]), s = TILE[mat];
        double[] q = new double[23];
        double[][] v = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            double u, w;
            if (ay >= ax && ay >= az) {
                u = v[i][0];
                w = v[i][2];
            } else if (ax >= az) {
                u = v[i][2];
                w = -v[i][1];
            } else {
                u = v[i][0];
                w = -v[i][1];
            }
            q[i * 5] = v[i][0];
            q[i * 5 + 1] = v[i][1];
            q[i * 5 + 2] = v[i][2];
            q[i * 5 + 3] = u / s;
            q[i * 5 + 4] = w / s;
        }
        q[20] = n[0];
        q[21] = n[1];
        q[22] = n[2];
        cur.quads[mat].add(q);
    }

    /** 贴图坐标自己给的四边形（灯箱面板整张贴上去）。uv 是四个顶点的 (u, v)。 */
    private void quadUv(int mat, double[] a, double[] b, double[] c, double[] d, double[] out, double[] uv) {
        double[] n = normal(cross(sub(b, a), sub(c, a)));
        double[][] v = {a, b, c, d};
        int[] order = {0, 1, 2, 3};
        if (dot(n, out) < 0) {
            order = new int[]{0, 3, 2, 1};
            n = mul(n, -1);
        }
        double[] q = new double[23];
        for (int i = 0; i < 4; i++) {
            int k = order[i];
            q[i * 5] = v[k][0];
            q[i * 5 + 1] = v[k][1];
            q[i * 5 + 2] = v[k][2];
            q[i * 5 + 3] = uv[k * 2];
            q[i * 5 + 4] = uv[k * 2 + 1];
        }
        q[20] = n[0];
        q[21] = n[1];
        q[22] = n[2];
        cur.quads[mat].add(q);
    }

    /** 盒子：中心 c，三根半轴 ex、ey、ez（互相垂直）。 */
    private void box(int mat, double[] c, double[] ex, double[] ey, double[] ez) {
        double[][] p = new double[8][];
        for (int i = 0; i < 8; i++) {
            p[i] = add(add(add(c, mul(ex, (i & 1) == 0 ? -1 : 1)), mul(ey, (i & 2) == 0 ? -1 : 1)), mul(ez, (i & 4) == 0 ? -1 : 1));
        }
        quad(mat, p[1], p[3], p[7], p[5], ex);
        quad(mat, p[0], p[4], p[6], p[2], mul(ex, -1));
        quad(mat, p[2], p[6], p[7], p[3], ey);
        quad(mat, p[0], p[1], p[5], p[4], mul(ey, -1));
        quad(mat, p[4], p[5], p[7], p[6], ez);
        quad(mat, p[0], p[2], p[3], p[1], mul(ez, -1));
    }

    /** 一根方杆从 a 到 b：宽 w、高 h，高的方向尽量贴着 up（null 就随便挑一个不平行的）。 */
    private void bar(int mat, double[] a, double[] b, double w, double h, double[] up) {
        double[] axis = sub(b, a);
        double len = Math.sqrt(dot(axis, axis));
        if (len < 1e-4) return;
        double[] d = mul(axis, 1 / len);
        double[] ref = up != null && Math.abs(dot(d, up)) < 0.95 ? up : Math.abs(d[1]) < 0.9 ? UP : new double[]{1, 0, 0};
        double[] side = normal(cross(d, ref)), upward = normal(cross(side, d));
        box(mat, mul(add(a, b), 0.5), mul(d, len / 2), mul(upward, h / 2), mul(side, w / 2));
    }

    /** 槽钢斜梁：从 a 到 b，厚 t、高 h，腹板尽量贴着 up 竖着。 */
    private void channel(double[] a, double[] b, double t, double h, double[] up) {
        double[] c = mul(add(a, b), 0.5);
        double[] d = sub(b, a);
        double len = Math.sqrt(dot(d, d));
        if (len < 1e-4) return;
        d = mul(d, 1 / len);
        double[] side = normal(cross(d, up)), upward = normal(cross(side, d));
        box(RUST, add(c, mul(upward, -h / 2)), mul(d, len / 2 + 0.005), mul(upward, h / 2), mul(side, t / 2));
    }

    /** 顺着井壁弯的一块板：φ a..b、离井心 r0..r1、上面在 top、厚 thick。twoSided：上下两面都朝外（格栅从下面也看得见）。 */
    private void arcPlate(int topMat, int sideMat, double a, double b, double r0, double r1, double top, double thick, boolean twoSided) {
        int n = Math.max(2, (int) Math.ceil((b - a) * (r0 + r1) / 2 / 0.6));
        for (int i = 0; i < n; i++) {
            double p0 = a + (b - a) * i / n, p1 = a + (b - a) * (i + 1) / n;
            double[] t0 = Well.at(p0, r0, top), t1 = Well.at(p1, r0, top), t2 = Well.at(p1, r1, top), t3 = Well.at(p0, r1, top);
            double[] b0 = down(t0, thick), b1 = down(t1, thick), b2 = down(t2, thick), b3 = down(t3, thick);
            quad(topMat, t0, t1, t2, t3, UP);
            quad(twoSided ? topMat : sideMat, b3, b2, b1, b0, mul(UP, -1));
            quad(sideMat, t0, b0, b1, t1, mul(radial((p0 + p1) / 2), -1));
            quad(sideMat, t3, t2, b2, b3, radial((p0 + p1) / 2));
            if (i == 0) quad(sideMat, t3, b3, b0, t0, mul(tangent(p0), -1));
            if (i == n - 1) quad(sideMat, t1, b1, b2, t2, tangent(p1));
        }
    }

    /** 顺着井壁弯的一根方杆：离井心 r，上沿在 top，宽 w、高 h。 */
    private void arcBar(int mat, double a, double b, double r, double top, double w, double h) {
        int n = Math.max(2, (int) Math.ceil((b - a) * r / 0.6));
        for (int i = 0; i < n; i++) {
            double p0 = a + (b - a) * i / n, p1 = a + (b - a) * (i + 1) / n;
            bar(mat, Well.at(p0, r, top - h / 2), Well.at(p1, r, top - h / 2), w, h, UP);
        }
    }

    /** 顺着半径方向的一根方杆（横撑、防滑条）：φ 处、从 r0 到 r1，上沿在 top。 */
    private void radialBar(int mat, double phi, double r0, double r1, double top, double w, double h) {
        bar(mat, Well.at(phi, r0, top - h / 2), Well.at(phi, r1, top - h / 2), w, h, UP);
    }

    /** 竖着的一块板（踏步立板）：φ 处、r0..r1、从 y0 到 y1，厚 t。 */
    private void radialPlate(int mat, double phi, double r0, double r1, double y0, double y1, double t) {
        double[] a = Well.at(phi, (r0 + r1) / 2, (y0 + y1) / 2);
        box(mat, a, mul(radial(phi), (r1 - r0) / 2), mul(UP, Math.abs(y1 - y0) / 2), mul(tangent(phi), t / 2));
    }

    // ---------- 写出去 ----------

    /** 客户端读的网格：每段 编号、原点、半径，然后每种材质 四边形数、顶点 (相对原点 x, y, z, u, v)、法线（-127..127）。 */
    public void writeBlob(DataOutputStream out) throws IOException {
        out.writeInt(pieces.size());
        for (Piece p : pieces) {
            out.writeInt(p.id);
            out.writeDouble(p.ox);
            out.writeDouble(p.oy);
            out.writeDouble(p.oz);
            out.writeFloat((float) Math.sqrt(p.sx * p.sx + p.sy * p.sy + p.sz * p.sz) / 2 + 1);
            int groups = 0;
            for (List<double[]> q : p.quads) if (!q.isEmpty()) groups++;
            out.writeByte(groups);
            for (int m = 0; m < p.quads.length; m++) {
                List<double[]> list = p.quads[m];
                if (list.isEmpty()) continue;
                out.writeByte(m);
                out.writeInt(list.size());
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(list.size() * 20 * 4);
                for (double[] q : list) {
                    for (int v = 0; v < 4; v++) {
                        buffer.putFloat((float) (q[v * 5] - p.ox));
                        buffer.putFloat((float) (q[v * 5 + 1] - p.oy));
                        buffer.putFloat((float) (q[v * 5 + 2] - p.oz));
                        buffer.putFloat((float) q[v * 5 + 3]);
                        buffer.putFloat((float) q[v * 5 + 4]);
                    }
                }
                out.write(buffer.array());
                byte[] normals = new byte[list.size() * 3];
                for (int i = 0; i < list.size(); i++) {
                    double[] q = list.get(i);
                    for (int k = 0; k < 3; k++) normals[i * 3 + k] = (byte) Math.round(q[20 + k] * 127);
                }
                out.write(normals);
            }
        }
    }

    /** 插件放锚点用：编号、原点、大小。 */
    public String index() {
        StringBuilder b = new StringBuilder();
        for (Piece p : pieces) b.append(p.id).append(' ').append(p.ox).append(' ').append(p.oy).append(' ').append(p.oz).append(' ').append(p.sx).append(' ').append(p.sy).append(' ').append(p.sz).append('\n');
        return b.toString();
    }

    /** 整套钢结构存一份 OBJ（世界坐标），贴图名对上客户端的 textures/steel/*.png。 */
    public void writeObj(Path obj) throws IOException {
        Path mtl = obj.resolveSibling(obj.getFileName().toString().replace(".obj", ".mtl"));
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(mtl, StandardCharsets.UTF_8))) {
            for (String m : MATERIALS) w.printf("newmtl %s%nKd 1 1 1%nmap_Kd textures/steel/%s.png%n%n", m, m);
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(obj, StandardCharsets.UTF_8))) {
            w.println("# 血肉公园 深井的钢结构和残骸（世界坐标，1 = 1 格）");
            w.println("mtllib " + mtl.getFileName());
            int v = 1;
            for (Piece p : pieces) {
                w.println("o piece_" + p.id);
                for (int m = 0; m < p.quads.length; m++) {
                    if (p.quads[m].isEmpty()) continue;
                    w.println("usemtl " + MATERIALS[m]);
                    for (double[] q : p.quads[m]) {
                        for (int k = 0; k < 4; k++) w.printf("v %.4f %.4f %.4f%nvt %.4f %.4f%n", q[k * 5], q[k * 5 + 1], q[k * 5 + 2], q[k * 5 + 3], -q[k * 5 + 4]);
                        w.printf("vn %.3f %.3f %.3f%n", q[20], q[21], q[22]);
                        int n = (v - 1) / 4 + 1;
                        w.printf("f %d/%d/%d %d/%d/%d %d/%d/%d %d/%d/%d%n", v, v, n, v + 1, v + 1, n, v + 2, v + 2, n, v + 3, v + 3, n);
                        v += 4;
                    }
                }
            }
        }
    }
}

package world.hechao.fleshpark.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 缆车路线：根实体走的线。车厢一直保持水平，从根实体往后伸，所以坐在根实体上的人正好贴着前窗。
 * 从车站出门，过肉口，顺着食道直直往下，掉进空腔，停在游客中心 3 号码头。
 */
public final class Route {
    /** 车厢的范围（相对根实体）。人坐在根实体上，前窗在 +0.8 处。 */
    public static final double CABIN_X0 = -6.2, CABIN_X1 = 0.8, CABIN_HALF_Z = 2.0;
    public static final double CABIN_CENTER_X = (CABIN_X0 + CABIN_X1) / 2;
    /** 地板面比根实体低这么多。玩家骑在根实体上时脚在根实体下 0.6，眼睛在根实体上 1.02，比坐着的人稍高。 */
    public static final double FLOOR_DROP = 0.33;
    /** 缆绳和吊臂的高度（相对根实体）。 */
    public static final double CABLE_Y = 3.2;
    /** 缆绳在车厢两边，离中线这么远。车厢一直是水平的，缆绳陡的时候不能从车厢中间穿过去。 */
    public static final double CABLE_SIDE = 2.6;

    private static final double[][] POINTS = {
            {-0.8, 231.33, 0}, {8, 231.33, 0}, {17, 231.2, 0}, {27, 229.8, 0}, {36, 225, 0}, {43.5, 216, 0}, {47.5, 205, 0},
            {49, 192, 0}, {49, 176, 0}, {49, 160, 0}, {49, 146, 0}, {52, 128, 0}, {60, 100, 0}, {72, 72, 0}, {85, 50, 0},
            {95, 36.5, 0}, {100.5, 31.2, 0}, {102.5, 30.33, 0}
    };
    private final double[] xs, ys, zs;
    private final double step;
    public final double length;

    public Route() {
        List<double[]> dense = new ArrayList<>();
        int n = POINTS.length;
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = i == 0 ? mirror(POINTS[0], POINTS[1]) : POINTS[i - 1];
            double[] p3 = i == n - 2 ? mirror(POINTS[n - 1], POINTS[n - 2]) : POINTS[i + 2];
            for (int k = 0; k < 200; k++) dense.add(catmull(p0, POINTS[i], POINTS[i + 1], p3, k / 200.0));
        }
        dense.add(POINTS[n - 1].clone());
        double total = 0;
        double[] lengths = new double[dense.size()];
        for (int i = 1; i < dense.size(); i++) {
            total += dist(dense.get(i - 1), dense.get(i));
            lengths[i] = total;
        }
        length = total;
        int samples = (int) Math.ceil(total / 0.05) + 1;
        step = total / (samples - 1);
        xs = new double[samples];
        ys = new double[samples];
        zs = new double[samples];
        int j = 0;
        for (int i = 0; i < samples; i++) {
            double d = Math.min(total, i * step);
            while (j < lengths.length - 2 && lengths[j + 1] < d) j++;
            double span = lengths[j + 1] - lengths[j];
            double k = span <= 0 ? 0 : (d - lengths[j]) / span;
            double[] a = dense.get(j), b = dense.get(j + 1);
            xs[i] = a[0] + (b[0] - a[0]) * k;
            ys[i] = a[1] + (b[1] - a[1]) * k;
            zs[i] = a[2] + (b[2] - a[2]) * k;
        }
    }

    private static double[] mirror(double[] end, double[] next) {
        return new double[]{2 * end[0] - next[0], 2 * end[1] - next[1], 2 * end[2] - next[2]};
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** 向心 Catmull-Rom：拐弯不打结，也不会明显冲过控制点。 */
    private static double[] catmull(double[] p0, double[] p1, double[] p2, double[] p3, double u) {
        double t0 = 0, t1 = t0 + Math.sqrt(dist(p0, p1)), t2 = t1 + Math.sqrt(dist(p1, p2)), t3 = t2 + Math.sqrt(dist(p2, p3));
        double t = t1 + (t2 - t1) * u;
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) {
            double a1 = lerp(p0[c], p1[c], t0, t1, t), a2 = lerp(p1[c], p2[c], t1, t2, t), a3 = lerp(p2[c], p3[c], t2, t3, t);
            double b1 = lerp(a1, a2, t0, t2, t), b2 = lerp(a2, a3, t1, t3, t);
            out[c] = lerp(b1, b2, t1, t2, t);
        }
        return out;
    }

    private static double lerp(double a, double b, double ta, double tb, double t) {
        return tb == ta ? a : a + (b - a) * (t - ta) / (tb - ta);
    }

    public void at(double d, double[] out) {
        double pos = Math.max(0, Math.min(length, d)) / step;
        int i = Math.min((int) pos, xs.length - 2);
        double k = Math.min(1, pos - i);
        out[0] = xs[i] + (xs[i + 1] - xs[i]) * k;
        out[1] = ys[i] + (ys[i + 1] - ys[i]) * k;
        out[2] = zs[i] + (zs[i + 1] - zs[i]) * k;
    }

    /** 第一次降到这个高度时走了多远。出站以后路线一直往下，能二分。 */
    public double distAtY(double y) {
        int lo = 0, hi = ys.length - 1;
        if (ys[hi] > y) return length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (ys[mid] <= y) hi = mid;
            else lo = mid + 1;
        }
        return lo * step;
    }

    public double distAtX(double x) {
        for (int i = 0; i < xs.length; i++) if (xs[i] >= x) return i * step;
        return length;
    }

    /** 这个球会不会碰到车厢扫过的空间。r 是球的半径。 */
    public boolean carHit(double x, double y, double z, double r) {
        for (int i = 0; i < xs.length; i += 10) {
            double dy = y - ys[i];
            if (dy < -0.7 - r || dy > CABLE_Y + 0.2 + r) continue;
            double dx = x - xs[i];
            if (dx < CABIN_X0 - 0.2 - r || dx > CABIN_X1 + 0.2 + r) continue;
            if (Math.abs(z - zs[i]) <= CABIN_HALF_Z + 0.2 + r) return true;
        }
        return false;
    }

    /** 给扫过的每个采样点回调一次，用来把挡路的方块挖掉。 */
    public void forEachSample(double spacing, SampleVisitor visitor) {
        int every = Math.max(1, (int) Math.round(spacing / step));
        for (int i = 0; i < xs.length; i += every) visitor.visit(xs[i], ys[i], zs[i]);
        visitor.visit(xs[xs.length - 1], ys[ys.length - 1], zs[zs.length - 1]);
    }

    public interface SampleVisitor {
        void visit(double x, double y, double z);
    }
}

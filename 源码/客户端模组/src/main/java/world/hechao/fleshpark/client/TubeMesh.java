package world.hechao.fleshpark.client;

/**
 * 一节肉索的网格：沿 X 的纺锤形，占满 0 到 1 的格子，插件靠缩放定大小。
 * 几节互相叠着摆，叠出来的外形连续；表面有几条拧着的肌束，法线按相邻顶点算，光顺着鼓起来的地方走。
 */
final class TubeMesh {
    /** 沿长度只用贴图的这么多：一节长约 4 个半径，一圈约 6.3 个半径，这样贴图不会被压扁。 */
    private static final double U_SPAN = 0.62;
    /** 两头这么长的一段算“尖”。 */
    static final float TIP = 0.1f;

    final int sides, rings;
    /** 每个顶点三个数。一圈 sides+1 个顶点，最后一个和第一个位置相同，贴图才不会在接缝处倒着走一遍。 */
    final float[] position, normal;
    /** 每个顶点两个数，贴图坐标（0 到 1）。 */
    final float[] tex;
    /** 每一圈的 t（沿长度 0 到 1）。 */
    final float[] along;

    TubeMesh(int sides, int rings, int detail) {
        this.sides = sides;
        this.rings = rings;
        int count = rings * (sides + 1);
        position = new float[count * 3];
        normal = new float[count * 3];
        tex = new float[count * 2];
        along = new float[rings];
        for (int r = 0; r < rings; r++) {
            // 两头的圈密一点，尖才是圆的
            double t = (1 - Math.cos(Math.PI * r / (rings - 1))) / 2;
            along[r] = (float) t;
            for (int s = 0; s <= sides; s++) {
                double a = Math.PI * 2 * (s % sides) / sides;
                double[] p = point(t, a, detail);
                int i = index(r, s) * 3;
                position[i] = (float) p[0];
                position[i + 1] = (float) p[1];
                position[i + 2] = (float) p[2];
                double[] n = surfaceNormal(t, a, detail);
                normal[i] = (float) n[0];
                normal[i + 1] = (float) n[1];
                normal[i + 2] = (float) n[2];
                // 身子绕一圈包贴图。两头要是也这么包，所有贴图都缩到尖上，成一朵放射状的褶，所以尖上改成正对着贴
                boolean tip = t < TIP || t > 1 - TIP;
                int j = index(r, s) * 2;
                tex[j] = (float) (tip ? 0.5 + (p[1] - 0.5) * 0.6 : t * U_SPAN);
                tex[j + 1] = (float) (tip ? 0.5 + (p[2] - 0.5) * 0.6 : (double) s / sides);
            }
        }
    }

    int index(int ring, int side) {
        return ring * (sides + 1) + side;
    }

    private static double[] point(double t, double a, int detail) {
        double r = radius(t, a, detail);
        return new double[]{t, 0.5 + r * Math.cos(a), 0.5 + r * Math.sin(a)};
    }

    /** 外形：中间鼓、两头收成圆头。detail 决定有几层肌束，远处的网格顶点少，表现不了细的。 */
    private static double radius(double t, double a, int detail) {
        double s = Math.abs(2 * t - 1);
        double r = 0.5 * Math.pow(Math.max(0, Math.cos(s * Math.PI / 2)), 0.55);
        double twist = (t - 0.5) * Math.PI * 2;
        double ridges = 0;
        if (detail >= 1) ridges += 0.055 * Math.cos(3 * a + 0.8 * twist);
        if (detail >= 2) ridges += 0.028 * Math.cos(7 * a - 0.6 * twist + 1.3) + 0.012 * Math.cos(13 * a + 1.1 * twist + 4.1);
        return r * (1 + ridges - 0.06);
    }

    private static double[] surfaceNormal(double t, double a, int detail) {
        if (t < 1e-4) return new double[]{-1, 0, 0};
        if (t > 1 - 1e-4) return new double[]{1, 0, 0};
        double dt = 1e-3, da = 1e-3;
        double[] t0 = point(Math.max(0, t - dt), a, detail), t1 = point(Math.min(1, t + dt), a, detail);
        double[] a0 = point(t, a - da, detail), a1 = point(t, a + da, detail);
        double ux = t1[0] - t0[0], uy = t1[1] - t0[1], uz = t1[2] - t0[2];
        double vx = a1[0] - a0[0], vy = a1[1] - a0[1], vz = a1[2] - a0[2];
        // 绕一圈的方向叉乘沿长度的方向，朝外
        double nx = vy * uz - vz * uy, ny = vz * ux - vx * uz, nz = vx * uy - vy * ux;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len < 1e-12 ? new double[]{t < 0.5 ? -1 : 1, 0, 0} : new double[]{nx / len, ny / len, nz / len};
    }
}

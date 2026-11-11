package world.hechao.fleshpark.core;

/** 值噪声。肉墙的起伏、肉的种类都由它定，种子固定，每次生成的公园都一样。 */
public final class Noise {
    private Noise() {
    }

    private static double hash(int x, int y, int z) {
        int n = x * 374761393 + y * 668265263 + z * 1442968193;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xffff) / 65535.0;
    }

    public static double value(double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double a = mix(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx), b = mix(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx);
        double c = mix(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx), d = mix(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx);
        return mix(mix(a, b, fy), mix(c, d, fy), fz);
    }

    public static double fbm(double x, double y, double z, int octaves) {
        double total = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            total += value(x + i * 17.3, y - i * 9.1, z + i * 5.7) * amp;
            norm += amp;
            x *= 2.03;
            y *= 2.03;
            z *= 2.03;
            amp *= 0.5;
        }
        return total / norm;
    }

    public static double mix(double a, double b, double k) {
        return a + (b - a) * k;
    }

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}

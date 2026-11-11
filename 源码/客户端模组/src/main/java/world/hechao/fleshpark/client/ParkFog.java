package world.hechao.fleshpark.client;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * 雾的距离和颜色。身边看得清，远处被黑暗吞掉；空腔的雾放远一点，才看得出有多大。
 * 走到哪一段由服务端说了算，这里只负责慢慢过渡。
 */
public final class ParkFog {
    private static final String WORLD = "hechao_fleshpark";
    private static float start = 9, end = 58, shade = 0.55f, mix, r, g, b;
    private static float startTo = 9, endTo = 58, shadeTo = 0.55f, mixTo, rTo, gTo, bTo;
    private static long last;

    private ParkFog() {
    }

    public static boolean applies(ClientLevel level) {
        return level != null && level.dimension().identifier().getPath().equals(WORLD);
    }

    static void target(float fogStart, float fogEnd, float fogShade, int rgb, float rgbMix) {
        startTo = fogStart;
        endTo = fogEnd;
        shadeTo = fogShade;
        rTo = (rgb >> 16 & 255) / 255f;
        gTo = (rgb >> 8 & 255) / 255f;
        bTo = (rgb & 255) / 255f;
        mixTo = rgbMix;
    }

    static void reset() {
        target(9, 58, 0.55f, 0, 0);
    }

    public static void update() {
        long now = System.nanoTime();
        float k = last == 0 ? 1 : Math.min(1, (now - last) / 1e9f / 2.5f);
        last = now;
        start += (startTo - start) * k;
        end += (endTo - end) * k;
        shade += (shadeTo - shade) * k;
        mix += (mixTo - mix) * k;
        r += (rTo - r) * k;
        g += (gTo - g) * k;
        b += (bTo - b) * k;
    }

    public static float start() {
        return start;
    }

    public static float end() {
        return end;
    }

    public static int tint(int argb) {
        float cr = (argb >> 16 & 255) * shade, cg = (argb >> 8 & 255) * shade, cb = (argb & 255) * shade;
        cr += (r * 255 - cr) * mix;
        cg += (g * 255 - cg) * mix;
        cb += (b * 255 - cb) * mix;
        return argb & 0xFF000000 | (int) cr << 16 | (int) cg << 8 | (int) cb;
    }
}

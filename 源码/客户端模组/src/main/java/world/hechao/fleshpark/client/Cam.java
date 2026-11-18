package world.hechao.fleshpark.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 入场片和收尾的镜头：服务端给一串机位（眼睛的位置和朝向）和时长，客户端每个 tick 把人挪到曲线上的下一个点。
 * 画面在两个 tick 之间按旧位置和新位置插值，所以镜头是顺的，不是一秒跳二十下。放的时候鼠标转不动视角。
 */
final class Cam {
    private static final double EYE = 1.62;
    private static double[][] keys = new double[0][];
    private static int ticks, age, clock;
    private static boolean active, linear;
    /** 手持晃动：头盔记录仪那种走路一颠一颠、头一直在小幅度转。0 是稳的。 */
    static double shake;

    private Cam() {
    }

    static boolean active() {
        return active;
    }

    /** cam off，或者 cam [lin] 时长 x y z 水平 俯仰;x y z 水平 俯仰;...（lin：匀速走，头尾不减速，几段接着放不会一停一停） */
    static void set(String line) {
        line = line.trim();
        linear = line.startsWith("cam lin ");
        if (linear) line = "cam " + line.substring(8);
        String[] a = line.split(" ", 3);
        if (a.length < 3 || a[1].equals("off")) {
            active = false;
            return;
        }
        ticks = Math.max(1, Integer.parseInt(a[1]));
        String[] parts = a[2].split(";");
        keys = new double[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] f = parts[i].trim().split(" ");
            keys[i] = new double[5];
            for (int k = 0; k < 5; k++) keys[i][k] = Double.parseDouble(f[k]);
        }
        age = 0;
        active = true;
    }

    static void reset() {
        active = false;
        shake = 0;
    }

    static void tick(Minecraft mc) {
        if (!active || mc.player == null || keys.length == 0) return;
        age = Math.min(age + 1, ticks);
        clock++;
        double t = age / (double) ticks, e = linear ? t : t * t * (3 - 2 * t);
        double[] q = sample(e).clone();
        if (shake > 0) {
            // 几条不同快慢的正弦叠起来：不像噪声那么抖，像人头在动
            double c = clock;
            q[3] += shake * (Math.sin(c * 0.13) * 1.6 + Math.sin(c * 0.41 + 1.3) * 0.5);
            q[4] += shake * (Math.sin(c * 0.11 + 2.1) * 1.1 + Math.sin(c * 0.57) * 0.35);
            q[1] += shake * 0.035 * Math.sin(c * 0.62);
        }
        LocalPlayer player = mc.player;
        player.setDeltaMovement(0, 0, 0);
        player.setPos(q[0], q[1] - EYE, q[2]);
        player.setYRot((float) q[3]);
        player.setXRot((float) q[4]);
        player.setYHeadRot((float) q[3]);
    }

    /** 过所有机位的平滑曲线（Catmull-Rom），朝向跟着一起插。 */
    private static double[] sample(double u) {
        if (keys.length == 1) return keys[0];
        double f = u * (keys.length - 1);
        int i = Math.min(keys.length - 2, (int) Math.floor(f));
        double s = f - i;
        double[] p0 = keys[Math.max(0, i - 1)], p1 = keys[i], p2 = keys[i + 1], p3 = keys[Math.min(keys.length - 1, i + 2)];
        double[] out = new double[5];
        for (int k = 0; k < 5; k++) {
            out[k] = 0.5 * (2 * p1[k] + (p2[k] - p0[k]) * s + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * s * s + (3 * p1[k] - p0[k] - 3 * p2[k] + p3[k]) * s * s * s);
        }
        return out;
    }
}

package world.hechao.fleshpark.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

import java.util.ArrayDeque;

/**
 * 拍素材用：替人真的走路、转头看东西。走是按住前进键（会颠、有脚步声、会撞墙），转头每帧平滑地转，上面再叠一点手抖，
 * 像一个人拿着手电边走边看。
 * walk 步骤;步骤;…  每一步：go x z [俯仰]（走到这点，脸朝着走的方向）、fwd 格数 [偏转]（朝脸的方向走几格）、look x y z 秒（转头看这一点）、
 * turn 水平增量 俯仰 秒（原地转头）、wait 秒。walk off 停。
 */
final class Walk {
    private static final ArrayDeque<String[]> steps = new ArrayDeque<>();
    private static String[] step;
    private static boolean active;
    private static double baseYaw, basePitch, fromYaw, fromPitch, toYaw, toPitch;
    private static long stepAt, lastFrame;
    private static double goPitch;
    private static int stuckTicks;
    private static double lastX, lastZ;

    private Walk() {
    }

    static void set(String line) {
        String body = line.length() > 5 ? line.substring(5).trim() : "off";
        steps.clear();
        step = null;
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        if (body.equals("off") || mc.player == null) {
            active = false;
            return;
        }
        for (String s : body.split(";")) if (!s.isBlank()) steps.add(s.trim().split("\\s+"));
        baseYaw = mc.player.getYRot();
        basePitch = mc.player.getXRot();
        lastFrame = Util.getMillis();
        active = true;
        next(mc.player);
    }

    static void reset() {
        if (active) Minecraft.getInstance().options.keyUp.setDown(false);
        active = false;
        steps.clear();
        step = null;
    }

    private static void next(LocalPlayer p) {
        Minecraft.getInstance().options.keyUp.setDown(false);
        step = steps.poll();
        stepAt = Util.getMillis();
        fromYaw = baseYaw;
        fromPitch = basePitch;
        stuckTicks = 0;
        lastX = p.getX();
        lastZ = p.getZ();
        if (step == null) {
            active = false;
            org.slf4j.LoggerFactory.getLogger("fleshpark").info("走完");
            return;
        }
        if (step[0].equals("fwd")) {
            // fwd 格数 [水平偏转]：朝现在脸的方向（再偏一点）走几格，换算成 go
            double yaw = Math.toRadians(fromYaw + (step.length > 2 ? Double.parseDouble(step[2]) : 0)), n = Double.parseDouble(step[1]);
            step = new String[]{"go", Double.toString(p.getX() - Math.sin(yaw) * n), Double.toString(p.getZ() + Math.cos(yaw) * n)};
        }
        switch (step[0]) {
            case "look" -> {
                double dx = d(1) - p.getX(), dy = d(2) - p.getEyeY(), dz = d(3) - p.getZ();
                toYaw = unwrap(Math.toDegrees(Math.atan2(-dx, dz)), fromYaw);
                toPitch = Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
            }
            case "turn" -> {
                toYaw = fromYaw + d(1);
                toPitch = d(2);
            }
            case "go" -> goPitch = step.length > 3 ? d(3) : 6;
            default -> {
            }
        }
    }

    private static double d(int i) {
        return Double.parseDouble(step[i]);
    }

    private static double unwrap(double a, double ref) {
        while (a - ref > 180) a -= 360;
        while (a - ref < -180) a += 360;
        return a;
    }

    /** 每 tick：按不按前进键、到没到、卡没卡住。 */
    static void tick(Minecraft mc) {
        if (!active || mc.player == null || step == null) return;
        LocalPlayer p = mc.player;
        double sec = (Util.getMillis() - stepAt) / 1000.0;
        switch (step[0]) {
            case "go" -> {
                double dx = d(1) - p.getX(), dz = d(2) - p.getZ();
                if (Math.hypot(dx, dz) < 0.45) {
                    next(p);
                    return;
                }
                mc.options.keyUp.setDown(true);
                if (++stuckTicks % 30 == 0) {
                    if (Math.hypot(p.getX() - lastX, p.getZ() - lastZ) < 0.25) {
                        org.slf4j.LoggerFactory.getLogger("fleshpark").info(String.format(java.util.Locale.ROOT, "走不过去 %.1f %.1f，人在 %.1f %.1f", d(1), d(2), p.getX(), p.getZ()));
                        next(p);
                        return;
                    }
                    lastX = p.getX();
                    lastZ = p.getZ();
                }
            }
            case "look", "turn" -> {
                if (sec >= d(4 - (step[0].equals("turn") ? 1 : 0))) next(p);
            }
            case "wait" -> {
                if (sec >= d(1)) next(p);
            }
            default -> next(p);
        }
    }

    /** 每帧：转头。 */
    static void frame(Minecraft mc) {
        if (!active || mc.player == null || step == null) return;
        LocalPlayer p = mc.player;
        long now = Util.getMillis();
        double dt = Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        double sec = (now - stepAt) / 1000.0;
        switch (step[0]) {
            case "go" -> {
                double dx = d(1) - p.getX(), dz = d(2) - p.getZ();
                double want = unwrap(Math.toDegrees(Math.atan2(-dx, dz)), baseYaw);
                double k = 1 - Math.exp(-dt * 3.2);
                baseYaw += (want - baseYaw) * k;
                basePitch += (goPitch - basePitch) * (1 - Math.exp(-dt * 2));
            }
            case "look", "turn" -> {
                double len = d(step[0].equals("turn") ? 3 : 4);
                double t = Math.min(1, sec / len), e = t * t * (3 - 2 * t);
                baseYaw = fromYaw + (toYaw - fromYaw) * e;
                basePitch = fromPitch + (toPitch - fromPitch) * e;
            }
            default -> {
            }
        }
        // 手抖：几条慢正弦叠起来，像人拿着手电的头在微微动
        double c = now / 1000.0;
        double sway = Math.sin(c * 0.9) * 0.7 + Math.sin(c * 2.3 + 1.1) * 0.25;
        double nod = Math.sin(c * 0.7 + 2.0) * 0.45 + Math.sin(c * 1.9) * 0.15;
        p.setYRot((float) (baseYaw + sway));
        p.setXRot((float) Math.max(-89, Math.min(89, basePitch + nod)));
        p.setYHeadRot(p.getYRot());
    }
}

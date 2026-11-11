package world.hechao.fleshpark.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;

/**
 * 缆车里能走：人还是骑在缆车上（服务端照样拽着整辆车走），客户端在车厢里给人挪一个位置。
 * WASD 在车厢地板上走，出不了车厢。脚步声是铁皮地板。
 */
final class Cabin {
    /** 缆车车厢里能站的范围，相对根实体。门在 +z 一侧开着，不让走出去。接送车的范围是发过来的。 */
    private static final double TRAM_X0 = -5.6, TRAM_X1 = 0.2, TRAM_Z0 = -1.25, TRAM_Z1 = 1.3, SPEED = 0.09;
    private static double x0 = TRAM_X0, x1 = TRAM_X1, z0 = TRAM_Z0, z1 = TRAM_Z1, standY = Double.NaN;
    private static boolean active;
    private static double rx, ry, rz, walked;

    private Cabin() {
    }

    /** cabin off，或者 cabin 人相对根实体的 x 脚底高度 z。 */
    static void set(String[] a) {
        if (a.length < 4) {
            active = false;
            return;
        }
        // 后面可以跟车厢范围和站起来的脚底高度：坐着的人一动就站起来
        boolean custom = a.length >= 9;
        x0 = custom ? Double.parseDouble(a[4]) : TRAM_X0;
        x1 = custom ? Double.parseDouble(a[5]) : TRAM_X1;
        z0 = custom ? Double.parseDouble(a[6]) : TRAM_Z0;
        z1 = custom ? Double.parseDouble(a[7]) : TRAM_Z1;
        standY = custom ? Double.parseDouble(a[8]) : Double.NaN;
        rx = clamp(Double.parseDouble(a[1]), x0, x1);
        ry = Double.parseDouble(a[2]);
        rz = clamp(Double.parseDouble(a[3]), z0, z1);
        active = true;
    }

    static void reset() {
        active = false;
    }

    static void afterRide(LocalPlayer player) {
        if (!active) return;
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;
        Vec2 move = player.input.getMoveVector();
        double yaw = Math.toRadians(player.getYRot()), sin = Math.sin(yaw), cos = Math.cos(yaw);
        double len = Math.max(1, Math.sqrt(move.x * move.x + move.y * move.y));
        double dx = (move.x * cos - move.y * sin) / len * SPEED, dz = (move.y * cos + move.x * sin) / len * SPEED;
        double nx = clamp(rx + dx, x0, x1), nz = clamp(rz + dz, z0, z1);
        if (!Double.isNaN(standY) && (move.x != 0 || move.y != 0)) ry = Math.min(standY, ry + 0.06);
        walked += Math.hypot(nx - rx, nz - rz);
        rx = nx;
        rz = nz;
        player.setPos(vehicle.getX() + rx, vehicle.getY() + ry, vehicle.getZ() + rz);
        if (walked > 0.6) {
            walked = 0;
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.METAL_STEP, SoundSource.PLAYERS, 0.18f, 0.8f, false);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
